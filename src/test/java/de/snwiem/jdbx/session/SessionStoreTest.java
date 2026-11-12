package de.snwiem.jdbx.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.snwiem.jdbx.config.ConnectionParameters;

class SessionStoreTest {

	@TempDir
	Path base;

	private static SessionMetadata metadata(String lastStatement) {
		var connection = new ConnectionInfo("db.example", 5432, "shop", "alice", "jdbc:postgresql://db.example:5432/shop", "prod");
		return new SessionMetadata(Instant.parse("2026-11-10T10:00:00Z"), connection, 500, 30L, true, lastStatement, null);
	}

	private static String tree(Path dir) throws Exception {
		try (Stream<Path> files = Files.walk(dir)) {
			var out = new StringBuilder();
			for (Path file : files.sorted().toList()) {
				out.append(dir.relativize(file)).append(Files.isRegularFile(file) ? ":" + Files.readString(file) : "").append('\n');
			}
			return out.toString();
		}
	}

	@Test
	void metadataRoundTripsAndNeverContainsThePassword() throws Exception {
		var parameters = new ConnectionParameters("jdbc:postgresql://u:pw-in-url@h:6432/d?password=pw-in-query&sslmode=require",
			Map.of("user", "bob", "password", "pw-in-properties"), false);
		var info = ConnectionInfo.of(parameters, null);
		assertThat(info.host()).isEqualTo("h");
		assertThat(info.port()).isEqualTo(6432);
		assertThat(info.database()).isEqualTo("d");
		assertThat(info.user()).isEqualTo("bob");
		assertThat(info.label()).isEqualTo("bob@h:6432/d");
		var written = new SessionMetadata(Instant.parse("2026-11-10T10:00:00Z"), info, 1000, null, false, "select 1", "OLD-ID");
		Path file = base.resolve("session.yaml");
		written.write(file);
		assertThat(Files.readString(file)).doesNotContain("pw-in-url", "pw-in-query", "pw-in-properties", "password=");
		assertThat(SessionMetadata.read(file)).isEqualTo(written);
	}

	@Test
	void lastStatementIsShortenedToOneLine() {
		String longText = "select\n  " + "x, ".repeat(100) + "y from t";
		var shortened = metadata(null).withLastStatement(longText).lastStatement();
		assertThat(shortened).doesNotContain("\n").hasSizeLessThanOrEqualTo(80).startsWith("select x, x,").endsWith("…");
	}

	@Test
	void forkCopiesHistoryAndMetadataAndLeavesTheOriginalUnchanged() throws Exception {
		var store = new SessionStore(base);
		var original = SessionDirectory.create(base);
		HistoryStore.open(original.historyFile(), true).add("select 1\nfrom t");
		metadata("select 1").write(original.metadataFile());
		String before = tree(original.path());

		var fork = store.fork(original.id(), metadata("select 1"));
		var secondFork = store.fork(original.id(), metadata("select 1"));

		assertThat(tree(original.path())).isEqualTo(before);
		assertThat(fork.id()).isNotEqualTo(original.id()).isNotEqualTo(secondFork.id());
		assertThat(HistoryStore.open(fork.historyFile(), true).entries()).containsExactly("select 1\nfrom t");
		assertThat(SessionMetadata.read(fork.metadataFile()).resumedFrom()).isEqualTo(original.id());
		assertThat(SessionMetadata.read(fork.metadataFile()).rowLimit()).isEqualTo(500);
		// The forks do not affect each other.
		HistoryStore.open(fork.historyFile(), true).add("select 2");
		assertThat(HistoryStore.open(secondFork.historyFile(), true).entries()).containsExactly("select 1\nfrom t");
		assertThat(tree(original.path())).isEqualTo(before);
	}

