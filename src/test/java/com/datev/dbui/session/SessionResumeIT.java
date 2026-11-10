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
