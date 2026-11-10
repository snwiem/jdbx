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
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");
	@TempDir Path directory;

	private ConnectionParameters parameters() {
		return new ConnectionParameters(POSTGRES.getJdbcUrl(),
			Map.of("user", POSTGRES.getUsername(), "password", POSTGRES.getPassword()), false);
	}

	private Session session() throws Exception {
		var parameters = parameters();
		var context = new SessionContext(directory.resolve("state"), Settings.defaults(), parameters, null, name -> null);
		return new Session(parameters.open(), SessionDirectory.create(context.stateBase()), context);
	}

	private static MessageBlock last(Session session, String text) throws Exception {
		return session.submit(text).get(10, TimeUnit.SECONDS).messageBlocks().getLast();
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
				.extracting(Candidate::insert).containsExactly("table", "markdown", "csv", "tsv", "json");
		}
	}
}
