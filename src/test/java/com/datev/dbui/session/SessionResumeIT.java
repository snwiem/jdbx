package com.datev.dbui.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.datev.dbui.config.ConnectionParameters;
import com.datev.dbui.config.Profile;
import com.datev.dbui.config.Settings;

/** Session seam: history, session.yaml and {@code /resume} against a real database, without a terminal. */
@Testcontainers
class SessionResumeIT {

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17").withPassword("s3cr3t-pw");

	@TempDir
	Path stateDir;

	private ConnectionParameters parameters() {
		return new ConnectionParameters(POSTGRES.getJdbcUrl(),
			Map.of("user", POSTGRES.getUsername(), "password", POSTGRES.getPassword()), false);
	}

	private Session session(Settings settings, String profile, ConnectionParameters parameters,
			Function<String, Profile> profiles) throws Exception {
		Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
		var context = new SessionContext(stateDir, settings, parameters, profile, profiles);
		return new Session(connection, SessionDirectory.create(stateDir), context);
	}

	private Session session() throws Exception {
		return session(Settings.defaults(), null, parameters(), name -> null);
	}

	private static Session.Update submit(Session session, String input) throws Exception {
		return session.submit(input).get(10, TimeUnit.SECONDS);
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

	private String earlierSession() throws Exception {
		try (Session first = session()) {
			submit(first, "select 1 as one");
			submit(first, " select 2 as hidden");
			submit(first, "select 3\n as three");
			return first.directory().id();
		}
	}

	@Test
	void historyAndMetadataAreWrittenWithoutThePassword() throws Exception {
		try (Session session = session()) {
			submit(session, "select 1 as one");
			submit(session, " select 2 as hidden");
			assertThat(session.history()).containsExactly("select 1 as one");
			assertThat(Files.readAllLines(session.directory().historyFile())).containsExactly("select 1 as one");
			var metadata = SessionMetadata.read(session.directory().metadataFile());
			assertThat(metadata.lastStatement()).isEqualTo(" select 2 as hidden".strip());
			assertThat(metadata.connection().user()).isEqualTo(POSTGRES.getUsername());
			assertThat(metadata.rowLimit()).isEqualTo(1000);
			for (Path file : List.of(session.directory().historyFile(), session.directory().metadataFile(), session.logFile())) {
				assertThat(Files.readString(file)).doesNotContain(POSTGRES.getPassword());
			}
		}
	}

	@Test
	void resumeListsEarlierSessionsAndForksTheChosenOne() throws Exception {
		String earlier = earlierSession();
		String before = tree(stateDir.resolve("sessions").resolve(earlier));
		try (Session session = session()) {
			var listed = submit(session, "/resume");
			var band = listed.messageBlocks().getLast();
			assertThat(band.kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(band.text()).contains("1", POSTGRES.getUsername() + "@", "select 3 as three", earlier)
				.doesNotContain("hidden");
			assertThat(session.history()).as("/resume is not saved").isEmpty();

			String originDirectory = session.directory().id();
			var resumed = submit(session, "/resume 1");
			assertThat(resumed.messageBlocks().getLast().kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(session.directory().id()).isNotEqualTo(earlier).isNotEqualTo(originDirectory);
			assertThat(session.history()).containsExactly("select 1 as one", "select 3\n as three");
			assertThat(SessionMetadata.read(session.directory().metadataFile()).resumedFrom()).isEqualTo(earlier);
			assertThat(session.logFile().getParent()).isEqualTo(session.directory().path());
			assertThat(submit(session, "select 7 as seven").messageBlocks().getLast().kind()).isEqualTo(MessageBlock.Kind.RESULT);
			assertThat(session.history()).endsWith("select 7 as seven");
		}
		assertThat(tree(stateDir.resolve("sessions").resolve(earlier))).isEqualTo(before);
	}

	@Test
	void successfulResumeDoesNotKeepThePreviousSessionsLastResultSummary() throws Exception {
		String earlier = earlierSession();
		try (Session current = session()) {
			submit(current, "select generate_series(1,1500)");
			assertThat(current.status().lastResult().truncated()).isTrue();
			submit(current, "/resume " + earlier);
			assertThat(current.status().lastResult()).isNull();
		}
	}

	@Test
	void resumeAlsoAcceptsTheSessionId() throws Exception {
		String earlier = earlierSession();
		try (Session session = session()) {
			submit(session, "/resume " + earlier);
			assertThat(SessionMetadata.read(session.directory().metadataFile()).resumedFrom()).isEqualTo(earlier);
			assertThat(submit(session, "/resume nope").messageBlocks().getLast().kind()).isEqualTo(MessageBlock.Kind.ERROR);
		}
	}

	@Test
	void twoInstancesResumeTheSameSessionWithoutAffectingEachOtherOrTheOriginal() throws Exception {
		String earlier = earlierSession();
		String before = tree(stateDir.resolve("sessions").resolve(earlier));
		try (Session one = session(); Session two = session()) {
			submit(one, "/resume " + earlier);
			submit(two, "/resume " + earlier);
			assertThat(one.directory().id()).isNotEqualTo(two.directory().id());
			submit(one, "select 11 as eleven");
			assertThat(two.history()).doesNotContain("select 11 as eleven");
			assertThat(HistoryStore.open(two.directory().historyFile(), true).entries()).doesNotContain("select 11 as eleven");
		}
		assertThat(tree(stateDir.resolve("sessions").resolve(earlier))).isEqualTo(before);
	}

	@Test
	void executionControlChangesArePersistedAndResumeRestoresTheirBehaviorAndHistory() throws Exception {
		String earlier;
		String sql = "select * from generate_series(1,10)";
		try (Session first = session()) {
			earlier = first.directory().id();
			submit(first, "/limit 3");
			assertThat(SessionMetadata.read(first.directory().metadataFile()).rowLimit()).isEqualTo(3);
			var blocks = submit(first, sql).messageBlocks();
			assertThat(blocks.get(blocks.size() - 2).result().rows()).containsExactly(List.of(1), List.of(2), List.of(3));
			assertThat(blocks.getLast().text()).isEqualTo("truncated at 3 rows");
			submit(first, "/timeout 7");
			assertThat(SessionMetadata.read(first.directory().metadataFile()).timeoutSeconds()).isEqualTo(7L);
			assertThat(submit(first, "/quit").ended()).isTrue();
		}
		String before = tree(stateDir.resolve("sessions").resolve(earlier));
		try (Session resumed = session()) {
			submit(resumed, "/resume 1");
			assertThat(resumed.settings().rowLimit()).isEqualTo(3);
			assertThat(resumed.settings().timeout()).isEqualTo(java.time.Duration.ofSeconds(7));
			assertThat(resumed.status().rowLimit()).isEqualTo(3);
			assertThat(resumed.history()).containsExactly("/limit 3", sql, "/timeout 7");
			var blocks = submit(resumed, sql).messageBlocks();
			assertThat(blocks.get(blocks.size() - 2).result().rows()).hasSize(3);
			assertThat(blocks.getLast().text()).isEqualTo("truncated at 3 rows");
			submit(resumed, "/timeout off");
			assertThat(resumed.settings().timeout()).isNull();
			assertThat(SessionMetadata.read(resumed.directory().metadataFile()).timeoutSeconds()).isNull();
		}
		assertThat(tree(stateDir.resolve("sessions").resolve(earlier))).isEqualTo(before);
	}

	@Test
	void resumeRestoresRowLimitTimeoutAndReadOnly() throws Exception {
		String earlier;
		try (Session first = session(Settings.defaults().withExecution(5, java.time.Duration.ofSeconds(9), true), null, parameters(), name -> null)) {
			earlier = first.directory().id();
		}
		try (Session session = session()) {
			submit(session, "/resume " + earlier);
			assertThat(session.settings().rowLimit()).isEqualTo(5);
			assertThat(session.settings().timeout()).isEqualTo(java.time.Duration.ofSeconds(9));
			assertThat(session.settings().readOnly()).isTrue();
		}
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.CsvSource({ "0,7", "3,-1", "3,2147483648" })
	void invalidStoredExecutionSettingsLeaveTheCurrentSessionUnchanged(int rowLimit, long timeout) throws Exception {
		String earlier;
		try (Session first = session()) {
			earlier = first.directory().id();
			var stored = SessionMetadata.read(first.directory().metadataFile());
			new SessionMetadata(stored.started(), stored.connection(), rowLimit, timeout, stored.readOnly(), null, null)
				.write(first.directory().metadataFile());
		}
		try (Session current = session()) {
			String directory = current.directory().id();
			var band = submit(current, "/resume " + earlier).messageBlocks().getLast();
			assertThat(band.kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(band.text()).contains("Invalid execution settings");
			assertThat(current.directory().id()).isEqualTo(directory);
			assertThat(current.settings().rowLimit()).isEqualTo(1000);
			assertThat(current.settings().timeout()).isNull();
			assertThat(submit(current, "select 42").messageBlocks().getLast().result().rows()).containsExactly(List.of(42));
		}
	}

	@Test
	void readOnlyResumeDuringATransactionFailsWithoutChangingSettingsOrForking() throws Exception {
		String earlier;
		try (Session first = session(Settings.defaults().withExecution(3, null, true), null, parameters(), name -> null)) {
			earlier = first.directory().id();
		}
		Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
		var context = new SessionContext(stateDir, Settings.defaults(), parameters(), null, name -> null);
		try (Session current = new Session(connection, SessionDirectory.create(stateDir), context)) {
			String directory = current.directory().id();
			submit(current, "begin");
			var band = submit(current, "/resume " + earlier).messageBlocks().getLast();
			assertThat(band.kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(band.text()).contains("read-only");
			assertThat(current.directory().id()).isEqualTo(directory);
			assertThat(current.settings().readOnly()).isFalse().isEqualTo(connection.isReadOnly());
			assertThat(SessionMetadata.read(current.directory().metadataFile()).readOnly()).isFalse();
			try (var directories = Files.list(stateDir.resolve("sessions"))) {
				assertThat(directories.count()).isEqualTo(2);
			}
			submit(current, "rollback");
			assertThat(submit(current, "/resume " + earlier).messageBlocks().getLast().kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(current.settings().readOnly()).isTrue().isEqualTo(connection.isReadOnly());
		}
	}

	@Test
	void aDifferentConnectionWithoutAResolvablePasswordGivesAnErrorAndChangesNothing() throws Exception {
		String earlier;
		var elsewhere = new ConnectionParameters("jdbc:postgresql://elsewhere.example:5432/other", Map.of("user", "carol", "password", "x"), false);
		try (Session first = session(Settings.defaults(), null, elsewhere, name -> null)) {
			earlier = first.directory().id();
		}
		try (Session session = session()) {
			String current = session.directory().id();
			var result = submit(session, "/resume " + earlier);
			var band = result.messageBlocks().getLast();
			assertThat(band.kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(band.text()).contains("carol@elsewhere.example:5432/other", "password", "issue 0013");
			assertThat(session.directory().id()).isEqualTo(current);
			assertThat(submit(session, "select 1").messageBlocks().getLast().kind()).isEqualTo(MessageBlock.Kind.RESULT);
		}
	}

	@Test
	void aProfileWithAResolvablePasswordReconnectsOnResume() throws Exception {
		String earlier;
		try (Session first = session(Settings.defaults(), "pg", parameters(), name -> null)) {
			earlier = first.directory().id();
		}
		var elsewhere = new ConnectionParameters("jdbc:postgresql://elsewhere.example:5432/other", Map.of("user", "carol", "password", "x"), false);
		var profile = new Profile("pg", POSTGRES.getJdbcUrl(), null, null, null, POSTGRES.getUsername(), POSTGRES.getPassword(), null,
			null, Map.of());
		try (Session session = session(Settings.defaults(), null, elsewhere, name -> name.equals("pg") ? profile : null)) {
			var result = submit(session, "/resume " + earlier);
			assertThat(result.messageBlocks().getLast().kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(submit(session, "select 1 as one").messageBlocks().getLast().kind()).isEqualTo(MessageBlock.Kind.RESULT);
			assertThat(session.connectionLabel()).startsWith(POSTGRES.getUsername() + "@");
		}
	}

	@Test
	void reconnectOnResumeRebindsSchemaCacheAndAppliesExecutionSettingsToTheNewConnection() throws Exception {
		String earlier;
		var restored = Settings.defaults().withExecution(2, java.time.Duration.ofSeconds(1), true);
		try (Session first = session(restored, "pg", parameters(), name -> null)) {
			earlier = first.directory().id();
		}
		var elsewhere = new ConnectionParameters("jdbc:postgresql://elsewhere.example:5432/other",
			Map.of("user", "carol", "password", "x"), false);
		// With readOnlyMode=always the server exposes the JDBC read-only flag even with autocommit on.
		var profile = new Profile("pg", POSTGRES.getJdbcUrl(), null, null, null, POSTGRES.getUsername(), POSTGRES.getPassword(),
			null, null, Map.of("readOnlyMode", "always"));
		try (Session current = session(Settings.defaults(), null, elsewhere, name -> profile)) {
			Object oldPid = submit(current, "select pg_backend_pid()").messageBlocks().getLast().result().rows().getFirst().getFirst();
			submit(current, "/tables public");
			submit(current, "create table resume_cache_rebound (id int)");
			assertThat(submit(current, "/tables public").messageBlocks().getLast().result().rows())
				.extracting(row -> row.getFirst()).doesNotContain("resume_cache_rebound");
			assertThat(submit(current, "/resume " + earlier).messageBlocks().getLast().kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(submit(current, "select pg_backend_pid()").messageBlocks().getLast().result().rows().getFirst().getFirst())
				.isNotEqualTo(oldPid);
			assertThat(submit(current, "/tables public").messageBlocks().getLast().result().rows())
				.extracting(row -> row.getFirst()).contains("resume_cache_rebound");
			assertThat(submit(current, "show default_transaction_read_only").messageBlocks().getLast().result().rows())
				.containsExactly(List.of("on"));
			var limited = submit(current, "select generate_series(1,5)").messageBlocks();
			assertThat(limited.get(limited.size() - 2).result().rows()).hasSize(2);
			assertThat(limited.getLast().text()).isEqualTo("truncated at 2 rows");
			var timedOut = current.submit("select pg_sleep(10)").get(4, TimeUnit.SECONDS).messageBlocks().getLast();
			assertThat(timedOut.kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(timedOut.text()).contains("57014");
		}
	}

	@Test
	void schemaAndExecutionSlashCommandsShareHistoryButQuitAndResumeAreExcluded() throws Exception {
		try (Session current = session()) {
			submit(current, "create table resume_command_history (id int)");
			for (String command : List.of("/limit 3", "/timeout 7", "/schema", "/tables", "/describe resume_command_history", "/refresh")) {
				assertThat(submit(current, command).messageBlocks().getLast().kind()).isNotEqualTo(MessageBlock.Kind.ERROR);
			}
			assertThat(submit(current, "/unknown").messageBlocks().getLast().text()).isEqualTo("Unknown Slash command: /unknown");
			assertThat(submit(current, "/resume").messageBlocks().getLast().kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(submit(current, "/quit").ended()).isTrue();
			assertThat(current.history()).containsExactly("create table resume_command_history (id int)", "/limit 3", "/timeout 7",
				"/schema", "/tables", "/describe resume_command_history", "/refresh", "/unknown");
		}
	}

	@Test
	void startingASessionRemovesTheOldestDirectoriesBeyondSessionsKeep() throws Exception {
		var settings = new Settings(1000, null, "table", "horizontal", false, "NULL", "⏎", 60, null, true, 2);
		for (int i = 0; i < 4; i++) {
			try (Session session = session(settings, null, parameters(), name -> null)) {
				assertThat(session.directory().path()).isDirectory();
			}
		}
		try (Stream<Path> remaining = Files.list(stateDir.resolve("sessions"))) {
			assertThat(remaining.count()).isEqualTo(2);
		}
	}

	@Test
	void anUnusableStateDirectoryDisablesHistoryAndLogWithAYellowBandButTheSessionRuns() throws Exception {
		Path file = Files.writeString(stateDir.resolve("blocked"), "x");
		Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
		var context = new SessionContext(file.resolve("state"), Settings.defaults(), parameters(), null, name -> null);
		try (Session session = new Session(connection, SessionDirectory.createOrDisabled(file.resolve("state")), context)) {
			var first = session.messageBlocks().getFirst();
			assertThat(first.kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(first.text()).startsWith("history and Session log are disabled: ");
			assertThat(submit(session, "select 1 as one").messageBlocks().getLast().kind()).isEqualTo(MessageBlock.Kind.RESULT);
			assertThat(session.history()).isEmpty();
			assertThat(submit(session, "/resume").messageBlocks().getLast().kind()).isEqualTo(MessageBlock.Kind.ERROR);
		}
	}

	@Test
	void historyCanBeSwitchedOff() throws Exception {
		var settings = new Settings(1000, null, "table", "horizontal", false, "NULL", "⏎", 60, null, false, 50);
		try (Session session = session(settings, null, parameters(), name -> null)) {
			submit(session, "select 1");
			assertThat(session.history()).isEmpty();
			assertThat(session.directory().historyFile()).doesNotExist();
			assertThat(SessionMetadata.read(session.directory().metadataFile()).lastStatement()).isEqualTo("select 1");
		}
	}
}
