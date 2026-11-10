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
 * The directory of one Session: {@code <state base>/sessions/<id>/}. Today it holds only the Session log.
 * Issue 0017 (history, metadata, Resume) adds more files here, next to {@link #logFile()}.
 */
public final class SessionDirectory {

	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
	private static final SecureRandom RANDOM = new SecureRandom();

	private final String id;
	private final Path path;

	private SessionDirectory(String id, Path path) {
		this.id = id;
		this.path = path;
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

	public Path logFile() {
		return path.resolve("session.log");
	}
}
