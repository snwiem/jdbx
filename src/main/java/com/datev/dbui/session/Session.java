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
import com.datev.dbui.completion.Completion;
import com.datev.dbui.completion.CompletionEngine;
import com.datev.dbui.schema.SchemaCommands;
import com.datev.dbui.schema.SchemaProvider;
import com.datev.dbui.schema.SchemaProviderModel;

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
	private final TranscriptMemory transcript = new TranscriptMemory();
	private final ExecutionControls controls;
	private SchemaCommands schemaCommands;
	private final SlashCommandRegistry slashCommands;
	private SchemaProvider schemaProvider;
	private final CompletionEngine completionEngine;
	private com.datev.dbui.profile.ProfileCommands profiles;
	private volatile SessionInteraction interaction;
	private java.util.function.Function<String, Update> passwordContinuation;
	private boolean ended;
	private volatile boolean connectionLost;
	private boolean running;
	private volatile boolean cancelRequested;
	private Statement activeStatement;
	private boolean streamingStatement;
	private final Object cancelLock = new Object();
	private long startedAt;
	private ExecutionStatus.ResultSummary lastResult;
	private boolean heldResultTruncated;
	private boolean heldQueryResult;
	private CopyCommand copy = new CopyCommand(sequence -> { throw new IOException("Terminal clipboard is unavailable."); });
	private String lastSqlInput;

	/** Direct Interactive startup uses the same checked opener as /connect, Resume and reconnect. */
	public static Session connect(SessionDirectory directory, SessionContext context) throws SQLException {
		var connection = openConnection(context.parameters(), context.settings());
		try {
			return new Session(connection, directory, context);
		}
		catch (SQLException | RuntimeException e) {
			try { connection.close(); } catch (SQLException closeFailure) { e.addSuppressed(closeFailure); }
			throw e;
		}
	}

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
		this.slashCommands = new SlashCommandRegistry(commands());
		this.completionEngine = new CompletionEngine(List.of(new com.datev.dbui.completion.SlashCommandSource(slashCommands),
			new com.datev.dbui.completion.SchemaSource(), new com.datev.dbui.completion.KeywordSource()));
		if (connection != null) {
			this.schemaProvider = new SchemaProvider(connection);
			this.schemaCommands = new SchemaCommands(schemaProvider);
			connection.setAutoCommit(true);
			connection.setReadOnly(settings.readOnly());
		}
		if (directory.disabledReason() != null) {
			add(MessageBlock.info("history and Session log are disabled: " + directory.disabledReason()));
		}
	}

	/** Enables the Profile commands without changing the persistent Session context. */
	public void configureProfiles(Path directory, java.util.Map<String, String> environment) {
		profiles = new com.datev.dbui.profile.ProfileCommands(directory, environment);
		String warning = profiles.warning();
		if (warning != null) add(MessageBlock.info(warning));
	}

	/** Installed by the terminal adapter; no terminal is needed to exercise /copy. */
	public void clipboard(ClipboardSink sink) { copy = new CopyCommand(sink); }

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
		if (running || !(interaction instanceof SessionInteraction.Password prompt) || passwordContinuation == null) return CompletableFuture.failedFuture(new IllegalStateException("No password prompt is open."));
		var continuation = passwordContinuation;
		interaction = null;
		passwordContinuation = null;
		return run(() -> {
			Update update = continuation.apply(password);
			if (interaction == null && !update.messageBlocks().isEmpty() && update.messageBlocks().getLast().kind() == MessageBlock.Kind.ERROR) {
				interaction = prompt;
				passwordContinuation = continuation;
			}
			return update;
		});
	}

	private void connectProfile(com.datev.dbui.config.Profile profile, String profileName) throws SQLException {
		var supplied = profile;
		if (profile.password() == null && (profile.url() == null || !profile.url().matches(".*[?&]password=.*"))) {
			interaction = new SessionInteraction.Password(profile.user() == null ? "database" : profile.user());
			passwordContinuation = password -> {
				try {
					var withPassword = new com.datev.dbui.config.Profile(supplied.name(), supplied.url(), supplied.host(), supplied.port(), supplied.database(), supplied.user(), password,
						supplied.readOnly(), supplied.schema(), supplied.properties());
					switchConnection(com.datev.dbui.config.ConnectionParameters.resolve(new com.datev.dbui.config.ConnectionOptions(), withPassword, connectionDefaults()), profileName);
				}
				catch (com.datev.dbui.config.ConfigException | SQLException e) { add(MessageBlock.error(e instanceof SQLException sql ? SqlErrorText.of(sql) : e.getMessage())); }
				return new Update(messageBlocks(), false);
			};
			return;
		}
		switchConnection(com.datev.dbui.config.ConnectionParameters.resolve(new com.datev.dbui.config.ConnectionOptions(), supplied, connectionDefaults()), profileName);
	}

	private Settings connectionDefaults() {
		// Across /connect, keep the current mode unless the selected Profile explicitly overrides it.
		return settings();
	}

	public synchronized CompletableFuture<Update> saveDialog(boolean save) {
		if (running || !(interaction instanceof SessionInteraction.Dialog dialog)) return CompletableFuture.failedFuture(new IllegalStateException("No Profile dialog is open."));
		return run(() -> {
			SessionInteraction.Dialog retry = dialog;
			try {
				dialog.model().validate(save);
				if (!save && !dialog.connect()) throw new com.datev.dbui.config.ConfigException("Profile edits must be saved.");
				if (save) {
					profiles.save(dialog.model(), dialog.editingName());
					add(MessageBlock.info("Saved Profile '" + dialog.model().savedProfile().name() + "'."));
					retry = new SessionInteraction.Dialog(dialog.model(), dialog.connect(), dialog.model().savedProfile().name());
				}
				interaction = null;
				if (dialog.connect()) {
					var profile = profiles.resolve(dialog.model().connectionProfile());
					connectProfile(profile, save ? profile.name() : null);
				}
			}
			catch (com.datev.dbui.config.ConfigException | SQLException e) {
				interaction = retry;
				add(MessageBlock.error(e instanceof SQLException sql ? SqlErrorText.of(sql) : e.getMessage()));
			}
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
		return transcript.blocks();
	}

	public synchronized ExecutionStatus status() {
		return new ExecutionStatus(controls.rowLimit(), running,
			running ? Duration.ofNanos(System.nanoTime() - startedAt) : Duration.ZERO, lastResult, settings().readOnly(), connection != null && !connectionLost);
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
		if (HistoryStore.containsProfileCredentials(input)) {
			return CompletableFuture.completedFuture(errorUpdate("Inline Profile credentials are not accepted. Use the dialog or masked password prompt."));
		}
		add(MessageBlock.input(input));
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
		lastSqlInput = input;
		return runOnConnection(() -> execute(input));
	}

	/** Like {@link #run}, but first restores a lost connection (once); when that fails the work does not run. */
	private CompletableFuture<Update> runOnConnection(Supplier<Update> work) {
		return run(() -> ensureConnected() ? work.get() : new Update(messageBlocks(), false));
	}

	/** Key action, not an input: persist and confirm it without changing input history or echoing a Slash command. */
	public synchronized Update cycleOrientation() {
		if (!ended && !running) {
			String orientation = com.datev.dbui.Orientation.parse(settings().orientation()) == com.datev.dbui.Orientation.HORIZONTAL
				? "vertical" : "horizontal";
			updateSettings(settings().withOutput(settings().format(), orientation));
			add(MessageBlock.info("Orientation: " + orientation));
		}
		return new Update(messageBlocks(), ended);
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

	/**
	 * Completion for the editor text with the cursor at {@code cursor}. It runs on the Statement thread, so it never
	 * overlaps a running Statement or schema command, and it reads the schema cache. The first request loads the
	 * cache (a few metadata queries) and takes longer; the future completes when the candidates are ready.
	 */
	public CompletableFuture<Completion> complete(String text, int cursor) {
		return CompletableFuture.supplyAsync(() -> completionEngine.complete(text, cursor,
			connection == null || connectionLost ? com.datev.dbui.completion.SchemaModel.EMPTY : new SchemaProviderModel(schemaProvider)), executor);
	}

	/** Requests JDBC cancellation without blocking the input thread. The Session remains open. */
	public synchronized CompletableFuture<Void> cancel() {
		if (!running) {
			return CompletableFuture.completedFuture(null);
		}
		cancelRequested = true;
		Statement statement = activeStatement;
		boolean streaming = streamingStatement;
		return CompletableFuture.runAsync(() -> {
			// pgjdbc ignores cancel while idle. Retry across its idle-to-executing window, but never target a later input.
			while (statement != null && isActive(statement)) {
				try {
					if (streaming) cancelStreaming(statement);
					else statement.cancel();
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

	/** pgjdbc Statement.cancel ignores cursor fetches. Its connection-level cancellation covers those too. */
	private void cancelStreaming(Statement statement) throws SQLException {
		// finished() takes this same lock before ending the input, so the packet cannot reach a later input.
		// Do not hold the Session monitor during socket I/O: status and Ctrl-C must remain responsive.
		synchronized (cancelLock) {
			if (isActive(statement)) connection.unwrap(org.postgresql.PGConnection.class).cancelQuery();
		}
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
			SlashCommand.argument("N", "1", List.of(), ExecutionControls::positiveInteger), false, argument -> List.of(controls.limit(argument))));
		commands.add(new SlashCommand("timeout", "Change the Statement timeout (seconds: 1 to 2147483647), or turn it off.",
			SlashCommand.argument("<seconds|off>", "1", List.of("off"),
				value -> value.equals("off") ? value : ExecutionControls.positiveInteger(value)), false,
			argument -> List.of(controls.timeout(argument))));
		commands.add(new SlashCommand("readonly", "Show or change the Session Read-only setting.",
			SlashCommand.argument("on|off", "0..1", List.of("on", "off"), value -> {
				if (ReadOnlyCommand.parse("/readonly " + value) == ReadOnlyCommand.Kind.USAGE) {
					throw new IllegalArgumentException(ReadOnlyCommand.USAGE);
				}
				return value;
			}), true, argument -> {
				if (argument.isEmpty()) {
					return List.of(MessageBlock.info(ReadOnlyCommand.describe(settings().readOnly())));
				}
				if (ensureConnected(argument.equals("on"))) {
					changeReadOnly(argument.equals("on"));
				}
				return List.of();
			}));
		commands.add(schemaCommand("describe", "List schemas and tables, or describe a table.", "<schema|table>", "0..1", List.of()));
		commands.add(schemaCommand("refresh", "Clear the schema cache.", null, "0", List.of()));
		commands.add(new SlashCommand("resume", "List earlier Sessions, or fork one by number or id.",
			SlashCommand.argument("number|id", "0..1", List.of()), true, argument -> {
				if (state == null) {
					return List.of(MessageBlock.error("/resume is not available without a Session context."));
				}
				resume(argument);
				return List.of();
			}));
		commands.add(new SlashCommand("connect", "List Profiles or switch to one; open a dialog when none exist.",
			SlashCommand.argument("name|number", "0..1", () -> (profiles == null ? List.<String>of() : profiles.completionNames()).iterator()),
			true, argument -> {
				profileCommand("/connect" + (argument.isEmpty() ? "" : " " + argument));
				return List.of();
			}));
		commands.add(new SlashCommand("profile", "List, add, edit or remove a saved Profile.",
			SlashCommand.argument("list|add|edit <name|number>|remove <name|number>", "1", List.of("list", "add", "edit", "remove")),
			true, argument -> {
				profileCommand("/profile " + argument);
				return List.of();
			}));
		commands.add(new SlashCommand("format", "Change the Format for new Results.",
			SlashCommand.argument("<table|markdown|csv|tsv|json|json-pretty>", "1", List.of("table", "markdown", "csv", "tsv", "json", "json-pretty"),
				value -> com.datev.dbui.Format.parse(value).id()), false, argument -> {
				updateSettings(settings().withOutput(argument, settings().orientation()));
				return List.of(MessageBlock.info("Format: " + argument));
			}));
		commands.add(new SlashCommand("orientation", "Change the Orientation for new Results (json and json-pretty ignore it).",
			SlashCommand.argument("<horizontal|vertical>", "1", List.of("horizontal", "vertical"),
				value -> com.datev.dbui.Orientation.parse(value).id()), false, argument -> {
				updateSettings(settings().withOutput(settings().format(), argument));
				return List.of(MessageBlock.info("Orientation: " + argument));
			}));
		commands.add(new SlashCommand("copy", "Send the held query Result in the current Format and Orientation to the terminal clipboard; "
			+ "requires OSC 52 support. Limit: 64 KB of formatted UTF-8 text; use /save for larger Results.", null, false,
			argument -> List.of(copy.execute(heldQueryResult ? transcript.heldResult() : null, settings(),
				heldResultTruncated ? transcript.heldResult().rows().size() : null))));
		commands.add(new SlashCommand("save", "Write the held Result in the current Format and Orientation.",
			SlashCommand.argument("<file>", "1", List.of()), true, argument -> {
				saveResult(argument);
				return List.of();
			}));
		commands.add(new SlashCommand("dump", "Run all query Statements in READ ONLY transactions; write every row without the Row limit.",
			SlashCommand.argument("<file> [sql]", "1", List.of()), true, argument -> {
				dumpResults(argument);
				return List.of();
			}));
		commands.add(new SlashCommand("help", "List Slash commands and key bindings.", null, false,
			argument -> List.of(MessageBlock.info(slashCommands.help(settings().keyBindings())))));
		return commands;
	}

	private SlashCommand schemaCommand(String name, String help, String label, String arity, List<String> candidates) {
		return new SlashCommand(name, help, label == null ? null : SlashCommand.argument(label, arity, candidates), true,
			argument -> {
				if (ensureConnected()) {
					runSchemaCommand(name, argument);
				}
				return List.of();
			});
	}

	private void dumpResults(String argument) {
		try {
			var request = FileCommandLine.parse(argument, true);
			String sql = request.sql();
			var statements = com.datev.dbui.ResultFiles.queries(sql.isEmpty() ? (lastSqlInput == null ? "" : lastSqlInput) : sql);
			if (!ensureConnected()) return;
			try (var statement = connection.createStatement()) {
				statement.setQueryTimeout(controls.timeoutSeconds());
				activeStreaming(statement);
				long rows = com.datev.dbui.ResultFiles.dump(connection, statement, statements, settings(), request.file(), () -> cancelRequested);
				add(MessageBlock.info("Dumped " + rows + " rows to " + request.file() + "."));
			}
		}
		catch (SQLException e) { add(sqlError(e)); }
		catch (IOException | IllegalArgumentException e) { add(MessageBlock.error("Cannot dump: " + e.getMessage())); }
		catch (RuntimeException | Error e) { unexpectedError(e); }
	}

	private void saveResult(String argument) {
		var result = transcript.heldResult();
		if (result == null || result.rows().isEmpty()) {
			add(MessageBlock.info("No non-empty Result is held. No file was written."));
			return;
		}
		try {
			var request = FileCommandLine.parse(argument, false);
			com.datev.dbui.ResultFiles.save(result, settings(), request.file(), () -> cancelRequested);
			add(MessageBlock.info("Saved " + result.rows().size() + " rows to " + request.file()
				+ (heldResultTruncated ? ". Warning: the held Result was truncated." : ".")));
		}
		catch (IOException | SQLException | IllegalArgumentException e) {
			add(MessageBlock.error("Cannot save " + argument + ": " + e.getMessage()));
		}
		catch (RuntimeException | Error e) { unexpectedError(e); }
	}

	private void unexpectedError(Throwable error) {
		add(MessageBlock.error("Unexpected error: " + error.getMessage() + (logFile() == null ? "" : ". Details: " + logFile())));
		writeLog(error);
	}

	private Update resume(String argument) { return resume(argument, null); }

	private Update resume(String argument, String password) {
		try {
			if (argument.isEmpty()) {
				add(MessageBlock.info(state.list()));
				return new Update(messageBlocks(), false);
			}
			var resumed = state.resume(argument, connectionLost ? null : connection, password, Session::openConnection, Session::verifyReadOnly);
			replaceConnection(resumed.connection() == null ? connection : resumed.connection(),
				state.parameters(), state.profileName());
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
			connectionLost |= isConnectionLost(e);
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
		verifyReadOnly(connection, expected);
	}

	private static void verifyReadOnly(Connection connection, boolean expected) throws SQLException {
		boolean guarded = connection.isReadOnly();
		if (guarded) {
			connection.setAutoCommit(false);
		}
		try (var statement = connection.createStatement(); var rows = statement.executeQuery("show transaction_read_only")) {
			rows.next();
			if (rows.getString(1).equals("on") != expected) {
				throw new SQLException(expected ? "the server did not apply Read-only"
					: "the server is read-only; use --read-only or a Profile with read-only: true", "25000");
			}
		}
		finally {
			if (guarded) {
				endReadOnlyTransaction(connection);
			}
		}
	}

	private Update profileCommand(String input) {
		try {
			if (profiles == null) {
				return errorUpdate("Profile commands are not available without a Profile directory.");
			}
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
		var nextSettings = settings().withExecution(settings().rowLimit(), settings().timeout(), parameters.readOnly());
		Connection next = openConnection(parameters, nextSettings);
		if (state == null && pendingStateBase != null) {
			state = new SessionState(directory(), new SessionContext(pendingStateBase,
				settings().withExecution(settings().rowLimit(), settings().timeout(), parameters.readOnly()), parameters, profileName, profiles::load));
		}
		replaceConnection(next, parameters, profileName);
		add(MessageBlock.info("Connected to " + ConnectionInfo.of(parameters, profileName).label()
			+ " (" + ReadOnlyCommand.describe(parameters.readOnly()) + ")."));
	}

	private Update errorUpdate(String message) {
		add(MessageBlock.error(message));
		return new Update(messageBlocks(), ended);
	}

	private void finished() {
		synchronized (cancelLock) {
			synchronized (this) {
				activeStatement = null;
				streamingStatement = false;
				running = false;
			}
		}
	}

	private synchronized void active(Statement statement) {
		activeStatement = statement;
	}

	private synchronized void activeStreaming(Statement statement) {
		activeStatement = statement;
		streamingStatement = true;
	}

	private synchronized void resultSummary(int rows, long statementStartedAt, boolean truncated) {
		lastResult = new ExecutionStatus.ResultSummary(rows, Duration.ofNanos(System.nanoTime() - statementStartedAt), truncated);
	}

	private Update runSchemaCommand(String name, String argument) {
		try {
			schemaCommands.run(name, argument).forEach(this::add);
		}
		catch (SQLException e) {
			add(sqlError(e));
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
			add(sqlError(e));
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
		endReadOnlyTransaction(connection);
	}

	private static void endReadOnlyTransaction(Connection connection) throws SQLException {
		try {
			connection.rollback();
		}
		catch (SQLException ignored) {
			// the connection may be broken; setAutoCommit below reports it if it matters
		}
		connection.setAutoCommit(true);
	}

	/** An error band for a failed Statement. A lost connection is also remembered, so that the next input reconnects. */
	private MessageBlock sqlError(SQLException e) {
		if (!isConnectionLost(e)) {
			return MessageBlock.error(SqlErrorText.of(e));
		}
		connectionLost = true;
		return MessageBlock.error("Connection lost: " + SqlErrorText.of(e) + ". The Statement failed and is not run again. "
			+ "The next input reconnects once.");
	}

	private boolean isConnectionLost(SQLException e) {
		String state = e.getSQLState();
		if (state != null && (state.startsWith("08") || state.equals("57P01") || state.equals("57P02") || state.equals("57P03"))) {
			return true;
		}
		try {
			return connection.isClosed();
		}
		catch (SQLException ignored) {
			return true;
		}
	}

	/** Restores a lost connection once with the parameters of the Session. Returns false and adds an error band on failure. */
	private boolean ensureConnected() { return ensureConnected(null); }

	/** A requested /readonly mode is committed only after the replacement passes server verification. */
	private boolean ensureConnected(Boolean requestedReadOnly) {
		if (connection == null) {
			add(MessageBlock.error("Not connected. Use /connect or /profile add."));
			return false;
		}
		if (!connectionLost) {
			return true;
		}
		if (state == null) {
			add(MessageBlock.error("Cannot reconnect: this Session has no connection parameters."));
			return false;
		}
		var current = requestedReadOnly == null ? settings()
			: settings().withExecution(settings().rowLimit(), settings().timeout(), requestedReadOnly);
		var previous = state.parameters();
		var parameters = new com.datev.dbui.config.ConnectionParameters(previous.url(), previous.properties(), current.readOnly());
		try {
			replaceConnection(openConnection(parameters, current), parameters, state.profileName());
		}
		catch (SQLException | RuntimeException e) {
			add(MessageBlock.error("Cannot reconnect to " + state.connectionLabel() + ": " + e.getMessage()));
			return false;
		}
		add(MessageBlock.info("Reconnected to " + state.connectionLabel() + " (connection was lost)."));
		return true;
	}

	/** One opening path for /connect, Resume and reconnect. Driver properties apply the initial search_path. */
	private static Connection openConnection(com.datev.dbui.config.ConnectionParameters parameters, Settings settings) throws SQLException {
		ExecutionControls.validate(settings);
		var effective = new com.datev.dbui.config.ConnectionParameters(parameters.url(), parameters.properties(), settings.readOnly());
		Connection next = effective.open();
		try {
			next.setAutoCommit(true);
			next.setReadOnly(settings.readOnly());
			verifyReadOnly(next, settings.readOnly());
			// Timeout and Row limit are Statement-scoped and read from these same Settings on every execution.
			return next;
		}
		catch (SQLException | RuntimeException e) {
			try { next.close(); } catch (SQLException closeFailure) { e.addSuppressed(closeFailure); }
			throw e;
		}
	}

	/** One installation path invalidates both caches, persists settings/label and closes the old connection. */
	private synchronized void replaceConnection(Connection next, com.datev.dbui.config.ConnectionParameters parameters, String profileName) {
		var old = connection;
		if (state != null) state.connected(parameters, profileName);
		else updateSettings(settings().withExecution(settings().rowLimit(), settings().timeout(), parameters.readOnly()));
		connection = next;
		schemaProvider = new SchemaProvider(next);
		schemaCommands = new SchemaCommands(schemaProvider);
		connectionLost = false;
		lastResult = null;
		if (old != next && old != null) {
			try {
				old.close();
			}
			catch (SQLException ignored) {
				// The replacement is ready; closing the old connection is best effort.
			}
		}
	}

	private void runStatement(Statement statement, String sql, int rowLimit, long statementStartedAt) throws SQLException {
		if (statement.execute(sql)) {
			try (var rows = statement.getResultSet()) {
				var read = JdbcResultReader.read(rows, rowLimit);
				resultSummary(read.result().rows().size(), statementStartedAt, read.truncated());
				add(MessageBlock.result(read.result()));
				heldResultTruncated = read.truncated();
				heldQueryResult = true;
				if (read.truncated()) {
					add(MessageBlock.info("truncated at " + rowLimit + " rows"));
				}
			}
		}
		else {
			heldQueryResult = false;
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
		if (block.kind() == MessageBlock.Kind.RESULT) {
			heldResultTruncated = false;
			heldQueryResult = false; // Ordinary SQL marks its Result after adding it; schema Results are Save-only.
		}
		transcript.add(block.kind() == MessageBlock.Kind.RESULT ? MessageBlock.result(block.result(), settings()) : block);
	}

	@Override
	public void close() throws SQLException {
		cancel();
		executor.close();
		cancellations.close();
		try {
			if (connection != null) connection.close();
		}
		finally {
			directory().release(); // the Session ends: other processes may clean up its directory
		}
	}
}
