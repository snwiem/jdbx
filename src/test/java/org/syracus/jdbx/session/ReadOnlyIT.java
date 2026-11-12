package com.datev.dbui.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.datev.dbui.config.ConnectionParameters;
import com.datev.dbui.config.Profile;
import com.datev.dbui.config.Settings;

/** Session seam: read-only is enforced by the server, and /readonly changes it for the Session. */
@Testcontainers
class ReadOnlyIT {

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

	@TempDir
	Path stateDir;

	@BeforeEach
	void table() throws Exception {
		try (Connection admin = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
				var statement = admin.createStatement()) {
			statement.execute("drop table if exists ro_target");
			statement.execute("create table ro_target (id int)");
			statement.execute("insert into ro_target values (1)");
		}
	}

	private ConnectionParameters parameters(boolean readOnly) {
		Map<String, String> properties = new LinkedHashMap<>();
		properties.put("user", POSTGRES.getUsername());
		properties.put("password", POSTGRES.getPassword());
		return new ConnectionParameters(POSTGRES.getJdbcUrl(), properties, readOnly);
	}

	private Session session(boolean readOnly) throws Exception {
		var parameters = parameters(readOnly);
		var settings = Settings.defaults().withExecution(1000, null, readOnly);
		var context = new SessionContext(stateDir, settings, parameters, null, name -> (Profile) null);
		return new Session(parameters.open(), SessionDirectory.create(stateDir), context);
	}

	private static MessageBlock last(Session session, String input) throws Exception {
		return session.submit(input).get(10, java.util.concurrent.TimeUnit.SECONDS).messageBlocks().getLast();
	}

	private long count() throws Exception {
		try (Connection admin = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
				var statement = admin.createStatement(); var rows = statement.executeQuery("select count(*) from ro_target")) {
			rows.next();
			return rows.getLong(1);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "insert into ro_target values (2)", "update ro_target set id = 9", "create table ro_new (id int)",
		"drop table ro_target", "truncate ro_target", "delete from ro_target" })
	void writesFailInReadOnlyMode(String write) throws Exception {
		try (Session session = session(true)) {
			var band = last(session, write);
			assertThat(band.kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(band.text()).contains("read-only");
		}
		assertThat(count()).isEqualTo(1);
	}

	@Test
	void selectWorksInReadOnlyMode() throws Exception {
		try (Session session = session(true)) {
			assertThat(last(session, "select id from ro_target").result().rows()).containsExactly(List.of(1));
		}
	}

	@Test
	void writesWorkInReadWriteMode() throws Exception {
		try (Session session = session(false)) {
			assertThat(last(session, "insert into ro_target values (2)").kind()).isEqualTo(MessageBlock.Kind.INFO);
		}
		assertThat(count()).isEqualTo(2);
	}

	@ParameterizedTest
	@ValueSource(strings = { "set default_transaction_read_only = off", "set session characteristics as transaction read write",
		"set transaction read write", "begin read write", "commit", "rollback" })
	void aStatementTypedByTheUserDoesNotLiftTheProtectionForTheNextStatement(String attempt) throws Exception {
		try (Session session = session(true)) {
			session.submit(attempt).get(10, java.util.concurrent.TimeUnit.SECONDS);
			var band = last(session, "insert into ro_target values (2)");
			assertThat(band.kind()).isEqualTo(MessageBlock.Kind.ERROR);
		}
		assertThat(count()).isEqualTo(1);
	}

	@ParameterizedTest
	@ValueSource(strings = { "set transaction read write; insert into ro_target values (2)",
		"begin read write; insert into ro_target values (2); commit",
		"with moved as (insert into ro_target values (2) returning id) select id from moved" })
	void oneInputWithSeveralStatementsCannotWriteEither(String input) throws Exception {
		try (Session session = session(true)) {
			session.submit(input).get(10, java.util.concurrent.TimeUnit.SECONDS);
		}
		assertThat(count()).isEqualTo(1);
	}

	@Test
	void readonlyIsRegisteredWithHelpAndCompletionCandidates() throws Exception {
		try (Session session = session(false)) {
			assertThat(last(session, "/help commands").text()).contains("/readonly [on|off]");
			assertThat(session.slashCommands().completionCandidates("readonly")).containsExactly("on", "off");
			assertThat(session.slashCommands().parse("/readonly on").background()).isTrue();
		}
	}

	@Test
	void readonlyWithoutArgumentShowsTheCurrentState() throws Exception {
		try (Session session = session(true)) {
			assertThat(last(session, "/readonly").text()).isEqualTo("read-only");
		}
		try (Session session = session(false)) {
			assertThat(last(session, "/readonly").text()).isEqualTo("read-write");
		}
	}

	@Test
	void readonlyOnAndOffOverrideTheStartValueForTheSession() throws Exception {
		try (Session session = session(false)) {
			assertThat(last(session, "/readonly on").kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(session.settings().readOnly()).isTrue();
			assertThat(session.status().readOnly()).isTrue();
			assertThat(last(session, "insert into ro_target values (2)").kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(last(session, "/readonly off").kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(session.status().readOnly()).isFalse();
			assertThat(last(session, "insert into ro_target values (3)").kind()).isEqualTo(MessageBlock.Kind.INFO);
		}
		assertThat(count()).isEqualTo(2);
	}

	@Test
	void readonlyWithAWrongArgumentShowsUsageAndChangesNothing() throws Exception {
		try (Session session = session(false)) {
			var band = last(session, "/readonly maybe");
			assertThat(band.kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(band.text()).contains("Usage: /readonly [on|off]");
			assertThat(session.settings().readOnly()).isFalse();
		}
	}

	@Test
	void aRejectedChangeLeavesTheModeUnchangedAndShowsAnErrorBand() throws Exception {
		var real = parameters(false).open();
		Connection refusing = (Connection) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[] { Connection.class }, (proxy, method, args) -> {
				if (method.getName().equals("setReadOnly") && args[0].equals(true)) {
					throw new java.sql.SQLException("cannot change read-only now", "25001");
				}
				try {
					return method.invoke(real, args);
				}
				catch (java.lang.reflect.InvocationTargetException e) {
					throw e.getCause();
				}
			});
		var context = new SessionContext(stateDir, Settings.defaults(), parameters(false), null, name -> (Profile) null);
		try (Session session = new Session(refusing, SessionDirectory.create(stateDir), context)) {
			var band = last(session, "/readonly on");
			assertThat(band.kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(band.text()).contains("cannot change read-only now");
			assertThat(session.settings().readOnly()).isFalse();
			assertThat(session.status().readOnly()).isFalse();
			assertThat(SessionMetadata.read(session.directory().metadataFile()).readOnly()).isFalse();
			assertThat(last(session, "insert into ro_target values (2)").kind()).isEqualTo(MessageBlock.Kind.INFO);
		}
	}

	@Test
	void resumeRestoresTheReadOnlyModeOfTheEarlierSession() throws Exception {
		String earlier;
		try (Session first = session(false)) {
			last(first, "/readonly on");
			earlier = first.directory().id();
		}
		try (Session second = session(false)) {
			assertThat(last(second, "/resume " + earlier).kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(second.settings().readOnly()).isTrue();
			assertThat(last(second, "/readonly").text()).isEqualTo("read-only");
			assertThat(last(second, "insert into ro_target values (2)").kind()).isEqualTo(MessageBlock.Kind.ERROR);
		}
		assertThat(count()).isEqualTo(1);
	}
}
