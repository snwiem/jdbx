package org.syracus.jdbx.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.syracus.jdbx.config.ConfigException;
import org.syracus.jdbx.config.ConnectionParameters;
import org.syracus.jdbx.config.Profile;
import org.syracus.jdbx.config.Settings;

/** How Resume reuses credentials or requests a masked prompt. */
class ResumeConnectionTest {

	private static final ConnectionParameters CURRENT = new ConnectionParameters("jdbc:postgresql://h:5432/shop",
		Map.of("user", "alice", "password", "secret", "ApplicationName", "dbui"), false);

	private static ConnectionInfo stored(String host, int port, String database, String user, String profile) {
		return new ConnectionInfo(host, port, database, user, "jdbc:postgresql://" + host + ":" + port + "/" + database, profile);
	}

	private static ResumeConnection.Plan plan(ConnectionInfo stored, boolean readOnly, java.util.function.Function<String, Profile> profiles) {
		return ResumeConnection.plan("SID", stored, readOnly, ConnectionInfo.of(CURRENT, null), CURRENT, profiles, Settings.defaults());
	}

	@Test
	void theSameConnectionIsKept() {
		assertThat(plan(stored("h", 5432, "shop", "alice", null), false, name -> null)).isInstanceOf(ResumeConnection.Keep.class);
	}

	@Test
	void theSameEndpointAndUserWithAnotherProfileReconnects() {
		var profile = new Profile("a", null, "h", 5432, "shop", "alice", "pw", null, null, Map.of());
		var currentInfo = ConnectionInfo.of(CURRENT, "b");
		var result = ResumeConnection.plan("SID", stored("h", 5432, "shop", "alice", "a"), false, currentInfo, CURRENT, name -> profile,
			Settings.defaults());
		assertThat(result).isInstanceOf(ResumeConnection.Reconnect.class);
	}

	@Test
	void theSameProfileWithAnotherCurrentSchemaReconnects() {
		var stored = new ConnectionInfo("h", 5432, "shop", "alice", "jdbc:postgresql://h:5432/shop?currentSchema=a", "p");
		var current = new ConnectionParameters("jdbc:postgresql://h:5432/shop?currentSchema=b",
			Map.of("user", "alice", "password", "secret"), false);
		var result = ResumeConnection.plan("SID", stored, false, ConnectionInfo.of(current, "p"), current, name -> null, Settings.defaults());
		assertThat(result).isInstanceOf(ResumeConnection.Reconnect.class);
	}

	@Test
	void theSameProfileWithAnotherSchemaPropertyReconnects() {
		var profile = new Profile("p", null, "h", 5432, "shop", "alice", "pw", null, "sa", Map.of());
		var stored = stored("h", 5432, "shop", "alice", "p");
		var current = new ConnectionParameters("jdbc:postgresql://h:5432/shop",
			Map.of("user", "alice", "password", "secret", "ApplicationName", "dbui", "currentSchema", "sb"), false);
		var result = ResumeConnection.plan("SID", stored, false, ConnectionInfo.of(current, "p"), current, name -> profile, Settings.defaults());
		assertThat(result).isInstanceOf(ResumeConnection.Reconnect.class);
	}

	@Test
	void theSameProfileWithTheSameSchemaPropertyIsKept() {
		var profile = new Profile("p", null, "h", 5432, "shop", "alice", "pw", null, "sa", Map.of());
		var current = new ConnectionParameters("jdbc:postgresql://h:5432/shop",
			Map.of("user", "alice", "password", "pw", "ApplicationName", "dbui", "currentSchema", "sa"), false);
		assertThat(ResumeConnection.plan("SID", stored("h", 5432, "shop", "alice", "p"), false, ConnectionInfo.of(current, "p"), current,
			name -> profile, Settings.defaults())).isInstanceOf(ResumeConnection.Keep.class);
	}

	@Test
	void theSameProfileAndUrlIsKept() {
		var stored = new ConnectionInfo("h", 5432, "shop", "alice", "jdbc:postgresql://h:5432/shop?currentSchema=a", "p");
		var current = new ConnectionParameters("jdbc:postgresql://h:5432/shop?currentSchema=a",
			Map.of("user", "alice", "password", "secret"), false);
		assertThat(ResumeConnection.plan("SID", stored, false, ConnectionInfo.of(current, "p"), current, name -> null, Settings.defaults()))
			.isInstanceOf(ResumeConnection.Keep.class);
	}

	@Test
	void anotherDatabaseOfTheSameUserHostAndPortReusesTheCurrentPassword() {
		var result = plan(stored("h", 5432, "other", "alice", null), true, name -> null);
		var reconnect = (ResumeConnection.Reconnect) result;
		assertThat(reconnect.parameters().url()).isEqualTo("jdbc:postgresql://h:5432/other");
		assertThat(reconnect.parameters().properties()).containsEntry("password", "secret").containsEntry("user", "alice");
		assertThat(reconnect.parameters().readOnly()).isTrue();
	}

	@Test
	void aProfileWithAResolvablePasswordReconnects() {
		var profile = new Profile("prod", null, "db.example", 5433, "shop", "bob", "from-env", null, null, Map.of());
		var result = plan(stored("db.example", 5433, "shop", "bob", "prod"), false, name -> profile);
		var reconnect = (ResumeConnection.Reconnect) result;
		assertThat(reconnect.parameters().url()).isEqualTo("jdbc:postgresql://db.example:5433/shop");
		assertThat(reconnect.parameters().properties()).containsEntry("password", "from-env");
	}

	@Test
	void otherwiseTheResultRequestsAPasswordWithNonSecretConnectionValues() {
		var result = plan(stored("db.example", 5433, "shop", "bob", null), false, name -> null);
		var prompt = (ResumeConnection.Prompt) result;
		assertThat(prompt.message()).contains("SID", "bob@db.example:5433/shop", "password").doesNotContain("secret");
		assertThat(prompt.parameters().properties()).containsEntry("user", "bob").doesNotContainKey("password");
	}

	@Test
	void aProfileWhoseEnvironmentVariableIsMissingExplainsWhy() {
		var result = plan(stored("db.example", 5433, "shop", "bob", "prod"), false, name -> {
			throw new ConfigException("Profile 'prod' field 'password' uses ${PG_PASSWORD}, but that environment variable is not set.");
		});
		assertThat(((ResumeConnection.Prompt) result).message()).contains("PG_PASSWORD");
	}
}
