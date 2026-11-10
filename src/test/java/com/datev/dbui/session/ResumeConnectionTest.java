package com.datev.dbui.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.datev.dbui.config.ConfigException;
import com.datev.dbui.config.ConnectionParameters;
import com.datev.dbui.config.Profile;
import com.datev.dbui.config.Settings;

/** How Resume gets a connection without a password prompt. */
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
	void otherwiseTheResultNamesTheMissingPasswordAndTheMissingDialog() {
		var result = plan(stored("db.example", 5433, "shop", "bob", null), false, name -> null);
		var unavailable = (ResumeConnection.Unavailable) result;
		assertThat(unavailable.message()).contains("SID", "bob@db.example:5433/shop", "password", "issue 0013")
			.doesNotContain("secret");
	}

	@Test
	void aProfileWhoseEnvironmentVariableIsMissingExplainsWhy() {
		var result = plan(stored("db.example", 5433, "shop", "bob", "prod"), false, name -> {
			throw new ConfigException("Profile 'prod' field 'password' uses ${PG_PASSWORD}, but that environment variable is not set.");
		});
		assertThat(((ResumeConnection.Unavailable) result).message()).contains("PG_PASSWORD");
	}
}
