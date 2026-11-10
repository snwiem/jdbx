package com.datev.dbui.session;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.datev.dbui.JdbcResultReader;
import com.datev.dbui.StatementSplitter;
import com.datev.dbui.config.Settings;
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
	private volatile Settings plainSettings;
	private Consumer<SessionDirectory> directoryListener = directory -> { };
	private final ExecutorService executor = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("dbui-statement").factory());
	private final ExecutorService cancellations = Executors.newVirtualThreadPerTaskExecutor();
	private final List<MessageBlock> messageBlocks = new ArrayList<>();
	private final ExecutionControls controls;
	private SchemaCommands schemaCommands;
	private boolean ended;
	private boolean running;
	private volatile boolean cancelRequested;
	private Statement activeStatement;
	private long startedAt;
	private ExecutionStatus.ResultSummary lastResult;

	public Session(Connection connection, SessionDirectory directory) throws SQLException {
		this(connection, directory, (SessionContext) null);
	}

	/** Without a context: execution settings only, no stored history or Resume. */
	public Session(Connection connection, SessionDirectory directory, Settings settings) throws SQLException {
		this(connection, directory, null, settings);
	}

	/** With a context the Session stores history and metadata and supports {@code /resume}. */
	public Session(Connection connection, SessionDirectory directory, SessionContext context) throws SQLException {
		this(connection, directory, context, context == null ? Settings.defaults() : context.settings());
	}

	private Session(Connection connection, SessionDirectory directory, SessionContext context, Settings settings) throws SQLException {
		this.connection = connection;
		ExecutionControls.validate(settings);
		this.state = context == null ? null : new SessionState(directory, context);
		this.plainDirectory = directory;
		this.plainSettings = settings;
		this.controls = new ExecutionControls(this::settings, this::updateSettings);
		this.schemaCommands = new SchemaCommands(new SchemaProvider(connection));
		connection.setAutoCommit(true);
		connection.setReadOnly(settings.readOnly());
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
	public Settings settings() {
		return state == null ? plainSettings : state.settings();
	}

	private void updateSettings(Settings settings) {
		if (state == null) {
			plainSettings = settings;
		}
		else {
			state.settings(settings);
		}
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

	public synchronized ExecutionStatus status() {
		return new ExecutionStatus(controls.rowLimit(), running,
			running ? Duration.ofNanos(System.nanoTime() - startedAt) : Duration.ZERO, lastResult, settings().readOnly(), true);
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
			return run(() -> resume(input.strip().substring("/resume".length()).strip()));
		}
		if (input.strip().equals("/quit")) {
			ended = true;
			return CompletableFuture.completedFuture(new Update(messageBlocks(), true));
		}
		// SLASH-COMMAND HOOK (issue 0011): /readonly. Port to the Slash command registry of issue 0012.
		var readOnlyCommand = ReadOnlyCommand.parse(input);
		switch (readOnlyCommand) {
			case SHOW -> {
				messageBlocks.add(MessageBlock.info(ReadOnlyCommand.describe(settings().readOnly())));
				return CompletableFuture.completedFuture(new Update(messageBlocks(), false));
			}
			case USAGE -> {
				messageBlocks.add(MessageBlock.error(ReadOnlyCommand.USAGE));
				return CompletableFuture.completedFuture(new Update(messageBlocks(), false));
			}
			case ON, OFF -> {
				return run(() -> changeReadOnly(readOnlyCommand == ReadOnlyCommand.Kind.ON));
			}
			case NOT_MINE -> { }
		}
		var controlMessage = controls.handle(input);
		if (controlMessage != null) {
			messageBlocks.add(controlMessage);
			return CompletableFuture.completedFuture(new Update(messageBlocks(), false));
		}
		if (SchemaCommands.handles(input)) {
			return run(() -> runSchemaCommand(input));
		}
		String stripped = input.stripLeading();
		if (stripped.startsWith("/") && !stripped.startsWith("/*")) {
			messageBlocks.add(MessageBlock.error("Unknown Slash command: " + input.strip()));
			return CompletableFuture.completedFuture(new Update(messageBlocks(), false));
		}
		return run(() -> execute(input));
	}

	private CompletableFuture<Update> run(Supplier<Update> work) {
		running = true;
		startedAt = System.nanoTime();
		cancelRequested = false;
		return CompletableFuture.supplyAsync(() -> {
			try {
				return work.get();
			}
			finally {
				finished();
			}
		}, executor);
	}

	/** Requests JDBC cancellation without blocking the input thread. The Session remains open. */
	public synchronized CompletableFuture<Void> cancel() {
		if (!running) {
			return CompletableFuture.completedFuture(null);
		}
		cancelRequested = true;
		Statement statement = activeStatement;
		return CompletableFuture.runAsync(() -> {
			// pgjdbc ignores cancel while idle. Retry across its idle-to-executing window, but never target a later input.
			while (statement != null && isActive(statement)) {
				try {
					statement.cancel();
					Thread.sleep(50);
				}
				catch (SQLException e) {
					writeLog(e);
					return;
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}, cancellations);
	}

	private synchronized boolean isActive(Statement statement) {
		return running && activeStatement == statement;
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
				lastResult = null;
			}
			schemaCommands = new SchemaCommands(new SchemaProvider(connection)); // the schema cache belongs to the connection
			directoryListener.accept(state.directory());
			add(MessageBlock.info(resumed.message()));
		}
		catch (SessionState.ResumeException e) {
			add(MessageBlock.error(e.getMessage()));
		}
		return new Update(messageBlocks(), false);
	}

	/** Changes the mode atomically: the Settings change only after the server confirms it. */
	private Update changeReadOnly(boolean readOnly) {
		boolean previous = settings().readOnly();
		try {
			connection.setReadOnly(readOnly);
			verifyReadOnly(readOnly);
		}
		catch (SQLException e) {
			try {
				connection.setReadOnly(previous);
			}
			catch (SQLException ignored) {
				// The error below is the one that matters.
			}
			add(MessageBlock.error("Cannot switch to " + ReadOnlyCommand.describe(readOnly) + ": " + SqlErrorText.of(e)
				+ ". The Session stays " + ReadOnlyCommand.describe(previous) + "."));
			return new Update(messageBlocks(), false);
		}
		var current = settings();
		updateSettings(current.withExecution(current.rowLimit(), current.timeout(), readOnly));
		add(MessageBlock.info(ReadOnlyCommand.describe(readOnly)));
		return new Update(messageBlocks(), false);
	}

	/** Asks the server in the same way as a Statement runs, so that a driver flag without effect is caught. */
	private void verifyReadOnly(boolean expected) throws SQLException {
		boolean guarded = connection.isReadOnly();
		if (guarded) {
			connection.setAutoCommit(false);
		}
		try (var statement = connection.createStatement(); var rows = statement.executeQuery("show transaction_read_only")) {
			rows.next();
			if (rows.getString(1).equals("on") != expected) {
				throw new SQLException("the server did not apply it", "25000");
			}
		}
		finally {
			if (guarded) {
				endReadOnlyTransaction();
			}
		}
	}

	private synchronized void finished() {
		activeStatement = null;
		running = false;
	}

	private synchronized void active(Statement statement) {
		activeStatement = statement;
	}

	private synchronized void resultSummary(int rows, long statementStartedAt, boolean truncated) {
		lastResult = new ExecutionStatus.ResultSummary(rows, Duration.ofNanos(System.nanoTime() - statementStartedAt), truncated);
	}

	private Update runSchemaCommand(String input) {
		try {
			schemaCommands.run(input).forEach(this::add);
		}
		catch (SQLException e) {
			add(MessageBlock.error(SqlErrorText.of(e)));
		}
		catch (RuntimeException | Error e) {
			add(MessageBlock.error("Unexpected error: " + e.getMessage() + (logFile() == null ? "" : ". Details: " + logFile())));
			writeLog(e);
		}
		return new Update(messageBlocks(), false);
	}

	private Update execute(String input) {
		try (var statement = connection.createStatement()) {
			int rowLimit = controls.rowLimit();
			statement.setQueryTimeout(controls.timeoutSeconds());
			statement.setMaxRows(rowLimit == Integer.MAX_VALUE ? rowLimit : rowLimit + 1);
			active(statement);
			for (String sql : new StatementSplitter().split(input)) {
				if (cancelRequested) {
					add(MessageBlock.info("Statement cancelled"));
					break;
				}
				long statementStartedAt = System.nanoTime();
				// Read-only: every Statement gets its own explicit READ ONLY transaction (see ReadOnlyIT).
				boolean guarded = connection.isReadOnly();
				if (guarded) {
					connection.setAutoCommit(false);
				}
				try {
					runStatement(statement, sql, rowLimit, statementStartedAt);
				}
				finally {
					if (guarded) {
						endReadOnlyTransaction();
					}
				}
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

	private void endReadOnlyTransaction() throws SQLException {
		try {
			connection.rollback();
		}
		catch (SQLException ignored) {
			// the connection may be broken; setAutoCommit below reports it if it matters
		}
		connection.setAutoCommit(true);
	}

	private void runStatement(Statement statement, String sql, int rowLimit, long statementStartedAt) throws SQLException {
		if (statement.execute(sql)) {
			try (var rows = statement.getResultSet()) {
				var read = JdbcResultReader.read(rows, rowLimit);
				resultSummary(read.result().rows().size(), statementStartedAt, read.truncated());
				add(MessageBlock.result(read.result()));
				if (read.truncated()) {
					add(MessageBlock.info("truncated at " + rowLimit + " rows"));
				}
			}
		}
		else {
			add(MessageBlock.info(CommandTag.of(sql, statement.getLargeUpdateCount())));
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
			// Logging is best effort; never send a logging failure to the terminal.
		}
	}

	private synchronized void add(MessageBlock block) {
		messageBlocks.add(block);
	}

	@Override
	public void close() throws SQLException {
		cancel();
		executor.close();
		cancellations.close();
		connection.close();
	}
}
