package com.datev.dbui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Seam 1: loading Settings and Profiles, ${ENV} resolution, validation and override order. */
class ConfigTest {

	@TempDir
	Path dir;

	private ConfigLoader loader(Map<String, String> env) {
		return new ConfigLoader(dir, env);
	}

	private void write(String file, String content) throws Exception {
		Files.writeString(dir.resolve(file), content);
	}

	// --- history and Sessions ---

	@Test
	void historyIsOnAndFiftySessionsAreKeptByDefault() {
		assertThat(loader(Map.of()).settings().historyEnabled()).isTrue();
		assertThat(loader(Map.of()).settings().sessionsKeep()).isEqualTo(50);
	}

	@Test
	void historyEnabledAndSessionsKeepComeFromNestedKeys() throws Exception {
		write("config.yaml", """
				history:
				  enabled: false
				sessions:
				  keep: 7
				""");
		var settings = loader(Map.of()).settings();
		assertThat(settings.historyEnabled()).isFalse();
		assertThat(settings.sessionsKeep()).isEqualTo(7);
	}

	@Test
	void invalidHistoryAndSessionsKeysGiveClearErrors() throws Exception {
		write("config.yaml", "history:\n  enabled: maybe\n");
		assertThatThrownBy(() -> loader(Map.of()).settings()).isInstanceOf(ConfigException.class)
			.hasMessageContaining("history.enabled");
		write("config.yaml", "history: off\n");
		assertThatThrownBy(() -> loader(Map.of()).settings()).isInstanceOf(ConfigException.class)
			.hasMessageContaining("history");
		write("config.yaml", "sessions:\n  keep: 0\n");
		assertThatThrownBy(() -> loader(Map.of()).settings()).isInstanceOf(ConfigException.class)
			.hasMessageContaining("sessions.keep");
		write("config.yaml", "sessions:\n  keep: many\n");
		assertThatThrownBy(() -> loader(Map.of()).settings()).isInstanceOf(ConfigException.class)
			.hasMessageContaining("sessions.keep");
	}

	// --- config directory ---

	@Test
	void configDirectoryOptionBeatsEnvironmentBeatsXdgBeatsHome() {
		Path home = Path.of("/home/u");
		assertThat(ConfigLoader.directory("/opt/o", Map.of("DBUI_CONFIG_DIR", "/e", "XDG_CONFIG_HOME", "/x"), home))
			.isEqualTo(Path.of("/opt/o"));
		assertThat(ConfigLoader.directory(null, Map.of("DBUI_CONFIG_DIR", "/e", "XDG_CONFIG_HOME", "/x"), home))
			.isEqualTo(Path.of("/e"));
		assertThat(ConfigLoader.directory(null, Map.of("XDG_CONFIG_HOME", "/x"), home))
			.isEqualTo(Path.of("/x/dbui"));
		assertThat(ConfigLoader.directory(null, Map.of(), home)).isEqualTo(Path.of("/home/u/.config/dbui"));
	}

	// --- Settings ---

	@Test
	void missingConfigFileGivesDefaults() {
		Settings settings = loader(Map.of()).settings();

		assertThat(settings.rowLimit()).isEqualTo(1000);
		assertThat(settings.timeout()).isNull();
		assertThat(settings.format()).isEqualTo("table");
		assertThat(settings.orientation()).isEqualTo("horizontal");
		assertThat(settings.readOnly()).isFalse();
	}

	@Test
	void configFileOverridesDefaultsKeyByKey() throws Exception {
		write("config.yaml", """
				# comment
				row-limit: 50
				timeout: 30
				format: csv
				orientation: vertical
				read-only: true
				key-bindings: {}
				""");

		Settings settings = loader(Map.of()).settings();

		assertThat(settings.rowLimit()).isEqualTo(50);
		assertThat(settings.timeout()).isEqualTo(Duration.ofSeconds(30));
		assertThat(settings.format()).isEqualTo("csv");
		assertThat(settings.orientation()).isEqualTo("vertical");
		assertThat(settings.readOnly()).isTrue();
	}