	@Test
	void listShowsNewestFirstAtMostTheLimitWithoutTheCurrentSessionAndWithoutUnknownDirectories() throws Exception {
		var store = new SessionStore(base);
		Path sessions = base.resolve("sessions");
		Files.createDirectories(sessions.resolve("20260101T000000Z-00000001"));
		for (int i = 1; i <= 25; i++) {
			Path dir = Files.createDirectories(sessions.resolve("202611%02dT100000Z-0000000a".formatted(i)));
			metadata("select " + i).write(dir.resolve("session.yaml"));
		}
		var listed = store.list("20261125T100000Z-0000000a", 20);
		assertThat(listed).hasSize(20);
		assertThat(listed.getFirst().id()).isEqualTo("20261124T100000Z-0000000a");
		assertThat(listed.getFirst().metadata().lastStatement()).isEqualTo("select 24");
		assertThat(listed.getLast().id()).isEqualTo("20261105T100000Z-0000000a");
	}

	@Test
	void cleanupRemovesTheOldestBeyondTheCountButNeverTheCurrentUnknownOrForeignDirectories() throws Exception {
		Path sessions = base.resolve("sessions");
		var ids = List.of("20260101T000000Z-00000001", "20260102T000000Z-00000002", "20260103T000000Z-00000003",
			"20260104T000000Z-00000004");
		for (String id : ids) {
			metadata("x").write(Files.createDirectories(sessions.resolve(id)).resolve("session.yaml"));
		}
		Files.createDirectories(sessions.resolve("20250101T000000Z-0000000f")); // no session.yaml: unknown content
		Files.createDirectories(sessions.resolve("notes")); // not a Session id
		Path outside = Files.createDirectories(base.resolve("outside"));
		Files.writeString(outside.resolve("keep.txt"), "keep");
		Files.createSymbolicLink(sessions.resolve("20250202T000000Z-0000000e"), outside);

		new SessionStore(base).cleanup(2, ids.get(0));

		// keep = 2 counts the current Session (the oldest here, always kept) and the newest other one.
		assertThat(sessions.resolve(ids.get(0))).exists();
		assertThat(sessions.resolve(ids.get(1))).doesNotExist();
		assertThat(sessions.resolve(ids.get(2))).doesNotExist();
		assertThat(sessions.resolve(ids.get(3))).exists();
		assertThat(sessions.resolve("20250101T000000Z-0000000f")).exists();
		assertThat(sessions.resolve("notes")).exists();
		assertThat(outside.resolve("keep.txt")).exists();
	}

	private static final List<String> OLD_IDS = List.of("20260101T000000Z-00000001", "20260102T000000Z-00000002",
		"20260103T000000Z-00000003");

	private Path oldSession(String id) throws Exception {
		Path dir = Files.createDirectories(base.resolve("sessions").resolve(id));
		metadata("x").write(dir.resolve("session.yaml"));
		return dir;
	}

	private SessionDirectory currentSession() throws Exception {
		var current = SessionDirectory.create(base);
		metadata("current").write(current.metadataFile());
		return current;
	}

	/** A Session directory that a Session of this JVM holds: created by the same code that a running Session uses. */
	private SessionDirectory liveSession() throws Exception {
		var live = SessionDirectory.create(base);
		metadata("live").write(live.metadataFile());
		return live;
	}

	@Test
	void cleanupKeepsALiveSessionOfThisProcessEvenWhenTheCountIsExceededAndWhenCleanupRunsAgain() throws Exception {
		var live = liveSession();
		for (String id : OLD_IDS) {
			oldSession(id);
		}
		var current = currentSession();
		metadata("current").write(current.metadataFile());
		var store = new SessionStore(base);

		store.cleanup(1, current.id());
		store.cleanup(1, current.id()); // a probe that closes its own channel must not drop the lock of the live Session

		assertThat(live.path()).exists();
		assertThat(live.path().resolve("session.yaml")).exists();
		for (String id : OLD_IDS) {
			assertThat(base.resolve("sessions").resolve(id)).doesNotExist();
		}
		assertThat(current.path()).exists();
		// The lock still holds: a third Session of another process could not take it.
		assertThat(SessionDirectory.isLocked(live.path())).isTrue();
	}

