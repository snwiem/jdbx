package org.syracus.jdbx.session;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The directory of one Session: {@code <state base>/sessions/<id>/}. It holds the Session log,
 * the input history and the Session metadata.
 *
 * <p>A directory that was created by {@link #create} also holds an operating system lock on its {@code session.lock} file until
 * {@link #release()} or the end of the process. {@link #isLocked} tells whether a Session still uses a directory. The operating
 * system releases the lock when the process ends or crashes, so a stale lock file does not keep a directory alive.
 */
public final class SessionDirectory {

	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
	private static final SecureRandom RANDOM = new SecureRandom();

	private final String id;
	private final Path path;
	private final String disabledReason;
	private FileChannel lockChannel; // null when disabled, released, or the file system cannot lock
	private Path lockKey;

	private SessionDirectory(String id, Path path) {
		this(id, path, null);
	}

	private SessionDirectory(String id, Path path, String disabledReason) {
		this.id = id;
		this.path = path;
		this.disabledReason = disabledReason;
	}

	static final String LOCK_FILE = "session.lock";

	/**
	 * Lock files that this JVM holds. POSIX locks belong to the process, not to the channel: on some systems, closing any other channel
	 * on the same file in this JVM drops the lock (see {@link FileLock}). So {@link #isLocked} answers for these files from this set and never opens them.
	 */
	private static final Set<Path> HELD = ConcurrentHashMap.newKeySet();

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

	/** JDBX_STATE_DIR, else $XDG_STATE_HOME/jdbx, else ~/.local/state/jdbx. Empty variables count as unset. */
	public static Path stateBase(Map<String, String> environment, Path home) {
		String override = environment.get("JDBX_STATE_DIR");
		if (override != null && !override.isEmpty()) {
			return Path.of(override);
		}
		String xdg = environment.get("XDG_STATE_HOME");
		if (xdg != null && !xdg.isEmpty()) {
			return Path.of(xdg).resolve("jdbx");
		}
		return home.resolve(".local").resolve("state").resolve("jdbx");
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
				var directory = new SessionDirectory(id, path);
				directory.lock();
				return directory;
			}
			catch (java.nio.file.FileAlreadyExistsException e) {
				// Same id drawn twice: draw again.
			}
		}
	}

	/**
	 * Takes the lock that marks this Session as live. Best effort: on a file system without locks the Session runs without one, and
	 * {@link #isLocked} then treats the directory as live for every other process, because it cannot probe either.
	 */
	private void lock() {
		Path lockFile = path.resolve(LOCK_FILE);
		try {
			var channel = FileChannel.open(lockFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
			try {
				FileLock lock = channel.tryLock();
				if (lock == null) {
					channel.close();
					return;
				}
			}
			catch (IOException | RuntimeException e) {
				channel.close();
				return;
			}
			lockChannel = channel;
			lockKey = lockFile.toRealPath();
			HELD.add(lockKey);
		}
		catch (IOException e) {
			// No lock: see above.
		}
	}

	/** Ends the lock of this Session so that cleanup may remove the directory. Safe to call more than once. */
	public synchronized void release() {
		if (lockChannel == null) {
			return;
		}
		HELD.remove(lockKey);
		try {
			lockChannel.close(); // closing the channel releases its lock
		}
		catch (IOException e) {
			// Nothing to recover: the process end releases the lock anyway.
		}
		lockChannel = null;
	}

	/**
	 * Whether a Session, in this process or another, still uses the Session directory {@code directory}. A directory without a lock
	 * file is not locked. When the lock state cannot be determined, the answer is {@code true}: keeping a directory is the safe error.
	 */
	public static boolean isLocked(Path directory) {
		Path lockFile = directory.resolve(LOCK_FILE);
		if (!Files.isRegularFile(lockFile, LinkOption.NOFOLLOW_LINKS)) {
			return false;
		}
		try {
			if (HELD.contains(lockFile.toRealPath())) {
				return true;
			}
			try (var channel = FileChannel.open(lockFile, StandardOpenOption.WRITE)) {
				FileLock lock = channel.tryLock();
				if (lock == null) {
					return true;
				}
				lock.release();
				return false;
			}
		}
		catch (OverlappingFileLockException e) {
			return true;
		}
		catch (IOException | RuntimeException e) {
			return true;
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