	@Test
	void formatOptionsHavePrdDefaults() {
		Settings settings = loader(Map.of()).settings();

		assertThat(settings.nullMarker()).isEqualTo("NULL");
		assertThat(settings.multilineMarker()).isEqualTo("⏎");
		assertThat(settings.maxWidth()).isEqualTo(60);
		assertThat(settings.timezone()).isNull();
	}

	@Test
	void formatOptionsComeFromConfigFile() throws Exception {
		write("config.yaml", """
				null-marker: "∅"
				multiline-marker: "<nl>"
				max-width: 20
				timezone: Europe/Berlin
				""");

		Settings settings = loader(Map.of()).settings();

		assertThat(settings.nullMarker()).isEqualTo("∅");
		assertThat(settings.multilineMarker()).isEqualTo("<nl>");
		assertThat(settings.maxWidth()).isEqualTo(20);
		assertThat(settings.timezone()).isEqualTo(java.time.ZoneId.of("Europe/Berlin"));
	}

	@Test
	void invalidFormatOptionsGiveClearErrors() throws Exception {
		write("config.yaml", "timezone: Mars/Base\n");
		assertThatThrownBy(() -> loader(Map.of()).settings()).isInstanceOf(ConfigException.class)
			.hasMessageContaining("config.yaml")
			.hasMessageContaining("timezone")
			.hasMessageContaining("Mars/Base");

		write("config.yaml", "max-width: wide\n");
		assertThatThrownBy(() -> loader(Map.of()).settings()).isInstanceOf(ConfigException.class)
			.hasMessageContaining("max-width");

		write("config.yaml", "max-width: -3\n");
		assertThatThrownBy(() -> loader(Map.of()).settings()).isInstanceOf(ConfigException.class)
			.hasMessageContaining("max-width");

		write("config.yaml", "null-marker: [a]\n");
		assertThatThrownBy(() -> loader(Map.of()).settings()).isInstanceOf(ConfigException.class)
			.hasMessageContaining("null-marker");
	}

	@Test
	void invalidSettingsGiveClearErrors() throws Exception {
		write("config.yaml", "format: pdf\n");
		assertThatThrownBy(() -> loader(Map.of()).settings()).isInstanceOf(ConfigException.class)
			.hasMessageContaining("config.yaml")
			.hasMessageContaining("format")
			.hasMessageContaining("pdf")
			.hasMessageContaining("table");

		write("config.yaml", "row-limit: lots\n");
		assertThatThrownBy(() -> loader(Map.of()).settings()).isInstanceOf(ConfigException.class)
			.hasMessageContaining("row-limit");
	}

	// --- Profiles ---

	@Test
	void loadsProfileWithAllFields() throws Exception {
		write("profiles.yaml", """
				profiles:
				  local:
				    host: db.example
				    port: 6543
				    database: app
				    user: alice
				    password: secret
				    read-only: true
				    schema: shop
				    properties:
				      sslmode: require
				      loginTimeout: 5
				""");

		Profile profile = loader(Map.of()).profile("local");

		assertThat(profile.name()).isEqualTo("local");
		assertThat(profile.host()).isEqualTo("db.example");
		assertThat(profile.port()).isEqualTo(6543);
		assertThat(profile.database()).isEqualTo("app");
		assertThat(profile.user()).isEqualTo("alice");
		assertThat(profile.password()).isEqualTo("secret");
		assertThat(profile.readOnly()).isTrue();
		assertThat(profile.schema()).isEqualTo("shop");
		assertThat(profile.properties()).containsEntry("sslmode", "require").containsEntry("loginTimeout", "5");
		assertThat(profile.url()).isNull();
	}

	@Test
	void loadsProfileWithUrl() throws Exception {
		write("profiles.yaml", """
				profiles:
				  u:
				    url: jdbc:postgresql://h:1/d
				""");

		Profile profile = loader(Map.of()).profile("u");

		assertThat(profile.url()).isEqualTo("jdbc:postgresql://h:1/d");
		assertThat(profile.readOnly()).isNull();
	}

