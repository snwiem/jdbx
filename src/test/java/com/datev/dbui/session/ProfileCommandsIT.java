package com.datev.dbui.session;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import com.datev.dbui.config.*;

@Testcontainers
class ProfileCommandsIT {
	@Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17").withPassword("profile-private-secret");
	@TempDir Path directory;
	private ConnectionParameters parameters() {
		return new ConnectionParameters(POSTGRES.getJdbcUrl(), Map.of("user", POSTGRES.getUsername(), "password", POSTGRES.getPassword()), false);
	}
	private Session session(Connection connection) throws Exception { return session(connection, Map.of()); }
	private Session session(Connection connection, Map<String, String> environment) throws Exception {
		var context = new SessionContext(directory.resolve("state"), Settings.defaults(), parameters(), null, new ConfigLoader(directory, environment)::profile);
		var session = new Session(connection, SessionDirectory.create(context.stateBase()), context);
		session.configureProfiles(directory, environment);
		return session;
	}
	private static Session.Update submit(Session session, String text) throws Exception { return session.submit(text).get(10, TimeUnit.SECONDS); }
	private Profile profile(String name, boolean readOnly) {
		return new Profile(name, POSTGRES.getJdbcUrl(), null, null, null, POSTGRES.getUsername(), POSTGRES.getPassword(), readOnly,
			"public", Map.of("readOnlyMode", "always"));
	}

