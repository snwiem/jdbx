package de.snwiem.jdbx.session;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** The {@code sessions} directory below the state base: lists, forks and cleans up Session directories. */
public final class SessionStore {

	/** One earlier Session. */
	public record Entry(String id, SessionMetadata metadata) { }

	private static final Pattern ID = Pattern.compile("\\d{8}T\\d{6}Z-[0-9a-f]{8}");

	private final Path stateBase;
	private final Path sessions;

	public SessionStore(Path stateBase) {
		this.stateBase = stateBase;
		this.sessions = stateBase.resolve("sessions");
	}

	/** Earlier Sessions with a readable {@code session.yaml}, newest first. */
	public List<Entry> list(String excludeId, int max) {
		List<Entry> result = new ArrayList<>();
		for (String id : ids()) {
			if (id.equals(excludeId)) {
				continue;
			}
			try {
				result.add(new Entry(id, SessionMetadata.read(sessions.resolve(id).resolve("session.yaml"))));
			}
			catch (IOException e) {
				// Unreadable metadata: not a Session this version can resume.
			}
			if (result.size() == max) {
				break;
			}
		}
		return result;
	}

	/**
	 * Resume: creates a new Session directory that holds a copy of the history of {@code sourceId} and the given
	 * metadata marked {@code resumed-from}. The source directory is only read.
	 */
	public SessionDirectory fork(String sourceId, SessionMetadata metadata) throws IOException {
		var fork = SessionDirectory.create(stateBase);
		Path sourceHistory = sessions.resolve(sourceId).resolve("history");
		if (Files.isRegularFile(sourceHistory)) {
			Files.copy(sourceHistory, fork.historyFile());
		}
		metadata.forkedFrom(sourceId, Instant.now()).write(fork.metadataFile());
		return fork;
	}

	/**
	 * Removes the oldest inactive Session directories so that at most {@code keep} Sessions remain, the current one included. Never
	 * touches the current Session, a live Session (see {@link SessionDirectory#isLocked}; it does not count against {@code keep}), a
	 * directory without {@code session.yaml}, a name that is not a Session id, or a symbolic link.
	 */
	public void cleanup(int keep, String currentId) {
		List<String> ids = ids();
		int kept = ids.contains(currentId) ? 1 : 0; // the current Session always stays and counts
		for (String id : ids) {
			if (id.equals(currentId) || SessionDirectory.isLocked(sessions.resolve(id)) || ++kept <= keep) {
				continue;
			}
			try {
				delete(sessions.resolve(id));
			}
			catch (IOException e) {
				// Best effort: a directory that cannot be removed stays.
			}
		}
	}

	/** Ids of real Session directories that have a {@code session.yaml}, newest first. */
	private List<String> ids() {
		if (!Files.isDirectory(sessions)) {
			return List.of();
		}
		try (Stream<Path> children = Files.list(sessions)) {
			return children.filter(path -> ID.matcher(path.getFileName().toString()).matches())
				.filter(path -> Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS))
				.filter(path -> Files.isRegularFile(path.resolve("session.yaml"), java.nio.file.LinkOption.NOFOLLOW_LINKS))
				.map(path -> path.getFileName().toString()).sorted(Comparator.reverseOrder()).toList();
		}
		catch (IOException e) {
			return List.of();
		}
	}

	private static void delete(Path directory) throws IOException {
		try (Stream<Path> tree = Files.walk(directory)) {
			for (Path path : tree.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(path); // deletes a symbolic link itself, never its target
			}
		}
	}
}
