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
	private final SlashCommandRegistry slashCommands;
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
		this.slashCommands = new SlashCommandRegistry(commands());
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
			running ? Duration.ofNanos(System.nanoTime() - startedAt) : Duration.ZERO, lastResult);
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
		String stripped = input.stripLeading();
		if (stripped.startsWith("/") && !stripped.startsWith("/*")) {
			var invocation = slashCommands.parse(input);
			Supplier<Update> work = () -> {
				invocation.execute().forEach(this::add);
				return new Update(messageBlocks(), ended);
			};
			return invocation.background() ? run(work) : CompletableFuture.completedFuture(work.get());
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

	/** Registry shared by dispatch, /help and on-request argument Completion. */
	public SlashCommandRegistry slashCommands() {
		return slashCommands;
	}

	private List<SlashCommand> commands() {
		var commands = new ArrayList<SlashCommand>();
		commands.add(new SlashCommand("quit", "End the Session.", null, false, argument -> {
			ended = true;
			return List.of();
		}));
		commands.add(new SlashCommand("limit", "Change the Row limit (1 to 2147483647).",
			SlashCommand.argument("N", "1", List.of()), false, argument -> List.of(controls.limit(argument))));
		commands.add(new SlashCommand("timeout", "Change the Statement timeout (seconds: 1 to 2147483647), or turn it off.",
			SlashCommand.argument("<seconds|off>", "1", List.of("off")), false, argument -> List.of(controls.timeout(argument))));
		commands.add(schemaCommand("schema", "List schemas; all includes system schemas.", "[all]", "0..1", List.of("all")));
		commands.add(schemaCommand("tables", "List tables in the current or named schema.", "[schema]", "0..1", List.of()));
		commands.add(schemaCommand("describe", "Describe a table, its columns and keys.", "<table>", "1", List.of()));
		commands.add(schemaCommand("refresh", "Clear the schema cache.", null, "0", List.of()));
		commands.add(new SlashCommand("resume", "List earlier Sessions, or fork one by number or id.",
			SlashCommand.argument("[number|id]", "0..1", List.of()), true, argument -> {
				if (state == null) {
					return List.of(MessageBlock.error("/resume is not available without a Session context."));
				}
				resume(argument);
				return List.of();
			}));
		commands.add(new SlashCommand("help", "List Slash commands and key bindings.", null, false,
			argument -> List.of(MessageBlock.info(slashCommands.help()))));
		return commands;
	}

	private SlashCommand schemaCommand(String name, String help, String label, String arity, List<String> candidates) {
		return new SlashCommand(name, help, label == null ? null : SlashCommand.argument(label, arity, candidates), true,
			argument -> {
				runSchemaCommand(name, argument);
				return List.of();
			});
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

	private Update runSchemaCommand(String name, String argument) {
		try {
			schemaCommands.run(name, argument).forEach(this::add);
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
