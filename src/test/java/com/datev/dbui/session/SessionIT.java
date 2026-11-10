package com.datev.dbui.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Session seam: input in, Message blocks and Results out, without a terminal. */
@Testcontainers
class SessionIT {

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

	private Session session() throws Exception {
		return new Session(DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
	}

	@Test
	void slowStatementDoesNotBlockInputAndEchoIsAvailableImmediately() throws Exception {
		try (Session session = session()) {
			var pending = session.submit("select pg_sleep(1)");
			assertThat(pending).isNotDone();
			assertThat(session.messageBlocks()).extracting(MessageBlock::text).containsExactly("select pg_sleep(1)");
			assertThat(pending.get(5, TimeUnit.SECONDS).messageBlocks().getLast().kind()).isEqualTo(MessageBlock.Kind.RESULT);
		}
	}

	@Test
	void aSecondInputIsRejectedUntilTheStatementCompletes() throws Exception {
		try (Session session = session()) {
			var pending = session.submit("select pg_sleep(1)");
			var rejected = session.submit("select 2");
			assertThatThrownBy(() -> rejected.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(IllegalStateException.class);
			var update = pending.get(5, TimeUnit.SECONDS);
			assertThat(update.messageBlocks()).extracting(MessageBlock::kind)
				.containsExactly(MessageBlock.Kind.INPUT, MessageBlock.Kind.RESULT);
			assertThat(session.submit("select 3").get(5, TimeUnit.SECONDS).messageBlocks().getLast().result().rows().getFirst())
				.containsExactly(3);
		}
	}

	@Test
	void statementsShareOneAutocommitConnection() throws Exception {
		try (Session session = session()) {
			session.submit("create temporary table session_values (id integer)").get(5, TimeUnit.SECONDS);
			session.submit("insert into session_values values (7)").get(5, TimeUnit.SECONDS);
			var update = session.submit("select id from session_values").get(5, TimeUnit.SECONDS);
			assertThat(update.messageBlocks().getLast().result().rows().getFirst()).containsExactly(7);
		}
	}

	@Test
	void sqlErrorLeavesSessionUsable() throws Exception {
		try (Session session = session()) {
			var failed = session.submit("select * from no_such_table").get(5, TimeUnit.SECONDS);
			assertThat(failed.ended()).isFalse();
			assertThat(failed.messageBlocks().getLast().kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(failed.messageBlocks().getLast().text()).contains("no_such_table");
			var recovered = session.submit("select 42 as answer").get(5, TimeUnit.SECONDS);
			assertThat(recovered.messageBlocks().getLast().result().rows().getFirst()).containsExactly(42);
		}
	}

	@Test
	void onlyQuitEndsTheSession() throws Exception {
		try (Session session = session()) {
			assertThat(session.submit("  ").get(5, TimeUnit.SECONDS).ended()).isFalse();
			var update = session.submit(" /quit ").get(5, TimeUnit.SECONDS);
			assertThat(update.ended()).isTrue();
			assertThat(update.messageBlocks()).extracting(MessageBlock::kind).containsExactly(MessageBlock.Kind.INPUT);
			assertThat(update.messageBlocks().getFirst().text()).isEqualTo(" /quit ");
			var afterQuit = session.submit("select 99 as must_not_run").get(5, TimeUnit.SECONDS);
			assertThat(afterQuit.ended()).isTrue();
			assertThat(afterQuit.messageBlocks()).isEqualTo(update.messageBlocks());
		}
	}

	@Test
	void statementProducesInputEchoAndResult() throws Exception {
		try (Session session = session()) {
			var update = session.submit("select 1 as id, 'alice' as name, null as nothing").get(5, TimeUnit.SECONDS);

			assertThat(update.ended()).isFalse();
			assertThat(update.messageBlocks()).extracting(MessageBlock::kind)
				.containsExactly(MessageBlock.Kind.INPUT, MessageBlock.Kind.RESULT);
			assertThat(update.messageBlocks().getFirst().text()).isEqualTo("select 1 as id, 'alice' as name, null as nothing");
			var result = update.messageBlocks().getLast().result();
			assertThat(result.columns()).containsExactly("id", "name", "nothing");
			assertThat(result.rows().getFirst()).containsExactly(1, "alice", null);
		}
	}
}
