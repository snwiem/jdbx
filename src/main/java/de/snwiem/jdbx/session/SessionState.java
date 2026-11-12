package de.snwiem.jdbx.session;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import de.snwiem.jdbx.config.Settings;

/**
 * The persistent part of a Session: history, {@code session.yaml}, the settings in effect and Resume. It owns the
 * current {@link SessionDirectory}; Resume replaces it with a fork. Never writes a password.
 */
final class SessionState {

	static final int LIST_LIMIT = 20;

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

	/** A message for the user; the Session shows it in an error band. */
	static class ResumeException extends Exception {
		ResumeException(String message) {
			super(message);
		}
	}

	static final class PasswordRequired extends ResumeException {
		final String sessionId;
		PasswordRequired(String sessionId, String label) { super(label); this.sessionId = sessionId; }
	}

	/** The result of a Resume: a new connection (or {@code null} to keep the old one) and the text for an info band. */
	record Resumed(Connection connection, String message) { }

	@FunctionalInterface
	interface ConnectionOpener {
		Connection open(de.snwiem.jdbx.config.ConnectionParameters parameters, Settings settings) throws SQLException;
	}

	@FunctionalInterface
	interface ModeVerifier {
		void verify(Connection connection, boolean readOnly) throws SQLException;
	}

	private final SessionContext context;
	private final SessionStore store;
	private volatile SessionDirectory directory;
	private volatile HistoryStore history;
	private volatile Settings settings;
	private volatile ConnectionInfo info;
	private volatile de.snwiem.jdbx.config.ConnectionParameters parameters;
	private volatile SessionMetadata metadata;
	private List<SessionStore.Entry> listing;

	SessionState(SessionDirectory directory, SessionContext context) {
		this.context = context;
		this.store = new SessionStore(context.stateBase());
		this.directory = directory;
		this.settings = context.settings();
		this.parameters = context.parameters();
		this.info = ConnectionInfo.of(parameters, context.profileName());
		this.history = directory.disabledReason() != null ? HistoryStore.disabled()
			: HistoryStore.open(directory.historyFile(), settings.historyEnabled());
		this.metadata = metadataOf(settings, info, null);
		if (directory.disabledReason() == null) {
			writeMetadata();
			store.cleanup(settings.sessionsKeep(), directory.id());
		}
	}

	private static SessionMetadata metadataOf(Settings settings, ConnectionInfo info, String resumedFrom) {
		return new SessionMetadata(Instant.now(), info, settings.rowLimit(),
			settings.timeout() == null ? null : settings.timeout().toSeconds(), settings.readOnly(), null, resumedFrom, settings.format(), settings.orientation());
	}

	SessionDirectory directory() {
		return directory;
	}

	List<String> history() {
		return history.entries();
	}

	boolean historyEnabled() {
		return history.enabled();
	}

	void clearHistory() {
		history.clear();
	}

	Settings settings() {
		return settings;
	}

	/** Updates the same settings that execution uses, and persists them immediately, including Slash commands. */
	void settings(Settings settings) {
		this.settings = settings;
		metadata = new SessionMetadata(metadata.started(), metadata.connection(), settings.rowLimit(),
			settings.timeout() == null ? null : settings.timeout().toSeconds(), settings.readOnly(),
			metadata.lastStatement(), metadata.resumedFrom(), settings.format(), settings.orientation());
		writeMetadata();
	}

	/** The parameters of the connection in use, including the password. They change when {@code /resume} reconnects. */
	de.snwiem.jdbx.config.ConnectionParameters parameters() {
		return parameters;
	}

	String connectionLabel() {
		return info.label();
	}

	String profileName() { return info.profile(); }

	void connected(de.snwiem.jdbx.config.ConnectionParameters parameters, String profileName) {
		this.parameters = parameters;
		this.info = ConnectionInfo.of(parameters, profileName);
		metadata = metadata.withConnection(info);
		settings(settings.withExecution(settings.rowLimit(), settings.timeout(), parameters.readOnly()));
	}

	/** Records an input: history entry and, for a Statement, the last Statement in {@code session.yaml}. */
	void submitted(String input) {
		history.add(input);
		String stripped = input.stripLeading();
		if (!stripped.startsWith("/") || stripped.startsWith("/*")) {
			metadata = metadata.withLastStatement(input);
			writeMetadata();
		}
	}

	private void writeMetadata() {
		if (directory.metadataFile() == null) {
			return;
		}
		try {
			metadata.write(directory.metadataFile());
		}
		catch (IOException e) {
			// Best effort: the Session continues without up-to-date metadata.
		}
	}

	/** The numbered list of earlier Sessions for an info band. */
	String list() throws ResumeException {
		requireDirectory();
		listing = store.list(directory.id(), LIST_LIMIT);
		if (listing.isEmpty()) {
			throw new ResumeException("There is no earlier Session to resume.");
		}
		var text = new StringBuilder("Earlier Sessions, newest first. Type /resume <number> or /resume <id>.");
		for (int i = 0; i < listing.size(); i++) {
			var entry = listing.get(i);
			String statement = entry.metadata().lastStatement();
			text.append('\n').append(String.format("%2d  %s  %s  %s  [%s]", i + 1, entry.metadata().connection().label(),
				TIME.format(entry.metadata().started()), statement == null ? "(no Statement)" : statement, entry.id()));
		}
		return text.toString();
	}

