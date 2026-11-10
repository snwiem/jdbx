package com.datev.dbui.session;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;

/**
 * The directory of one Session: {@code <state base>/sessions/<id>/}. It holds the Session log,
 * the input history and the Session metadata.
 */
public final class SessionDirectory {

	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
	private static final SecureRandom RANDOM = new SecureRandom();

	private final String id;
	private final Path path;
	private final String disabledReason;

	private SessionDirectory(String id, Path path) {
		this(id, path, null);
	}

	private SessionDirectory(String id, Path path, String disabledReason) {
		this.id = id;
		this.path = path;
		this.disabledReason = disabledReason;
	}

	/**
	 * Like {@link #create(Path)}, but a failure does not abort the application: the result is a disabled directory
	 * that has no files and says why in {@link #disabledReason()}.
	 */
	public static SessionDirectory createOrDisabled(Path stateBase) {
		try {
			return create(stateBase);
		}
		catch (IOException | RuntimeException e) {
			String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
			return new SessionDirectory(null, null, stateBase + ": " + reason);
		}
	}

	/** The reason why history and Session log are disabled, or {@code null} when the directory works. */
	public String disabledReason() {
		return disabledReason;
	}

	/** DBUI_STATE_DIR, else $XDG_STATE_HOME/dbui, else ~/.local/state/dbui. Empty variables count as unset. */
	public static Path stateBase(Map<String, String> environment, Path home) {
		String override = environment.get("DBUI_STATE_DIR");
		if (override != null && !override.isEmpty()) {
			return Path.of(override);
		}
		String xdg = environment.get("XDG_STATE_HOME");
		if (xdg != null && !xdg.isEmpty()) {
			return Path.of(xdg).resolve("dbui");
		}
		return home.resolve(".local").resolve("state").resolve("dbui");
	}

	public static Path defaultStateBase() {
		return stateBase(System.getenv(), Path.of(System.getProperty("user.home")));
	}

	/** Creates a new, empty Session directory with a unique id: UTC timestamp plus a random suffix. */
	public static SessionDirectory create(Path stateBase) throws IOException {
		Path sessions = stateBase.resolve("sessions");
		Files.createDirectories(sessions);
		while (true) {
			byte[] suffix = new byte[4];
			RANDOM.nextBytes(suffix);
			String id = TIMESTAMP.format(Instant.now()) + "-" + HexFormat.of().formatHex(suffix);
			Path path = sessions.resolve(id);
			try {
				Files.createDirectory(path);
				Files.createFile(path.resolve("session.log"));
				return new SessionDirectory(id, path);
			}
			catch (java.nio.file.FileAlreadyExistsException e) {
				// Same id drawn twice: draw again.
			}
		}
	}

	public String id() {
		return id;
	}

	public Path path() {
		return path;
	}

	/** The Session log file, or {@code null} when the directory is disabled. */
	public Path logFile() {
		return path == null ? null : path.resolve("session.log");
	}

	/** The input history file, or {@code null} when the directory is disabled. */
	public Path historyFile() {
		return path == null ? null : path.resolve("history");
	}

	/** The Session metadata file, or {@code null} when the directory is disabled. */
	public Path metadataFile() {
		return path == null ? null : path.resolve("session.yaml");
	}
}