	@Test
	void failedDialogConnectionsKeepTheEnteredValuesForCorrection() throws Exception {
		var stateBase = directory.resolve("failed-startup");
		try (Session current = new Session(SessionDirectory.create(stateBase), Settings.defaults())) {
			current.configureProfiles(directory, Map.of());
			var model = new ProfileDialogModel(new Profile(null, "jdbc:postgresql://127.0.0.1:1/dbui?connectTimeout=1", null, null, null,
				POSTGRES.getUsername(), POSTGRES.getPassword(), null, null, Map.of()), null);
			current.openConnectionDialog(model, stateBase);
			assertThat(current.saveDialog(false).get(10, TimeUnit.SECONDS).messageBlocks().getLast().kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(current.interaction()).isInstanceOf(SessionInteraction.Dialog.class);
			assertThat(((SessionInteraction.Dialog) current.interaction()).model()).isSameAs(model);
			model.set(ProfileDialogModel.Field.URL, POSTGRES.getJdbcUrl());
			assertThat(current.saveDialog(false).get(10, TimeUnit.SECONDS).messageBlocks().getLast().text()).contains("Connected to");
		}
	}

	@Test
	void unchangedProfileEditsKeepPasswordReferencesInsideSavedUrls() throws Exception {
		java.nio.file.Files.writeString(directory.resolve("profiles.yaml"), "profiles:\n  url:\n    url: '" + POSTGRES.getJdbcUrl() + "&user=" + POSTGRES.getUsername() + "&password=${URL_PASSWORD}'\n    properties:\n      sslpassword: ${TLS_PASSWORD}\n");
		try (Session current = session(parameters().open(), Map.of("URL_PASSWORD", POSTGRES.getPassword(), "TLS_PASSWORD", "tls-secret-not-for-files"))) {
			submit(current, "/profile edit url");
			assertThat(current.interaction()).isInstanceOf(SessionInteraction.Dialog.class);
			assertThat(((SessionInteraction.Dialog) current.interaction()).model().value(ProfileDialogModel.Field.PROPERTIES)).contains("${TLS_PASSWORD}").doesNotContain("tls-secret-not-for-files");
			current.saveDialog(true).get(10, TimeUnit.SECONDS);
			assertThat(java.nio.file.Files.readString(directory.resolve("profiles.yaml"))).contains("${URL_PASSWORD}", "${TLS_PASSWORD}")
				.doesNotContain(POSTGRES.getPassword(), "tls-secret-not-for-files");
		}
	}

	@Test
	void existingEnvironmentReferencesInTypedProfileFieldsWorkForConnectListAndEdit() throws Exception {
		java.nio.file.Files.writeString(directory.resolve("profiles.yaml"), "profiles:\n  env:\n    host: ${PG_HOST}\n    port: ${PG_PORT}\n    database: " + POSTGRES.getDatabaseName() + "\n    user: " + POSTGRES.getUsername() + "\n    password: ${MISSING_PASSWORD}\n    read-only: ${PG_READ_ONLY}\n");
		var environment = Map.of("PG_HOST", POSTGRES.getHost(), "PG_PORT", POSTGRES.getMappedPort(5432).toString(), "PG_READ_ONLY", "true");
		try (Session current = session(parameters().open(), environment)) {
			assertThat(submit(current, "/profile list").messageBlocks().getLast().text()).contains("env", POSTGRES.getHost());
			submit(current, "/connect env");
			assertThat(current.interaction()).isInstanceOf(SessionInteraction.Password.class);
			current.answerPassword(POSTGRES.getPassword()).get(10, TimeUnit.SECONDS);
			assertThat(submit(current, "select 12").messageBlocks().getLast().result().rows()).containsExactly(List.of(12));
			submit(current, "/profile edit env");
			var model = ((SessionInteraction.Dialog) current.interaction()).model();
			assertThat(model.value(ProfileDialogModel.Field.PORT)).isEqualTo(POSTGRES.getMappedPort(5432).toString());
			assertThat(model.value(ProfileDialogModel.Field.PASSWORD)).isEqualTo("${MISSING_PASSWORD}");
			assertThat(model.value(ProfileDialogModel.Field.READ_ONLY)).isEqualTo("true");
			current.cancelInteraction();
			submit(current, "/profile remove env");
			assertThat(current.interaction()).isInstanceOf(SessionInteraction.Confirmation.class);
			submit(current, "y");
			assertThat(new ProfileStore(directory).names()).isEmpty();
		}
	}

	@Test
	void disconnectedStartupConnectsFromTheDialogAndPromptsWithoutSavingPromptCredentials() throws Exception {
		var stateBase = directory.resolve("startup-state");
		try (Session session = new Session(SessionDirectory.create(stateBase), Settings.defaults())) {
			session.configureProfiles(directory, Map.of());
			var options = new ConnectionOptions(); options.url = POSTGRES.getJdbcUrl(); options.user = POSTGRES.getUsername();
			var model = ProfileDialogModel.prefill(options, null, null, Settings.defaults());
			session.openConnectionDialog(model, stateBase);
			model.set(ProfileDialogModel.Field.NAME, "startup");
			session.saveDialog(true).get(10, TimeUnit.SECONDS);
			assertThat(session.interaction()).isInstanceOf(SessionInteraction.Password.class);
			assertThat(new ProfileStore(directory).profile("startup").password()).isNull();
			session.answerPassword(POSTGRES.getPassword()).get(10, TimeUnit.SECONDS);
			assertThat(session.connectionLabel()).startsWith(POSTGRES.getUsername() + "@");
			assertThat(submit(session, "select 123").messageBlocks().getLast().result().rows()).containsExactly(List.of(123));
			assertThat(session.history()).containsExactly("select 123");
		}
	}

	@Test
	void missingProfilePasswordUsesThePromptApiWithoutEchoOrPersistence() throws Exception {
		var given = profile("prompt", false);
		new ProfileStore(directory).save(new Profile(given.name(), given.url(), null, null, null, given.user(), "${MISSING_SECRET}", false, null, Map.of()));
		try (Session session = session(parameters().open())) {
			assertThat(submit(session, "/connect prompt").messageBlocks().getLast().kind()).isNotEqualTo(MessageBlock.Kind.ERROR);
			assertThat(session.interaction()).isInstanceOf(SessionInteraction.Password.class);
			assertThat(session.answerPassword(POSTGRES.getPassword()).get(10, TimeUnit.SECONDS).messageBlocks().getLast().text()).contains("Connected to");
			assertThat(session.interaction()).isNull();
			assertThat(submit(session, "select 42").messageBlocks().getLast().result().rows()).containsExactly(List.of(42));
			assertThat(session.history()).doesNotContain(POSTGRES.getPassword());
			assertThat(session.messageBlocks().toString()).doesNotContain(POSTGRES.getPassword());
			assertThat(java.nio.file.Files.readString(session.directory().metadataFile())).doesNotContain(POSTGRES.getPassword());
			assertThat(new ProfileStore(directory).profile("prompt").password()).isEqualTo("${MISSING_SECRET}");
		}
	}

	@Test
	void profileDialogsSaveEditsAndRemovalRequiresConfirmation() throws Exception {
		try (Session session = session(parameters().open())) {
			submit(session, "/profile add");
			var dialog = (SessionInteraction.Dialog) session.interaction();
			var model = dialog.model();
			model.set(ProfileDialogModel.Field.NAME, "saved");
			model.set(ProfileDialogModel.Field.URL, POSTGRES.getJdbcUrl());
			model.set(ProfileDialogModel.Field.USER, POSTGRES.getUsername());
			model.set(ProfileDialogModel.Field.PASSWORD, "${MY_PASSWORD}");
			assertThat(session.saveDialog(true).get(10, TimeUnit.SECONDS).messageBlocks().getLast().text()).contains("Saved Profile");
			assertThat(new ProfileStore(directory).profile("saved").password()).isEqualTo("${MY_PASSWORD}");
			submit(session, "/profile edit saved");
			var edit = (SessionInteraction.Dialog) session.interaction();
			assertThat(edit.model().value(ProfileDialogModel.Field.PASSWORD)).isEqualTo("${MY_PASSWORD}");
			edit.model().set(ProfileDialogModel.Field.SCHEMA, "app");
			session.saveDialog(true).get(10, TimeUnit.SECONDS);
			assertThat(new ProfileStore(directory).profile("saved").schema()).isEqualTo("app");
			submit(session, "/profile remove saved");
			assertThat(new ProfileStore(directory).names()).contains("saved");
			submit(session, "n");
			assertThat(new ProfileStore(directory).names()).contains("saved");
			submit(session, "/profile remove saved");
			submit(session, "y");
			assertThat(new ProfileStore(directory).names()).isEmpty();
		}
	}

	@Test
	void connectListsProfilesAndSwitchesConnectionLabelSchemaCacheAndExecutionControls() throws Exception {
		new ProfileStore(directory).save(profile("private", true));
		Connection old = parameters().open();
		try (Session session = session(old)) {
			submit(session, "/limit 2");
			submit(session, "/timeout 1");
			assertThat(submit(session, "/profile list").messageBlocks().getLast().text()).contains("private", POSTGRES.getUsername()).doesNotContain(POSTGRES.getPassword());
			submit(session, "/tables public");
			submit(session, "create table profile_cache_rebound(id int)");
			assertThat(submit(session, "/tables public").messageBlocks().getLast().result().rows()).extracting(row -> row.getFirst()).doesNotContain("profile_cache_rebound");
			assertThat(submit(session, "/connect").messageBlocks().getLast().text()).contains("/connect <name|number>", "1");
			assertThat(submit(session, "/connect 1").messageBlocks().getLast().kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(old.isClosed()).isTrue();
			assertThat(session.connectionLabel()).startsWith(POSTGRES.getUsername() + "@");
			assertThat(submit(session, "/tables public").messageBlocks().getLast().result().rows()).extracting(row -> row.getFirst()).contains("profile_cache_rebound");
			assertThat(submit(session, "show default_transaction_read_only").messageBlocks().getLast().result().rows()).containsExactly(List.of("on"));
			var limited = submit(session, "select generate_series(1,5)").messageBlocks();
			assertThat(limited.get(limited.size() - 2).result().rows()).hasSize(2);
			assertThat(session.submit("select pg_sleep(10)").get(4, TimeUnit.SECONDS).messageBlocks().getLast().text()).contains("57014");
		}
	}
}