	/**
	 * Forks the chosen Session and makes the fork current. On any problem nothing changes.
	 *
	 * @param current the open connection
	 */
	Resumed resume(String argument, Connection current, String password, ConnectionOpener opener, ModeVerifier verifier) throws ResumeException {
		requireDirectory();
		var chosen = choose(argument);
		var stored = chosen.metadata();
		Settings restored;
		try {
			restored = settings.withExecution(stored.rowLimit(), stored.timeoutSeconds() == null ? null
				: Duration.ofSeconds(stored.timeoutSeconds()), stored.readOnly()).withOutput(stored.format(), stored.orientation());
			ExecutionControls.validate(restored);
		}
		catch (IllegalArgumentException | ArithmeticException e) {
			throw new ResumeException("Invalid execution settings in Session " + chosen.id() + ": " + e.getMessage());
		}
		var plan = ResumeConnection.plan(chosen.id(), stored.connection(), stored.readOnly(), info, parameters, context.profiles(),
			settings);
		Connection reconnected = null;
		var newParameters = parameters;
		var newInfo = stored.connection();
		if (plan instanceof ResumeConnection.Prompt prompt) {
			if (password == null) throw new PasswordRequired(chosen.id(), prompt.message());
			var properties = new java.util.LinkedHashMap<>(prompt.parameters().properties());
			properties.put("password", password);
			plan = new ResumeConnection.Reconnect(new de.snwiem.jdbx.config.ConnectionParameters(prompt.parameters().url(), properties, stored.readOnly()));
		}
		boolean previousReadOnly = false;
		boolean restoreReadOnly = false;
		try {
			if (plan instanceof ResumeConnection.Keep && (current == null || current.isClosed())) {
				plan = new ResumeConnection.Reconnect(parameters);
			}
			if (plan instanceof ResumeConnection.Reconnect reconnect) {
				newParameters = reconnect.parameters();
				reconnected = opener.open(newParameters, restored);
				newInfo = ConnectionInfo.of(newParameters, stored.connection().profile());
			}
			else {
				previousReadOnly = current.isReadOnly();
				if (previousReadOnly != restored.readOnly()) {
					// pgjdbc rejects this during an open transaction. Do not fork or change Settings first.
					current.setReadOnly(restored.readOnly());
					restoreReadOnly = true;
				}
				verifier.verify(current, restored.readOnly());
			}
			var fork = store.fork(chosen.id(), stored.withConnection(newInfo));
			metadata = SessionMetadata.read(fork.metadataFile());
			var previous = directory;
			directory = fork;
			previous.release(); // the earlier Session directory is no longer in use
		}
		catch (SQLException | IOException e) {
			if (restoreReadOnly) {
				try {
					current.setReadOnly(previousReadOnly);
				}
				catch (SQLException restoreFailure) {
					e.addSuppressed(restoreFailure);
				}
			}
			if (reconnected != null) {
				try {
					reconnected.close();
				}
				catch (SQLException ignored) {
					// The failure that matters is the one above.
				}
			}
			throw new ResumeException("Cannot resume Session " + chosen.id() + ": " + e.getMessage());
		}
		parameters = new de.snwiem.jdbx.config.ConnectionParameters(newParameters.url(), newParameters.properties(), restored.readOnly());
		info = newInfo;
		settings = restored;
		history = HistoryStore.open(directory.historyFile(), settings.historyEnabled());
		listing = null;
		store.cleanup(settings.sessionsKeep(), directory.id());
		return new Resumed(reconnected, "Resumed Session " + chosen.id() + " (" + newInfo.label() + ") as new Session "
			+ directory.id() + ". History and settings are restored.");
	}

	private SessionStore.Entry choose(String argument) throws ResumeException {
		List<SessionStore.Entry> candidates = listing != null ? listing : store.list(directory.id(), LIST_LIMIT);
		if (argument.matches("\\d+")) {
			int number = Integer.parseInt(argument);
			if (number < 1 || number > candidates.size()) {
				throw new ResumeException("No Session number " + number + ". Type /resume to list the earlier Sessions.");
			}
			return candidates.get(number - 1);
		}
		for (var entry : new ArrayList<>(store.list(directory.id(), Integer.MAX_VALUE))) {
			if (entry.id().equals(argument)) {
				return entry;
			}
		}
		throw new ResumeException("No earlier Session '" + argument + "'. Type /resume to list the earlier Sessions.");
	}

	private void requireDirectory() throws ResumeException {
		if (directory.disabledReason() != null) {
			throw new ResumeException("/resume is not available: history and Session log are disabled: " + directory.disabledReason());
		}
	}
}