	@Test
	void resolvesEnvReferencesInProfileValues() throws Exception {
		write("profiles.yaml", """
				profiles:
				  p:
				    host: h
				    password: ${DB_PASS}
				    user: "app_${STAGE}"
				""");

		Profile profile = loader(Map.of("DB_PASS", "from-env", "STAGE", "dev")).profile("p");

		assertThat(profile.password()).isEqualTo("from-env");
		assertThat(profile.user()).isEqualTo("app_dev");
	}

	@Test
	void undefinedEnvReferenceNamesVariableAndProfileOnly() throws Exception {
		write("profiles.yaml", """
				profiles:
				  p:
				    host: h
				    password: ${DB_PASS}
				  other:
				    host: h
				    password: ${NOT_SET}
				""");

		assertThat(loader(Map.of("DB_PASS", "x")).profile("p").password()).isEqualTo("x");
		assertThatThrownBy(() -> loader(Map.of()).profile("p")).isInstanceOf(ConfigException.class)
			.hasMessageContaining("DB_PASS")
			.hasMessageContaining("'p'");
	}

	@Test
	void unknownProfileListsKnownNames() throws Exception {
		write("profiles.yaml", """
				profiles:
				  beta: {host: h}
				  alpha: {host: h}
				""");

		assertThatThrownBy(() -> loader(Map.of()).profile("gamma")).isInstanceOf(ConfigException.class)
			.hasMessageContaining("gamma")
			.hasMessageContaining("alpha, beta");
	}

	@Test
	void unknownProfileWithoutFileSaysSo() {
		assertThatThrownBy(() -> loader(Map.of()).profile("x")).isInstanceOf(ConfigException.class)
			.hasMessageContaining("x")
			.hasMessageContaining("profiles.yaml");
	}

	@Test
	void invalidProfileFieldsGiveClearErrors() throws Exception {
		write("profiles.yaml", """
				profiles:
				  typo: {host: h, usr: bob}
				  both: {host: h, url: "jdbc:postgresql://h/d"}
				  port: {host: h, port: abc}
				""");

		assertThatThrownBy(() -> loader(Map.of()).profile("typo")).isInstanceOf(ConfigException.class)
			.hasMessageContaining("typo")
			.hasMessageContaining("usr");
		assertThatThrownBy(() -> loader(Map.of()).profile("both")).isInstanceOf(ConfigException.class)
			.hasMessageContaining("url")
			.hasMessageContaining("host");
		assertThatThrownBy(() -> loader(Map.of()).profile("port")).isInstanceOf(ConfigException.class)
			.hasMessageContaining("port");
	}

	// --- Override order: options over Profile over Settings over defaults ---

	private static ConnectionOptions options() {
		return new ConnectionOptions();
	}

	@Test
	void profileValuesBuildUrlAndProperties() {
		Profile profile = new Profile("p", null, "h", 6543, "app", "alice", "pw", null, "shop",
				Map.of("sslmode", "require"));

		ConnectionParameters parameters = ConnectionParameters.resolve(options(), profile, Settings.defaults());

		assertThat(parameters.url()).isEqualTo("jdbc:postgresql://h:6543/app");
		assertThat(parameters.properties()).containsEntry("user", "alice")
			.containsEntry("password", "pw")
			.containsEntry("ApplicationName", "dbui")
			.containsEntry("currentSchema", "shop")
			.containsEntry("sslmode", "require");
		assertThat(parameters.readOnly()).isFalse();
	}

	@Test
	void optionsOverrideProfileFields() {
		Profile profile = new Profile("p", null, "h", 5432, "app", "alice", "pw", false, "shop", Map.of());
		ConnectionOptions options = options();
		options.host = "tunnel";
		options.port = 7777;
		options.database = "other";
		options.user = "bob";
		options.password = "cli-pw";
		options.readOnly = true;
		options.schema = "audit";

		ConnectionParameters parameters = ConnectionParameters.resolve(options, profile, Settings.defaults());

		assertThat(parameters.url()).isEqualTo("jdbc:postgresql://tunnel:7777/other");
		assertThat(parameters.properties()).containsEntry("user", "bob")
			.containsEntry("password", "cli-pw")
			.containsEntry("currentSchema", "audit");
		assertThat(parameters.readOnly()).isTrue();
	}

