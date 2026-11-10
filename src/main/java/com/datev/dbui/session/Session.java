package com.datev.dbui.session;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.datev.dbui.Result;
import com.datev.dbui.schema.SchemaCommands;
import com.datev.dbui.schema.SchemaProvider;

/** Owns one JDBC connection. Statement execution never runs on the input thread. */
public final class Session implements AutoCloseable {

	public record Update(List<MessageBlock> messageBlocks, boolean ended) {
		public Update {
			messageBlocks = List.copyOf(messageBlocks);
		}
	}

	private Connection connection;
	private final SessionState state;
	private final SessionDirectory plainDirectory;
	private Consumer<SessionDirectory> directoryListener = directory -> { };
	private final ExecutorService executor = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("dbui-statement").factory());
	private final List<MessageBlock> messageBlocks = new ArrayList<>();
	private SchemaCommands schemaCommands;
	private boolean ended;
	private boolean running;

	public Session(Connection connection, SessionDirectory directory) throws SQLException {
		this(connection, directory, null);
	}

	/** With a context the Session stores history and metadata and supports {@code /resume}. */
	public Session(Connection connection, SessionDirectory directory, SessionContext context) throws SQLException {
		this.connection = connection;
		this.state = context == null ? null : new SessionState(directory, context);
		this.plainDirectory = directory;
		this.schemaCommands = new SchemaCommands(new SchemaProvider(connection));
		connection.setAutoCommit(true);
		if (directory.disabledReason() != null) {
			messageBlocks.add(MessageBlock.info("history and Session log are disabled: " + directory.disabledReason()));
		}
	}

	/** The current Session directory. {@code /resume} replaces it with a fork. */
	public SessionDirectory directory() {
		return state == null ? plainDirectory : state.directory();
	}

	/** The Session log file, or {@code null} when it is disabled. Only this Session writes to it. */
	public Path logFile() {
		return directory().logFile();
	}

	/** The history of this Session, oldest first. */
	public List<String> history() {
		return state == null ? List.of() : state.history();
	}

	/** The settings in effect; {@code /resume} restores row limit, timeout and read-only. */
	public com.datev.dbui.config.Settings settings() {
		return state == null ? null : state.settings();
	}

	/** {@code user@host:port/db} of the connection, or {@code null} without a context. */
	public String connectionLabel() {
		return state == null ? null : state.connectionLabel();
	}

	/** Called when {@code /resume} switches to a new Session directory (for example to move the log file). */
	public void onDirectoryChange(Consumer<SessionDirectory> listener) {
		this.directoryListener = listener;
	}

	public synchronized List<MessageBlock> messageBlocks() {
		return List.copyOf(messageBlocks);
	}

	/** Accepts the next input after the previous submission completes; type-ahead is not supported. */
	public synchronized CompletableFuture<Update> submit(String input) {
		if (ended) {
			return CompletableFuture.completedFuture(new Update(messageBlocks(), true));
		}
		if (running) {
			return CompletableFuture.failedFuture(new IllegalStateException("A Statement is already running"));
		}
		if (input.isBlank()) {
			return CompletableFuture.completedFuture(new Update(messageBlocks(), false));
		}
		messageBlocks.add(MessageBlock.input(input));
		if (state != null) {
			state.submitted(input);
		}
		if (state != null && isResume(input)) {
			running = true;
			return run(() -> resume(input.strip().substring("/resume".length()).strip()));
		}
		if (input.strip().equals("/quit")) {
			ended = true;
			return CompletableFuture.completedFuture(new Update(messageBlocks(), true));
		}
		if (SchemaCommands.handles(input)) {
			running = true;
			return CompletableFuture.supplyAsync(() -> {
				try {
					return runSchemaCommand(input);
				}
				finally {
					finished();
				}
			}, executor);
		}
		String stripped = input.stripLeading();
		if (stripped.startsWith("/") && !stripped.startsWith("/*")) {
			messageBlocks.add(MessageBlock.error("Unknown Slash command: " + input.strip()));
			return CompletableFuture.completedFuture(new Update(messageBlocks(), false));
		}
		running = true;
		return run(() -> execute(input));
	}

	private CompletableFuture<Update> run(Supplier<Update> work) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return work.get();
			}
			finally {
				finished();
			}
		}, executor);
	}

	private static boolean isResume(String input) {
		String text = input.strip();
		return text.equals("/resume") || text.startsWith("/resume ");
	}

	private Update resume(String argument) {
		try {
			if (argument.isEmpty()) {
				add(MessageBlock.info(state.list()));
				return new Update(messageBlocks(), false);
			}
			var resumed = state.resume(argument, connection);
			synchronized (this) {
				if (resumed.connection() != null) {
					var old = connection;
					connection = resumed.connection();
					try {
						old.close();
					}
					catch (SQLException ignored) {
						// The new connection is open; the old one is gone either way.
					}
				}
			}
			schemaCommands = new SchemaCommands(new SchemaProvider(connection)); // the schema cache belongs to the connection
			try {
				connection.setReadOnly(state.settings().readOnly());
			}
			catch (SQLException e) {
				add(MessageBlock.error("Cannot set read-only to " + state.settings().readOnly() + ": " + e.getMessage()));
			}
			directoryListener.accept(state.directory());
			add(MessageBlock.info(resumed.message()));
		}
		catch (SessionState.ResumeException e) {
			add(MessageBlock.error(e.getMessage()));
		}
		return new Update(messageBlocks(), false);
	}

	private synchronized void finished() {
		running = false;
	}

	private Update runSchemaCommand(String input) {
		try {
			schemaCommands.run(input).forEach(this::add);
		}
		catch (SQLException e) {
			add(MessageBlock.error(SqlErrorText.of(e)));
		}
		catch (RuntimeException | Error e) {
			add(MessageBlock.error("Unexpected error: " + e.getMessage() + ". Details: " + logFile()));
			writeLog(e);
		}
		return new Update(messageBlocks(), false);
	}

	private Update execute(String input) {
		try (var statement = connection.createStatement()) {
			if (statement.execute(input)) {
				try (var rows = statement.getResultSet()) {
					add(MessageBlock.result(read(rows)));
				}
			}
			else {
				add(MessageBlock.info(CommandTag.of(input, statement.getLargeUpdateCount())));
			}
			return new Update(messageBlocks(), false);
		}
		catch (SQLException e) {
			add(MessageBlock.error(SqlErrorText.of(e)));
			return new Update(messageBlocks(), false);
		}
		catch (RuntimeException | Error e) {
			// Not a SQL error: the stack trace goes to the Session log, never to the terminal.
			add(MessageBlock.error("Unexpected error: " + e.getMessage() + (logFile() == null ? "" : ". Details: " + logFile())));
			writeLog(e);
			return new Update(messageBlocks(), false);
		}
	}

	private void writeLog(Throwable error) {
		if (logFile() == null) {
			return;
		}
		var trace = new StringWriter();
		error.printStackTrace(new PrintWriter(trace));
		try {
			Files.writeString(logFile(), Instant.now() + " Unexpected error\n" + trace, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		}
		catch (IOException ignored) {
			// The log is best effort; the red band already tells the user.
		}
	}

	private synchronized void add(MessageBlock block) {
		messageBlocks.add(block);
	}

	private static Result read(ResultSet rows) throws SQLException {
		var metadata = rows.getMetaData();
		int count = metadata.getColumnCount();
		List<String> columns = new ArrayList<>();
		for (int column = 1; column <= count; column++) {
			columns.add(metadata.getColumnLabel(column));
		}
		List<List<Object>> values = new ArrayList<>();
		while (rows.next()) {
			List<Object> row = new ArrayList<>();
			for (int column = 1; column <= count; column++) {
				row.add(rows.getObject(column));
			}
			values.add(row);
		}
		return new Result(columns, values);
	}

	@Override
	public void close() throws SQLException {
		executor.close();
		connection.close();
	}
}
