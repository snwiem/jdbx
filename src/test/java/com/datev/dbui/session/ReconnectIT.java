package com.datev.dbui.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.datev.dbui.config.ConnectionParameters;
import com.datev.dbui.config.Profile;
import com.datev.dbui.config.Settings;

/** Session seam: a lost connection is restored once on the next input, and a Statement never runs twice. */
@Testcontainers
class ReconnectIT {

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

	@TempDir
	Path stateDir;

	@BeforeEach
	void tables() throws Exception {
		try (Connection admin = admin(); var statement = admin.createStatement()) {
			statement.execute("drop table if exists rc_counter");
			statement.execute("create table rc_counter (n int)");
			statement.execute("create schema if not exists rc_schema");
			statement.execute("drop table if exists rc_schema.rc_first");
			statement.execute("create table rc_schema.rc_first (id int)");
		}
	}

	private static Connection admin() throws Exception {
		return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
	}

	private ConnectionParameters parameters(String url, boolean readOnly) {
		Map<String, String> properties = new LinkedHashMap<>();
		properties.put("user", POSTGRES.getUsername());
		properties.put("password", POSTGRES.getPassword());
		properties.put("currentSchema", "rc_schema");
		properties.put("connectTimeout", "3");
		return new ConnectionParameters(url, properties, readOnly);
	}

	private Session session(Settings settings, ConnectionParameters reconnectWith) throws Exception {
		var context = new SessionContext(stateDir, settings, reconnectWith, null, name -> (Profile) null);
		return new Session(parameters(POSTGRES.getJdbcUrl(), settings.readOnly()).open(), SessionDirectory.create(stateDir), context);
	}

	private Session session(Settings settings) throws Exception {
		return session(settings, parameters(POSTGRES.getJdbcUrl(), settings.readOnly()));
	}

	private static Session.Update submit(Session session, String input) throws Exception {
		return session.submit(input).get(10, TimeUnit.SECONDS);
	}

	private static MessageBlock last(Session session, String input) throws Exception {
		return submit(session, input).messageBlocks().getLast();
	}

	private static Object pid(Session session) throws Exception {
		return last(session, "select pg_backend_pid()").result().rows().getFirst().getFirst();
	}

	private static void terminate(Object pid) throws Exception {
		try (Connection admin = admin(); var statement = admin.createStatement()) {
			statement.execute("select pg_terminate_backend(" + pid + ")");
		}
		Thread.sleep(200);
	}

	private static long counter() throws Exception {
		try (Connection admin = admin(); var statement = admin.createStatement();
				var rows = statement.executeQuery("select count(*) from rc_counter")) {
			rows.next();
			return rows.getLong(1);
		}
	}

	@Test
	void theStatementThatHitTheDeadConnectionFailsAndTheNextInputReconnectsOnce() throws Exception {
		try (Session session = session(Settings.defaults())) {
			Object oldPid = pid(session);
			terminate(oldPid);

			var failed = last(session, "insert into rc_counter values (1)");
			assertThat(failed.kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(failed.text()).containsIgnoringCase("connection lost");
			assertThat(counter()).isZero();

			var next = submit(session, "select 1 as one").messageBlocks();
			assertThat(next.get(next.size() - 2).kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(next.get(next.size() - 2).text()).containsIgnoringCase("reconnected");
			assertThat(next.getLast().result().rows()).containsExactly(List.of(1));
			assertThat(counter()).as("the failed Statement was never run again").isZero();
			assertThat(pid(session)).isNotEqualTo(oldPid);

			var third = submit(session, "select 2 as two").messageBlocks();
			assertThat(third.getLast().result().rows()).containsExactly(List.of(2));
			assertThat(third.stream().filter(b -> b.text() != null && b.text().toLowerCase().contains("reconnected")).count())
				.as("exactly one reconnect").isEqualTo(1);
			assertThat(session.status().connected()).isTrue();
		}
	}

	@Test
	void afterAReconnectTimeoutRowLimitReadOnlySchemaCacheAndSearchPathApplyToTheNewConnection() throws Exception {
		var settings = Settings.defaults().withExecution(2, java.time.Duration.ofSeconds(1), false);
		try (Session session = session(settings)) {
			last(session, "/describe rc_schema");
			last(session, "create table rc_schema.rc_second (id int)");
			assertThat(last(session, "/describe rc_schema").result().rows()).extracting(row -> row.getFirst())
				.doesNotContain("rc_second");
			last(session, "/readonly on");
			terminate(pid(session));
			assertThat(last(session, "select 1").kind()).isEqualTo(MessageBlock.Kind.ERROR);

			assertThat(submit(session, "show search_path").messageBlocks().getLast().result().rows().getFirst().getFirst().toString())
				.contains("rc_schema");
			assertThat(session.status().readOnly()).isTrue();
			assertThat(last(session, "insert into rc_first values (1)").text()).contains("read-only");
			var limited = submit(session, "select generate_series(1,5)").messageBlocks();
			assertThat(limited.get(limited.size() - 2).result().rows()).hasSize(2);
			assertThat(last(session, "select pg_sleep(10)").text()).contains("57014");
			assertThat(last(session, "/describe rc_schema").result().rows()).extracting(row -> row.getFirst())
				.contains("rc_second");
		}
	}

	@Test
	void aFailedReconnectShowsAnErrorBandAndNotConnectedAndTheNextInputTriesAgain() throws Exception {
		var unreachable = parameters("jdbc:postgresql://localhost:1/nothing", false);
		try (Session session = session(Settings.defaults(), unreachable)) {
			terminate(pid(session));
			assertThat(last(session, "select 1").text()).containsIgnoringCase("connection lost");

			var failed = last(session, "insert into rc_counter values (1)");
			assertThat(failed.kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(failed.text()).containsIgnoringCase("cannot reconnect");
			assertThat(session.status().connected()).isFalse();
			assertThat(counter()).isZero();

			assertThat(last(session, "select 1").text()).containsIgnoringCase("cannot reconnect");
			assertThat(session.status().connected()).isFalse();
		}
	}
}