	@Test
	void singleOptionOverridesOnlyThatProfileField() {
		Profile profile = new Profile("p", null, "h", 5432, "app", "alice", "pw", null, null, Map.of());
		ConnectionOptions options = options();
		options.port = 7777;

		ConnectionParameters parameters = ConnectionParameters.resolve(options, profile, Settings.defaults());

		assertThat(parameters.url()).isEqualTo("jdbc:postgresql://h:7777/app");
		assertThat(parameters.properties()).containsEntry("user", "alice").doesNotContainKey("currentSchema");
	}

	@Test
	void urlOptionBeatsProfileHostFieldsAndPasswordFromProfileStillApplies() {
		Profile profile = new Profile("p", null, "h", 5432, "app", "alice", "pw", null, null, Map.of());
		ConnectionOptions options = options();
		options.url = "jdbc:postgresql://x:1/y";

		ConnectionParameters parameters = ConnectionParameters.resolve(options, profile, Settings.defaults());

		assertThat(parameters.url()).isEqualTo("jdbc:postgresql://x:1/y");
		assertThat(parameters.properties()).containsEntry("password", "pw");
	}

	@Test
	void passwordInUrlBeatsProfilePassword() {
		Profile profile = new Profile("p", null, "h", 5432, "app", "alice", "profile-pw", null, null, Map.of());
		ConnectionOptions options = options();
		options.url = "jdbc:postgresql://x:1/y?user=u&password=url-pw";

		ConnectionParameters parameters = ConnectionParameters.resolve(options, profile, Settings.defaults());

		assertThat(parameters.properties()).doesNotContainKey("password");
	}

	@Test
	void readOnlyFallsBackFromProfileToSettingsToOff() {
		Settings readOnlySettings = new Settings(1000, null, "table", "horizontal", true);
		Profile unset = new Profile("p", null, "h", 5432, "d", "u", "pw", null, null, Map.of());
		Profile off = new Profile("p", null, "h", 5432, "d", "u", "pw", false, null, Map.of());

		assertThat(ConnectionParameters.resolve(options(), unset, readOnlySettings).readOnly()).isTrue();
		assertThat(ConnectionParameters.resolve(options(), off, readOnlySettings).readOnly()).isFalse();
		assertThat(ConnectionParameters.resolve(options(), unset, Settings.defaults()).readOnly()).isFalse();
	}

	@Test
	void withoutProfileOptionsAloneAreEnoughAndPortDefaultsTo5432() {
		ConnectionOptions options = options();
		options.host = "h";
		options.database = "d";
		options.user = "u";
		options.password = "pw";

		ConnectionParameters parameters = ConnectionParameters.resolve(options, null, Settings.defaults());

		assertThat(parameters.url()).isEqualTo("jdbc:postgresql://h:5432/d");
	}

	@Test
	void missingValuesAreReportedTogetherAndNeverPrompt() {
		ConnectionOptions options = options();
		options.host = "h";

		assertThatThrownBy(() -> ConnectionParameters.resolve(options, null, Settings.defaults()))
			.isInstanceOf(ConfigException.class)
			.hasMessageContaining("--db")
			.hasMessageContaining("--user")
			.hasMessageContaining("--password")
			.hasMessageContaining("prompt");
	}

	@Test
	void missingPasswordAloneIsReported() {
		Profile profile = new Profile("p", null, "h", 5432, "d", "u", null, null, null, Map.of());

		assertThatThrownBy(() -> ConnectionParameters.resolve(options(), profile, Settings.defaults()))
			.isInstanceOf(ConfigException.class)
			.hasMessageContaining("password")
			.hasMessageNotContaining("--db");
	}

	@Test
	void missingHostAndUrlIsReported() {
		assertThatThrownBy(() -> ConnectionParameters.resolve(options(), null, Settings.defaults()))
			.isInstanceOf(ConfigException.class)
			.hasMessageContaining("--url")
			.hasMessageContaining("--host");
	}
}
