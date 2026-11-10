package com.datev.dbui.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.datev.dbui.config.ConnectionParameters;

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

		// keep = 2 keeps the two newest; the current Session (the oldest here) is protected on top of that.
		assertThat(sessions.resolve(ids.get(0))).exists();
		assertThat(sessions.resolve(ids.get(1))).doesNotExist();
		assertThat(sessions.resolve(ids.get(2))).exists();
		assertThat(sessions.resolve(ids.get(3))).exists();
		assertThat(sessions.resolve("20250101T000000Z-0000000f")).exists();
		assertThat(sessions.resolve("notes")).exists();
		assertThat(outside.resolve("keep.txt")).exists();
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