	@Test
	void cleanupRemovesASessionWithAStaleLockFileThatNobodyHolds() throws Exception {
		Path stale = oldSession(OLD_IDS.get(0));
		Files.createFile(stale.resolve("session.lock")); // left by a crashed process: the OS released its lock
		oldSession(OLD_IDS.get(1));
		var current = currentSession();

		new SessionStore(base).cleanup(1, current.id());

		assertThat(stale).doesNotExist();
		assertThat(base.resolve("sessions").resolve(OLD_IDS.get(1))).doesNotExist();
		assertThat(current.path()).exists();
	}

	@Test
	void aSessionBecomesRemovableWhenItsDirectoryIsReleased() throws Exception {
		var ended = liveSession();
		var current = currentSession();
		var store = new SessionStore(base);

		store.cleanup(1, current.id());
		assertThat(ended.path()).exists();

		ended.release();
		ended.release(); // idempotent
		store.cleanup(1, current.id());
		assertThat(ended.path()).doesNotExist();
	}

	@Test
	void liveSessionsDoNotCountAgainstTheRetentionCountOfInactiveSessions() throws Exception {
		var live = liveSession();
		for (String id : OLD_IDS) {
			oldSession(id);
		}
		var current = currentSession();

		new SessionStore(base).cleanup(3, current.id()); // current + the two newest inactive

		assertThat(live.path()).exists();
		assertThat(base.resolve("sessions").resolve(OLD_IDS.get(0))).doesNotExist();
		assertThat(base.resolve("sessions").resolve(OLD_IDS.get(1))).exists();
		assertThat(base.resolve("sessions").resolve(OLD_IDS.get(2))).exists();
	}

	@Test
	void aSessionHeldByAnotherProcessIsKeptAndRemovedAfterThatProcessEnds() throws Exception {
		Path held = oldSession(OLD_IDS.get(0));
		Path holder = base.resolve("LockHolder.java");
		Files.writeString(holder, """
			import java.nio.channels.FileChannel;
			import java.nio.file.*;
			public class LockHolder {
				public static void main(String[] args) throws Exception {
					var channel = FileChannel.open(Path.of(args[0]), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
					if (channel.tryLock() == null) { System.exit(2); }
					System.out.println("locked");
					System.out.flush();
					Thread.sleep(600_000);
				}
			}
			""");
		String java = ProcessHandle.current().info().command().orElseThrow();
		Process process = new ProcessBuilder(java, holder.toString(), held.resolve("session.lock").toString())
			.redirectErrorStream(true).start();
		try {
			var out = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
			String line;
			do {
				line = out.readLine();
			} while (line != null && !line.equals("locked"));
			assertThat(line).as("the child process holds the lock").isEqualTo("locked");
			oldSession(OLD_IDS.get(1));
			var current = currentSession();

			new SessionStore(base).cleanup(1, current.id());
			assertThat(held).exists();
			assertThat(base.resolve("sessions").resolve(OLD_IDS.get(1))).doesNotExist();

			process.destroyForcibly(); // a crash: the OS releases the lock
			process.waitFor();
			new SessionStore(base).cleanup(1, current.id());
			assertThat(held).doesNotExist();
		}
		finally {
			process.destroyForcibly();
		}
	}

	@Test
	void anUnwritableStateDirectoryGivesADisabledDirectoryWithAReasonInsteadOfAnException() throws Exception {
		Path file = Files.writeString(base.resolve("not-a-directory"), "x");
		var directory = SessionDirectory.createOrDisabled(file.resolve("state"));
		assertThat(directory.disabledReason()).isNotBlank();
		assertThat(directory.logFile()).isNull();
		assertThat(directory.historyFile()).isNull();
		assertThat(SessionDirectory.createOrDisabled(base).disabledReason()).isNull();
	}
}
