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
	private SessionState state;
	private Path pendingStateBase;
	private final SessionDirectory plainDirectory;
	private volatile Settings plainSettings;
	private Consumer<SessionDirectory> directoryListener = directory -> { };
	private final ExecutorService executor = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("dbui-statement").factory());
	private final ExecutorService cancellations = Executors.newVirtualThreadPerTaskExecutor();
	private final List<MessageBlock> messageBlocks = new ArrayList<>();
	private final ExecutionControls controls;
	private SchemaCommands schemaCommands;
	private com.datev.dbui.profile.ProfileCommands profiles;
	private volatile SessionInteraction interaction;
	private java.util.function.Function<String, Update> passwordContinuation;
	private boolean ended;
	private boolean running;
	private volatile boolean cancelRequested;
	private Statement activeStatement;
	private long startedAt;
	private ExecutionStatus.ResultSummary lastResult;

	/** Interactive startup before the connection values are complete. */
	public Session(SessionDirectory directory, Settings settings) throws SQLException {
		this(null, directory, null, settings);
	}

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
		if (connection != null) {
			this.schemaCommands = new SchemaCommands(new SchemaProvider(connection));
			connection.setAutoCommit(true);
			connection.setReadOnly(settings.readOnly());
		}
		if (directory.disabledReason() != null) {
			messageBlocks.add(MessageBlock.info("history and Session log are disabled: " + directory.disabledReason()));
		}
	}

	/** Enables the Profile commands without changing the persistent Session context. */
	public void configureProfiles(Path directory, java.util.Map<String, String> environment) {
		profiles = new com.datev.dbui.profile.ProfileCommands(directory, environment);
		String warning = profiles.warning();
		if (warning != null) add(MessageBlock.info(warning));
	}

	public SessionInteraction interaction() { return interaction; }

	public void openConnectionDialog(com.datev.dbui.config.ProfileDialogModel model, Path stateBase) {
		pendingStateBase = stateBase;
		interaction = new SessionInteraction.Dialog(model, true, model.value(com.datev.dbui.config.ProfileDialogModel.Field.NAME).isBlank()
			? null : model.value(com.datev.dbui.config.ProfileDialogModel.Field.NAME));
	}

	/** Cancels a dialog or prompt without submitting input or storing credentials in history. */
	public synchronized void cancelInteraction() { interaction = null; passwordContinuation = null; }

	/** The password never passes through submit, input echo, history or Session metadata. */
	public synchronized CompletableFuture<Update> answerPassword(String password) {
		if (running || !(interaction instanceof SessionInteraction.Password) || passwordContinuation == null) return CompletableFuture.failedFuture(new IllegalStateException("No password prompt is open."));
		var continuation = passwordContinuation;
		interaction = null;
		passwordContinuation = null;
		return run(() -> continuation.apply(password));
	}

	private void connectProfile(com.datev.dbui.config.Profile profile, String profileName) throws SQLException {
		var supplied = profile;
		if (profile.password() == null && (profile.url() == null || !profile.url().matches(".*[?&]password=.*"))) {
			interaction = new SessionInteraction.Password(profile.user() == null ? "database" : profile.user());
			passwordContinuation = password -> {
				try {
					var withPassword = new com.datev.dbui.config.Profile(supplied.name(), supplied.url(), supplied.host(), supplied.port(), supplied.database(), supplied.user(), password,
						supplied.readOnly(), supplied.schema(), supplied.properties());
					switchConnection(com.datev.dbui.config.ConnectionParameters.resolve(new com.datev.dbui.config.ConnectionOptions(), withPassword, settings()), profileName);
				}
				catch (com.datev.dbui.config.ConfigException | SQLException e) { add(MessageBlock.error(e instanceof SQLException sql ? SqlErrorText.of(sql) : e.getMessage())); }
				return new Update(messageBlocks(), false);
			};
			return;
		}
		switchConnection(com.datev.dbui.config.ConnectionParameters.resolve(new com.datev.dbui.config.ConnectionOptions(), supplied, settings()), profileName);
	}

	public synchronized CompletableFuture<Update> saveDialog(boolean save) {
		if (running || !(interaction instanceof SessionInteraction.Dialog dialog)) return CompletableFuture.failedFuture(new IllegalStateException("No Profile dialog is open."));
		return run(() -> {
			try {
				dialog.model().validate(save);
				if (!save && !dialog.connect()) throw new com.datev.dbui.config.ConfigException("Profile edits must be saved.");
				if (save) {
					profiles.save(dialog.model(), dialog.editingName());
					add(MessageBlock.info("Saved Profile '" + dialog.model().savedProfile().name() + "'."));
				}
				interaction = null;
				if (dialog.connect()) {
					var profile = profiles.resolve(dialog.model().connectionProfile());
					connectProfile(profile, save ? profile.name() : null);
				}
			}
			catch (com.datev.dbui.config.ConfigException | SQLException e) { add(MessageBlock.error(e instanceof SQLException sql ? SqlErrorText.of(sql) : e.getMessage())); }
			return new Update(messageBlocks(), false);
		});
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
		if (interaction instanceof SessionInteraction.Confirmation confirmation) {
			return run(() -> {
				try {
					if (input.strip().equalsIgnoreCase("y")) { profiles.removeConfirmed(confirmation.profileName()); add(MessageBlock.info("Removed Profile '" + confirmation.profileName() + "'.")); }
					else add(MessageBlock.info("Profile removal cancelled."));
				}
				catch (com.datev.dbui.config.ConfigException e) { add(MessageBlock.error(e.getMessage())); }
				interaction = null;
				return new Update(messageBlocks(), false);
			});
		}
		if (interaction != null) return CompletableFuture.failedFuture(new IllegalStateException("Answer or cancel the open dialog first."));
		messageBlocks.add(MessageBlock.input(input));
		if (state != null) {
			state.submitted(input);
		}
		// Issue 0013 hook: /connect and /profile move to the shared registry in issue 0012.
		if (profiles != null && com.datev.dbui.profile.ProfileCommands.handles(input)) {
			return run(() -> profileCommand(input));
		}
		if (state != null && isResume(input)) {
			return run(() -> resume(input.strip().substring("/resume".length()).strip()));
		}
		if (input.strip().equals("/quit")) {
			ended = true;
			return CompletableFuture.completedFuture(new Update(messageBlocks(), true));
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
		if (connection == null) {
			add(MessageBlock.error("Not connected. Use /connect or /profile add."));
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

	private Update resume(String argument) { return resume(argument, null); }

	private Update resume(String argument, String password) {
		try {
			if (argument.isEmpty()) {
				add(MessageBlock.info(state.list()));
				return new Update(messageBlocks(), false);
			}
			var resumed = state.resume(argument, connection, password);
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
		catch (SessionState.PasswordRequired prompt) {
			interaction = new SessionInteraction.Password(prompt.getMessage());
			passwordContinuation = supplied -> resume(prompt.sessionId, supplied);
		}
		catch (SessionState.ResumeException e) {
			add(MessageBlock.error(e.getMessage()));
		}
		return new Update(messageBlocks(), false);
	}

	private Update profileCommand(String input) {
		try {
			var action = profiles.execute(input);
			if (action instanceof com.datev.dbui.profile.ProfileCommands.Info info) add(MessageBlock.info(info.text()));
			else if (action instanceof com.datev.dbui.profile.ProfileCommands.Edit edit) interaction = new SessionInteraction.Dialog(edit.model(), edit.connect(), edit.editingName());
			else if (action instanceof com.datev.dbui.profile.ProfileCommands.Remove remove) {
				interaction = new SessionInteraction.Confirmation(remove.name());
				add(MessageBlock.info("Remove Profile '" + remove.name() + "'? Type y to confirm; anything else cancels."));
			}
			else if (action instanceof com.datev.dbui.profile.ProfileCommands.Connect connect) {
				connectProfile(connect.profile(), connect.profile().name());
			}
		}
		catch (com.datev.dbui.config.ConfigException | SQLException e) { add(MessageBlock.error(e instanceof SQLException sql ? SqlErrorText.of(sql) : e.getMessage())); }
		return new Update(messageBlocks(), false);
	}

	private void switchConnection(com.datev.dbui.config.ConnectionParameters parameters, String profileName) throws SQLException {
		Connection next = parameters.open();
		try { next.setAutoCommit(true); next.setReadOnly(parameters.readOnly()); }
		catch (SQLException e) { next.close(); throw e; }
		Connection old = connection;
		connection = next;
		if (state != null) state.connected(parameters, profileName);
		else if (pendingStateBase != null) {
			state = new SessionState(directory(), new SessionContext(pendingStateBase,
				settings().withExecution(settings().rowLimit(), settings().timeout(), parameters.readOnly()), parameters, profileName, profiles::load));
		}
		else updateSettings(settings().withExecution(settings().rowLimit(), settings().timeout(), parameters.readOnly()));
		schemaCommands = new SchemaCommands(new SchemaProvider(next));
		lastResult = null;
		if (old != null) { try { old.close(); } catch (SQLException ignored) { } }
		add(MessageBlock.info("Connected to " + ConnectionInfo.of(parameters, profileName).label() + "."));
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
		if (connection != null) connection.close();
	}
}
