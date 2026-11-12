package com.datev.dbui.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.datev.dbui.completion.Candidate;
import com.datev.dbui.config.ConnectionParameters;
import com.datev.dbui.config.Settings;

/** Cross-feature behavior through the Session's public input, Completion and settings interfaces. */
@Testcontainers
class FeatureIntegrationIT {
	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17").withPassword("integration-private-password");
	@TempDir Path directory;

	private ConnectionParameters parameters() {
		return new ConnectionParameters(POSTGRES.getJdbcUrl(),
			Map.of("user", POSTGRES.getUsername(), "password", POSTGRES.getPassword()), false);
	}

	private Session session() throws Exception {
		var parameters = parameters();
		var context = new SessionContext(directory.resolve("state"), Settings.defaults(), parameters, null,
			new com.datev.dbui.config.ConfigLoader(directory, Map.of())::profile);
		var session = new Session(parameters.open(), SessionDirectory.create(context.stateBase()), context);
		session.configureProfiles(directory, Map.of());
		return session;
	}

	private static MessageBlock last(Session session, String text) throws Exception {
		return session.submit(text).get(10, TimeUnit.SECONDS).messageBlocks().getLast();
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = {
		"/connect dev password=inline-private-secret", "/profile add --password inline-private-secret",
		"/connect dev pass=inline-private-secret", "/connect dev PGPASSWORD=inline-private-secret",
		"/connect jdbc:postgresql://host/db?password=inline-private-secret" })
	void inlineProfileCredentialsNeverEnterHistoryOrTranscript(String input) throws Exception {
		try (Session current = session()) {
			assertThat(last(current, input).kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(current.history()).doesNotContain(input);
			assertThat(current.messageBlocks().toString()).doesNotContain("inline-private-secret");
			assertThat(java.nio.file.Files.readString(current.directory().metadataFile())).doesNotContain("inline-private-secret");
		}
	}

	@Test
	void disconnectedStartupStillSupportsSlashCompletionAndReportsConnectionErrors() throws Exception {
		try (Session current = new Session(SessionDirectory.create(directory.resolve("empty")), Settings.defaults())) {
			current.configureProfiles(directory, Map.of());
			assertThat(current.status().connected()).isFalse();
			assertThat(current.complete("select * from ", 14).get(10, TimeUnit.SECONDS).candidates()).isEmpty();
			assertThat(current.complete("/for", 4).get(10, TimeUnit.SECONDS).candidates())
				.extracting(Candidate::insert).containsExactly("/format");
			assertThat(last(current, "/tables").text()).contains("Not connected");
			assertThat(last(current, "/readonly on").text()).contains("Not connected");
		}
	}

	@Test
	void promptedConnectRecoversFromFailedReconnectAndRetainsCredentialsOnlyInMemory() throws Exception {
		var unreachable = new ConnectionParameters("jdbc:postgresql://127.0.0.1:1/unreachable?connectTimeout=1",
			Map.of("user", POSTGRES.getUsername(), "password", POSTGRES.getPassword()), false);
		var context = new SessionContext(directory.resolve("state"), Settings.defaults(), unreachable, null, name -> null);
		new com.datev.dbui.config.ProfileStore(directory).save(new com.datev.dbui.config.Profile("prompted",
			parameters().url(), null, null, null, POSTGRES.getUsername(), null, true, "public", Map.of()));
		try (Session current = new Session(parameters().open(), SessionDirectory.create(context.stateBase()), context);
				var admin = parameters().open(); var sql = admin.createStatement()) {
			current.configureProfiles(directory, Map.of());
			Object pid = last(current, "select pg_backend_pid()").result().rows().getFirst().getFirst();
			sql.execute("select pg_terminate_backend(" + pid + ")");
			assertThat(last(current, "select 1").text()).contains("Connection lost");
			assertThat(last(current, "select 2").text()).contains("Cannot reconnect");
			assertThat(current.status().connected()).isFalse();
			last(current, "/connect prompted");
			assertThat(current.interaction()).isInstanceOf(SessionInteraction.Password.class);
			assertThat(current.answerPassword("incorrect").get(10, TimeUnit.SECONDS).messageBlocks().getLast().kind())
				.isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(current.interaction()).isInstanceOf(SessionInteraction.Password.class);
			current.answerPassword(POSTGRES.getPassword()).get(10, TimeUnit.SECONDS);
			assertThat(current.status().connected()).isTrue();
			assertThat(current.settings().readOnly()).isTrue();
			pid = last(current, "select pg_backend_pid()").result().rows().getFirst().getFirst();
			sql.execute("select pg_terminate_backend(" + pid + ")");
			assertThat(last(current, "select 3").text()).contains("Connection lost");
			assertThat(last(current, "select 4").result().rows()).containsExactly(java.util.List.of(4));
			assertThat(current.messageBlocks().toString()).doesNotContain(POSTGRES.getPassword(), "incorrect");
			assertThat(java.nio.file.Files.readString(current.directory().metadataFile())).doesNotContain(POSTGRES.getPassword());
			assertThat(java.nio.file.Files.readString(current.directory().historyFile())).doesNotContain(POSTGRES.getPassword());
			assertThat(new com.datev.dbui.config.ProfileStore(directory).profile("prompted").password()).isNull();
		}
	}

	@Test
	void resumeCanPromptForAnotherTargetAfterReconnectFails() throws Exception {
		String earlier;
		try (Session first = session()) {
			last(first, "/format csv");
			last(first, "/readonly on");
			earlier = first.directory().id();
		}
		var unreachable = new ConnectionParameters("jdbc:postgresql://127.0.0.1:1/unreachable?connectTimeout=1",
			Map.of("user", "other", "password", "unavailable"), false);
		var context = new SessionContext(directory.resolve("state"), Settings.defaults(), unreachable, null, name -> null);
		try (Session current = new Session(parameters().open(), SessionDirectory.create(context.stateBase()), context);
				var admin = parameters().open(); var sql = admin.createStatement()) {
			Object pid = last(current, "select pg_backend_pid()").result().rows().getFirst().getFirst();
			sql.execute("select pg_terminate_backend(" + pid + ")");
			last(current, "select 1");
			assertThat(last(current, "select 2").text()).contains("Cannot reconnect");
			last(current, "/resume " + earlier);
			assertThat(current.interaction()).isInstanceOf(SessionInteraction.Password.class);
			current.answerPassword(POSTGRES.getPassword()).get(10, TimeUnit.SECONDS);
			assertThat(current.status().connected()).isTrue();
			assertThat(current.settings().readOnly()).isTrue();
			assertThat(current.settings().format()).isEqualTo("csv");
			assertThat(last(current, "select 9 as id").text()).isEqualTo("id\n9\n");
			assertThat(SessionMetadata.read(current.directory().metadataFile()).resumedFrom()).isEqualTo(earlier);
		}
	}

	@Test
	void readonlyOnCanReconnectWhenTheServerNowRequiresReadOnly() throws Exception {
		var properties = new java.util.LinkedHashMap<>(parameters().properties());
		properties.put("options", "-c default_transaction_read_only=on");
		var reconnectWith = new ConnectionParameters(parameters().url(), properties, false);
		var context = new SessionContext(directory.resolve("failover"), Settings.defaults(), reconnectWith, null, name -> null);
		try (Session current = new Session(parameters().open(), SessionDirectory.create(context.stateBase()), context);
				var admin = parameters().open(); var sql = admin.createStatement()) {
			Object pid = last(current, "select pg_backend_pid()").result().rows().getFirst().getFirst();
			sql.execute("select pg_terminate_backend(" + pid + ")");
			last(current, "select 1");
			assertThat(last(current, "/readonly on").text()).isEqualTo("read-only");
			assertThat(current.status().connected()).isTrue();
			assertThat(current.settings().readOnly()).isTrue();
			assertThat(last(current, "show transaction_read_only").result().rows()).containsExactly(java.util.List.of("on"));
			pid = last(current, "select pg_backend_pid()").result().rows().getFirst().getFirst();
			sql.execute("select pg_terminate_backend(" + pid + ")");
			last(current, "select 2");
			assertThat(last(current, "/readonly off").kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(current.status().connected()).isFalse();
			assertThat(current.settings().readOnly()).isTrue();
		}
	}

	@Test
	void resumeChecksServerModeEvenWhenItKeepsTheCurrentConnection() throws Exception {
		String earlier;
		try (Session first = session()) { earlier = first.directory().id(); }
		var properties = new java.util.LinkedHashMap<>(parameters().properties());
		properties.put("options", "-c default_transaction_read_only=on");
		var serverReadOnly = new ConnectionParameters(parameters().url(), properties, false);
		var context = new SessionContext(directory.resolve("state"), Settings.defaults(), serverReadOnly, null, name -> null);
		try (Session current = new Session(serverReadOnly.open(), SessionDirectory.create(context.stateBase()), context)) {
			String before = current.directory().id();
			assertThat(last(current, "/resume " + earlier).kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(current.directory().id()).isEqualTo(before);
		}
	}

	@Test
	void startupChecksServerReadOnlyThroughTheSameConnectionOpeningPath() throws Exception {
		var properties = new java.util.LinkedHashMap<>(parameters().properties());
		properties.put("options", "-c default_transaction_read_only=on");
		var parameters = new ConnectionParameters(parameters().url(), properties, false);
		var context = new SessionContext(directory.resolve("startup"), Settings.defaults(), parameters, null, name -> null);
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> Session.connect(SessionDirectory.create(context.stateBase()), context))
			.isInstanceOf(java.sql.SQLException.class).hasMessageContaining("server is read-only");
		var readOnly = new SessionContext(context.stateBase(), Settings.defaults().withExecution(1000, null, true), parameters, null, name -> null);
		try (Session current = Session.connect(SessionDirectory.create(context.stateBase()), readOnly)) {
			assertThat(current.status().readOnly()).isTrue();
			assertThat(last(current, "show transaction_read_only").result().rows()).containsExactly(java.util.List.of("on"));
		}
	}

	@Test
	void anUnsetProfileKeepsReadWriteEvenWhenTheGlobalDefaultIsReadOnly() throws Exception {
		java.nio.file.Files.writeString(directory.resolve("config.yaml"), "read-only: true\n");
		new com.datev.dbui.config.ProfileStore(directory).save(new com.datev.dbui.config.Profile("unset",
			parameters().url(), null, null, null, POSTGRES.getUsername(), POSTGRES.getPassword(), null, null, Map.of()));
		try (Session current = session()) {
			last(current, "/readonly off");
			last(current, "/connect unset");
			assertThat(current.settings().readOnly()).isFalse();
			assertThat(last(current, "show transaction_read_only").result().rows()).containsExactly(java.util.List.of("off"));
		}
	}

	@Test
	void credentialWordsInProfileNamesRemainSelectableEditableAndRemovable() throws Exception {
		new com.datev.dbui.config.ProfileStore(directory).save(new com.datev.dbui.config.Profile("api-token",
			parameters().url(), null, null, null, POSTGRES.getUsername(), POSTGRES.getPassword(), null, null, Map.of()));
		try (Session current = session()) {
			assertThat(last(current, "/connect api-token").text()).contains("Connected to");
			last(current, "/profile edit api-token");
			assertThat(current.interaction()).isInstanceOf(SessionInteraction.Dialog.class);
			current.cancelInteraction();
			last(current, "/profile remove api-token");
			assertThat(current.interaction()).isInstanceOf(SessionInteraction.Confirmation.class);
			last(current, "y");
			assertThat(new com.datev.dbui.config.ProfileStore(directory).names()).isEmpty();
		}
	}

	@Test
	void profileRegistryHelpAndCandidatesFollowTheCurrentProfileFile() throws Exception {
		try (Session current = session()) {
			assertThat(last(current, "/help").text()).contains("/readonly", "/connect", "/profile", "Tab / Alt-/", "Dialog Tab / Shift-Tab");
			assertThat(current.slashCommands().parse("/connect").background()).isTrue();
			assertThat(current.slashCommands().parse("/profile list").background()).isTrue();
			assertThat(current.slashCommands().completionCandidates("profile")).containsExactly("list", "add", "edit", "remove");
			new com.datev.dbui.config.ProfileStore(directory).save(new com.datev.dbui.config.Profile("newly-saved",
				parameters().url(), null, null, null, POSTGRES.getUsername(), POSTGRES.getPassword(), null, null, Map.of()));
			assertThat(current.complete("/connect new", 12).get(10, TimeUnit.SECONDS).candidates())
				.extracting(Candidate::insert).containsExactly("newly-saved");
		}
	}

	@Test
	void schemaCommandArgumentsCompleteTablesAndSchemasFromTheSession() throws Exception {
		try (var admin = parameters().open(); var sql = admin.createStatement()) {
			sql.execute("create schema describe_sch; create table describe_sch.dc_target(id int); create table dc_public(id int)");
		}
		try (Session current = session()) {
			assertThat(current.complete("/describe dc_p", 14).get(10, TimeUnit.SECONDS).candidates())
				.extracting(Candidate::insert).containsExactly("dc_public");
			assertThat(current.complete("/describe describe_sch.dc", 25).get(10, TimeUnit.SECONDS).candidates())
				.extracting(Candidate::insert).containsExactly("dc_target");
			assertThat(current.complete("/tables describe_", 17).get(10, TimeUnit.SECONDS).candidates())
				.extracting(Candidate::insert).containsExactly("describe_sch");
		}
	}

	@Test
	void connectWithoutAProfileModeKeepsCurrentReadOnlyAndRebindsSchemaCompletion() throws Exception {
		try (var admin = parameters().open(); var sql = admin.createStatement()) {
			sql.execute("create schema switched; create table switched.connection_target(id int)");
		}
		new com.datev.dbui.config.ProfileStore(directory).save(new com.datev.dbui.config.Profile("switched",
			parameters().url(), null, null, null, POSTGRES.getUsername(), POSTGRES.getPassword(), null, "switched", Map.of()));
		try (Session current = session()) {
			current.complete("select * from ", 14).get(10, TimeUnit.SECONDS);
			last(current, "/readonly on");
			last(current, "/format markdown");
			last(current, "/orientation vertical");
			last(current, "/connect switched");
			assertThat(current.settings().readOnly()).isTrue();
			assertThat(last(current, "show search_path").result().rows()).containsExactly(java.util.List.of("switched"));
			assertThat(current.complete("select * from connection_", 25).get(10, TimeUnit.SECONDS).candidates())
				.extracting(Candidate::insert).containsExactly("connection_target");
			assertThat(last(current, "insert into connection_target values (1)").text()).contains("read-only");
			assertThat(current.settings().format()).isEqualTo("markdown");
			assertThat(current.settings().orientation()).isEqualTo("vertical");
			assertThat(SessionMetadata.read(current.directory().metadataFile()).connection().profile()).isEqualTo("switched");
		}
	}

	@Test
	void connectReadOnlyCanBeChangedAndResumeRestoresTheEarlierSettings() throws Exception {
		var parameters = parameters();
		new com.datev.dbui.config.ProfileStore(directory).save(new com.datev.dbui.config.Profile("protected",
			parameters.url(), null, null, null, POSTGRES.getUsername(), POSTGRES.getPassword(), true, "public", Map.of()));
		String earlier;
		try (Session first = session()) {
			last(first, "create table protected_target(id int)");
			last(first, "/format json");
			last(first, "/orientation vertical");
			last(first, "/limit 2");
			last(first, "/timeout 1");
			last(first, "/readonly on");
			earlier = first.directory().id();
		}
		try (Session current = session()) {
			assertThat(last(current, "/connect protected").text()).contains("Connected to");
			assertThat(last(current, "insert into protected_target values (1)").text()).contains("read-only");
			assertThat(last(current, "/readonly off").text()).isEqualTo("read-write");
			assertThat(last(current, "insert into protected_target values (2)").text()).contains("INSERT 0 1");
			assertThat(last(current, "/resume " + earlier).text()).contains("Resumed Session");
			assertThat(current.settings().readOnly()).isTrue();
			assertThat(current.settings().format()).isEqualTo("json");
			assertThat(current.settings().orientation()).isEqualTo("vertical");
			assertThat(current.settings().rowLimit()).isEqualTo(2);
			assertThat(current.settings().timeout()).isEqualTo(java.time.Duration.ofSeconds(1));
			assertThat(last(current, "insert into protected_target values (3)").text()).contains("read-only");
			assertThat(current.history()).doesNotContain("/resume " + earlier, "/quit");
		}
	}

	@Test
	void reconnectKeepsOutputAndReadOnlyAndRebuildsTheCompletionCache() throws Exception {
		try (var admin = parameters().open(); var sql = admin.createStatement()) {
			sql.execute("create table before_reconnect(id int)");
			try (Session session = session()) {
				last(session, "/format csv");
				last(session, "/orientation vertical");
				last(session, "/readonly on");
				assertThat(session.complete("select * from before_", 21).get(10, TimeUnit.SECONDS).candidates())
					.extracting(Candidate::insert).containsExactly("before_reconnect");
				sql.execute("create table after_reconnect(id int)");
				Object pid = last(session, "select pg_backend_pid()").result().rows().getFirst().getFirst();
				sql.execute("select pg_terminate_backend(" + pid + ")");
				assertThat(last(session, "select 1").text()).contains("Connection lost");
				assertThat(last(session, "select 2 as id").text()).isEqualTo("id,2\n");
				assertThat(session.settings().format()).isEqualTo("csv");
				assertThat(session.settings().orientation()).isEqualTo("vertical");
				assertThat(session.settings().readOnly()).isTrue();
				assertThat(last(session, "insert into before_reconnect values (1)").text()).contains("read-only");
				assertThat(session.complete("select * from after_", 20).get(10, TimeUnit.SECONDS).candidates())
					.extracting(Candidate::insert).containsExactly("after_reconnect");
			}
		}
	}

	@Test
	void completionUsesTheSessionsSlashCommandRegistryForNamesAndArguments() throws Exception {
		try (Session session = session()) {
			assertThat(session.complete("/for", 4).get(10, TimeUnit.SECONDS).candidates())
				.extracting(Candidate::insert).containsExactly("/format");
			assertThat(session.complete("/format ", 8).get(10, TimeUnit.SECONDS).candidates())
				.extracting(Candidate::insert).containsExactly("table", "markdown", "csv", "tsv", "json", "json-pretty");
		}
	}
}
