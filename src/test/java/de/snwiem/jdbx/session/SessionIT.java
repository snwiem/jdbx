package org.syracus.jdbx.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Session seam: input in, Message blocks and Results out, without a terminal. */
@Testcontainers
class SessionIT {

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

	@TempDir
	Path stateDir;

	private Connection connection() throws Exception {
		return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
	}

	private Session session() throws Exception {
		return new Session(connection(), SessionDirectory.create(stateDir));
	}

	@Test
	void transcriptRetainsOnlyTwoHundredBlocksAndOneResultAfterManyInputs() throws Exception {
		try (Session session = session()) {
			for (int i = 0; i < 110; i++) session.submit("select " + i + " as id").get(5, TimeUnit.SECONDS);
			assertThat(session.messageBlocks()).hasSize(200);
			assertThat(session.messageBlocks().getFirst().text()).isEqualTo("select 10 as id");
			assertThat(session.messageBlocks()).filteredOn(block -> block.result() != null).hasSize(1);
			assertThat(session.messageBlocks().get(1).text()).isEqualTo("id\n--\n10\n");
		}
	}

	@Test
	void invalidConfiguredTimeoutIsRejectedBeforeExecutingStatements() throws Exception {
		var settings = new org.syracus.jdbx.config.Settings(1000, java.time.Duration.ofSeconds(-1), "table", "horizontal", false);
		try (Connection connection = connection()) {
			assertThatThrownBy(() -> new Session(connection, SessionDirectory.create(stateDir), settings))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("Timeout must be 0 or more seconds");
		}
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(ints = { 0, -1, -2 })
	void invalidConfiguredRowLimitCannotDisableTheJdbcRowLimit(int rowLimit) throws Exception {
		var settings = new org.syracus.jdbx.config.Settings(rowLimit, null, "table", "horizontal", false);
		try (Connection connection = connection()) {
			assertThatThrownBy(() -> new Session(connection, SessionDirectory.create(stateDir), settings))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("Row limit must be 1 or more");
		}
	}

	@Test
	void closingTheSessionCancelsAnActiveStatementInsteadOfWaitingForIt() throws Exception {
		Session session = session();
		try {
			var pending = session.submit("select pg_sleep(10)");
			Thread.sleep(200);
			java.util.concurrent.CompletableFuture.runAsync(() -> {
				try {
					session.close();
				}
				catch (java.sql.SQLException e) {
					throw new java.util.concurrent.CompletionException(e);
				}
			}).get(4, TimeUnit.SECONDS);
			assertThat(pending).isDone();
		}
		finally {
			session.close();
		}
	}

	@Test
	void cancelJustBeforeJdbcExecutionIsNotLost() throws Exception {
		var beforeExecute = new java.util.concurrent.CountDownLatch(1);
		var allowExecute = new java.util.concurrent.CountDownLatch(1);
		var cancelSent = new java.util.concurrent.CountDownLatch(1);
		Connection real = connection();
		// Pause at the JDBC boundary to reproduce pgjdbc's idle-to-executing cancellation window.
		Connection delayed = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Connection.class },
			(proxy, method, args) -> {
				try {
					Object value = method.invoke(real, args);
					if (method.getName().equals("createStatement")) {
						return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { java.sql.Statement.class },
							(statementProxy, statementMethod, statementArgs) -> {
								if (statementMethod.getName().equals("execute")) {
									beforeExecute.countDown();
									allowExecute.await(5, TimeUnit.SECONDS);
								}
								try {
									Object result = statementMethod.invoke(value, statementArgs);
									if (statementMethod.getName().equals("cancel")) {
										cancelSent.countDown();
									}
									return result;
								}
								catch (InvocationTargetException e) {
									throw e.getCause();
								}
							});
					}
					return value;
				}
				catch (InvocationTargetException e) {
					throw e.getCause();
				}
			});
		try (Session session = new Session(delayed, SessionDirectory.create(stateDir))) {
			var pending = session.submit("select pg_sleep(10)");
			assertThat(beforeExecute.await(2, TimeUnit.SECONDS)).isTrue();
			var cancellation = session.cancel();
			try {
				assertThat(cancelSent.await(2, TimeUnit.SECONDS)).isTrue();
			}
			finally {
				allowExecute.countDown();
			}
			assertThat(pending.get(4, TimeUnit.SECONDS).messageBlocks().getLast().kind()).isEqualTo(MessageBlock.Kind.ERROR);
			cancellation.get(2, TimeUnit.SECONDS);
			assertThat(session.submit("select 42").get(5, TimeUnit.SECONDS).messageBlocks().getLast().result().rows().getFirst())
				.containsExactly(42);
		}
	}

	@Test
	void firstErrorStopsTheInputAndDoesNotUndoEarlierStatements() throws Exception {
		try (Session session = session()) {
			var blocks = session.submit("create temporary table stop_values (v int); insert into stop_values values (1); "
				+ "select 1/0; insert into stop_values values (99); select 99 as must_not_run")
				.get(5, TimeUnit.SECONDS).messageBlocks();
			assertThat(blocks).extracting(MessageBlock::kind).containsExactly(MessageBlock.Kind.INPUT,
				MessageBlock.Kind.INFO, MessageBlock.Kind.INFO, MessageBlock.Kind.ERROR);
			assertThat(blocks.getLast().text()).contains("division by zero");
			assertThat(session.submit("select v from stop_values").get(5, TimeUnit.SECONDS).messageBlocks().getLast()
				.result().rows()).containsExactly(java.util.List.of(1));
		}
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "/limit", "/limit 0", "/limit -1", "/limit nope",
		"/limit 1.5", "/limit 2147483648", "/limit 1 extra", "/timeout", "/timeout 0", "/timeout -1",
		"/timeout nope", "/timeout 1.5", "/timeout 2147483648", "/timeout off extra" })
	void invalidExecutionControlShowsAnErrorAndLeavesThePreviousSettingsUnchanged(String input) throws Exception {
		var settings = new org.syracus.jdbx.config.Settings(5, java.time.Duration.ofSeconds(1), "table", "horizontal", false);
		try (Session session = new Session(connection(), SessionDirectory.create(stateDir), settings)) {
			var block = session.submit(input).get(5, TimeUnit.SECONDS).messageBlocks().getLast();
			assertThat(block.kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(block.text()).startsWith("Usage: " + input.split(" ")[0]);
			assertThat(session.status().rowLimit()).isEqualTo(5);
			assertThat(session.submit("select pg_sleep(10)").get(4, TimeUnit.SECONDS).messageBlocks().getLast().kind())
				.isEqualTo(MessageBlock.Kind.ERROR);
		}
	}

	@Test
	void immediateCancelAndIdleCancelLeaveTheSessionUsable() throws Exception {
		try (Session session = session()) {
			session.cancel().get(1, TimeUnit.SECONDS);
			for (int attempt = 0; attempt < 10; attempt++) {
				var pending = session.submit("select pg_sleep(10)");
				session.cancel().get(2, TimeUnit.SECONDS);
				var update = pending.get(3, TimeUnit.SECONDS);
				assertThat(update.messageBlocks().getLast().kind()).isIn(MessageBlock.Kind.INFO, MessageBlock.Kind.ERROR);
			}
			assertThat(session.submit("select 42").get(5, TimeUnit.SECONDS).messageBlocks().getLast().result().rows().getFirst())
				.containsExactly(42);
		}
	}

	@Test
	void statusTracksTheRunningTimerAndLastResultRowsDurationAndTruncation() throws Exception {
		try (Session session = session()) {
			session.submit("/limit 5").get(5, TimeUnit.SECONDS);
			assertThat(session.status().rowLimit()).isEqualTo(5);
			assertThat(session.status().lastResult()).isNull();
			var pending = session.submit("select pg_sleep(0.2), generate_series(1,6)");
			Thread.sleep(100);
			assertThat(session.status().running()).isTrue();
			assertThat(session.status().elapsed()).isGreaterThan(java.time.Duration.ZERO);
			pending.get(5, TimeUnit.SECONDS);
			var status = session.status();
			assertThat(status.running()).isFalse();
			assertThat(status.lastResult().rows()).isEqualTo(5);
			assertThat(status.lastResult().duration()).isGreaterThanOrEqualTo(java.time.Duration.ofMillis(200));
			assertThat(status.lastResult().truncated()).isTrue();
			session.submit("select 1/0").get(5, TimeUnit.SECONDS);
			assertThat(session.status().lastResult()).isEqualTo(status.lastResult());
		}
	}

	@Test
	void cancelStopsALongStatementAndRemainingStatementsWithoutEndingTheSession() throws Exception {
		try (Session session = session()) {
			var pending = session.submit("select pg_sleep(10); select 99 as must_not_run");
			Thread.sleep(200);
			session.cancel();
			var update = pending.get(4, TimeUnit.SECONDS);
			assertThat(update.ended()).isFalse();
			assertThat(update.messageBlocks()).extracting(MessageBlock::kind)
				.containsExactly(MessageBlock.Kind.INPUT, MessageBlock.Kind.ERROR);
			assertThat(update.messageBlocks().getLast().text()).contains("canceling statement", "57014");
			assertThat(session.submit("select 42").get(5, TimeUnit.SECONDS).messageBlocks().getLast().result().rows().getFirst())
				.containsExactly(42);
		}
	}

	@Test
	void timeoutSlashCommandCanEnableAndDisableTheSessionTimeout() throws Exception {
		try (Session session = session()) {
			var enabled = session.submit("/timeout 1").get(5, TimeUnit.SECONDS).messageBlocks().getLast();
			assertThat(enabled.kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(enabled.text()).isEqualTo("Timeout: 1 s");
			assertThat(session.submit("select pg_sleep(10)").get(4, TimeUnit.SECONDS).messageBlocks().getLast().kind())
				.isEqualTo(MessageBlock.Kind.ERROR);
			var disabled = session.submit("/timeout off").get(5, TimeUnit.SECONDS).messageBlocks().getLast();
			assertThat(disabled.kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(disabled.text()).isEqualTo("Timeout: off");
			assertThat(session.submit("select pg_sleep(1.2), 7 as ok").get(5, TimeUnit.SECONDS).messageBlocks().getLast()
				.result().rows().getFirst().get(1)).isEqualTo(7);
		}
	}

	@Test
	void configuredTimeoutStopsAStatementWithAnErrorAndLeavesSessionUsable() throws Exception {
		var settings = new org.syracus.jdbx.config.Settings(1000, java.time.Duration.ofSeconds(1), "table", "horizontal", false);
		try (Session session = new Session(connection(), SessionDirectory.create(stateDir), settings)) {
			var block = session.submit("select pg_sleep(10)").get(4, TimeUnit.SECONDS).messageBlocks().getLast();
			assertThat(block.kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(block.text()).contains("canceling statement", "57014").doesNotContain("Exception");
			assertThat(session.submit("select 42").get(5, TimeUnit.SECONDS).messageBlocks().getLast().result().rows().getFirst())
				.containsExactly(42);
		}
	}

	@Test
	void statementsOfOneInputRunInOrderWithSeparateResults() throws Exception {
		try (Session session = session()) {
			var blocks = session.submit("create temporary table script_values (v text); "
				+ "do $$ begin insert into script_values values ('a;b'); end $$; "
				+ "select v from script_values; /* ; */ select 'last;value' as v")
				.get(5, TimeUnit.SECONDS).messageBlocks();
			assertThat(blocks).extracting(MessageBlock::kind).containsExactly(MessageBlock.Kind.INPUT,
				MessageBlock.Kind.INFO, MessageBlock.Kind.INFO, MessageBlock.Kind.RESULT, MessageBlock.Kind.RESULT);
			assertThat(blocks.get(3).result()).isNull();
			assertThat(blocks.get(3).text()).isEqualTo("v\n---\na;b\n");
			assertThat(blocks.get(4).result().rows().getFirst()).containsExactly("last;value");
		}
	}

	@Test
	void limitSlashCommandOverridesTheConfiguredRowLimitForThisSession() throws Exception {
		var settings = new org.syracus.jdbx.config.Settings(3, null, "table", "horizontal", false);
		try (Session session = new Session(connection(), SessionDirectory.create(stateDir), settings)) {
			var configured = session.submit("select generate_series(1,4)").get(5, TimeUnit.SECONDS).messageBlocks();
			assertThat(configured.get(1).result().rows()).hasSize(3);
			assertThat(configured.getLast().text()).isEqualTo("truncated at 3 rows");
			var changed = session.submit("/limit 5").get(5, TimeUnit.SECONDS).messageBlocks().getLast();
			assertThat(changed.kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(changed.text()).isEqualTo("Row limit: 5");
			var exact = session.submit("select generate_series(1,5)").get(5, TimeUnit.SECONDS).messageBlocks().getLast();
			assertThat(exact.kind()).isEqualTo(MessageBlock.Kind.RESULT);
			assertThat(exact.result().rows()).hasSize(5);
			var truncated = session.submit("select generate_series(1,6)").get(5, TimeUnit.SECONDS).messageBlocks();
			assertThat(truncated.get(truncated.size() - 2).result().rows()).hasSize(5);
			assertThat(truncated.getLast().text()).isEqualTo("truncated at 5 rows");
		}
	}

	@Test
	void defaultRowLimitTruncatesAt1000WithoutRewritingTheStatement() throws Exception {
		String sql = "select n, current_query() as sql from generate_series(1,1500) n";
		try (Session session = session()) {
			var blocks = session.submit(sql).get(5, TimeUnit.SECONDS).messageBlocks();
			assertThat(blocks).extracting(MessageBlock::kind)
				.containsExactly(MessageBlock.Kind.INPUT, MessageBlock.Kind.RESULT, MessageBlock.Kind.INFO);
			var result = blocks.get(1).result();
			assertThat(result.rows()).hasSize(1000);
			assertThat(result.rows().getLast()).containsExactly(1000, sql);
			assertThat(blocks.getLast().text()).isEqualTo("truncated at 1000 rows");
		}
	}

	@Test
	void resultPreservesLocalTemporalValuesAndStructuredJson() throws Exception {
		try (Session session = session()) {
			var update = session.submit("select timestamp '2024-01-15 12:34:56' as plain, "
				+ "timestamptz '2024-01-15 12:34:56+00' as zoned, date '2024-01-15' as day, "
				+ "time '12:34:56' as clock, '{\"id\":7}'::json as j, '{\"id\":8}'::jsonb as jb")
				.get(5, TimeUnit.SECONDS);
			assertThat(update.messageBlocks().getLast().result().rows().getFirst()).containsExactly(
				java.time.LocalDateTime.of(2024, 1, 15, 12, 34, 56),
				java.time.OffsetDateTime.parse("2024-01-15T12:34:56Z"), java.time.LocalDate.of(2024, 1, 15),
				java.time.LocalTime.of(12, 34, 56), new org.syracus.jdbx.JsonValue("{\"id\":7}"),
				new org.syracus.jdbx.JsonValue("{\"id\": 8}"));
		}
	}

	@Test
	void sqlErrorShowsServerMessageSqlStateAndPositionAndKeepsTheSessionUsable() throws Exception {
		try (Session session = session()) {
			var failed = session.submit("select nme from (select 1 as name) t").get(5, TimeUnit.SECONDS);
			assertThat(failed.messageBlocks().getLast().kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(failed.messageBlocks().getLast().text())
				.isEqualTo("ERROR: column \"nme\" does not exist (42703), position 8")
				.doesNotContain("Exception", "\tat ");
			assertThat(session.submit("select 1 as ok").get(5, TimeUnit.SECONDS).messageBlocks().getLast().kind())
				.isEqualTo(MessageBlock.Kind.RESULT);
		}
	}

	@Test
	void statementsWithoutAResultShowAnInfoBlockWithCommandTagAndCount() throws Exception {
		try (Session session = session()) {
			var created = session.submit("create temporary table info_t (i int)").get(5, TimeUnit.SECONDS);
			assertThat(created.messageBlocks()).extracting(MessageBlock::kind)
				.containsExactly(MessageBlock.Kind.INPUT, MessageBlock.Kind.INFO);
			assertThat(created.messageBlocks().getLast().text()).isEqualTo("CREATE TABLE");
			var inserted = session.submit("insert into info_t values (1),(2),(3)").get(5, TimeUnit.SECONDS);
			assertThat(inserted.messageBlocks().getLast().kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(inserted.messageBlocks().getLast().text()).isEqualTo("INSERT 0 3");
		}
	}

	@Test
	void twoSessionsWriteToTwoDifferentLogFilesInTheirOwnDirectories() throws Exception {
		try (Session first = session(); Session second = session()) {
			assertThat(first.logFile()).isNotEqualTo(second.logFile());
			assertThat(first.logFile().getParent()).isNotEqualTo(second.logFile().getParent());
			assertThat(first.logFile().getParent().getParent()).isEqualTo(stateDir.resolve("sessions"));
		}
	}

	@Test
	void unexpectedErrorShowsAShortMessageWithTheLogPathAndWritesTheStackTraceToTheLog() throws Exception {
		Connection real = connection();
		Connection broken = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Connection.class },
			(proxy, method, args) -> {
				if (method.getName().equals("createStatement")) {
					throw new IllegalStateException("internal failure");
				}
				try {
					return method.invoke(real, args);
				}
				catch (InvocationTargetException e) {
					throw e.getCause();
				}
			});
		try (Session session = new Session(broken, SessionDirectory.create(stateDir))) {
			var update = session.submit("select 1").get(5, TimeUnit.SECONDS);
			var block = update.messageBlocks().getLast();
			assertThat(block.kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(block.text()).contains("internal failure").contains(session.logFile().toString())
				.doesNotContain("\tat ").doesNotContain("IllegalStateException");
			assertThat(Files.readString(session.logFile())).contains("java.lang.IllegalStateException: internal failure")
				.contains("\tat ");
			assertThat(session.submit("select 1").get(5, TimeUnit.SECONDS).ended()).isFalse();
		}
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
	void aLeadingSqlBlockCommentIsNotASlashCommand() throws Exception {
		try (Session session = session()) {
			var update = session.submit("  /* report */ select 17 as id").get(5, TimeUnit.SECONDS);
			assertThat(update.messageBlocks().getLast().kind()).isEqualTo(MessageBlock.Kind.RESULT);
			assertThat(update.messageBlocks().getLast().result().rows().getFirst()).containsExactly(17);
		}
	}

	@Test
	void helpListsAllRegisteredSlashCommandsWithoutSendingAnythingToTheDatabase() throws Exception {
		try (Session session = session()) {
			var help = session.submit("/help").get(5, TimeUnit.SECONDS).messageBlocks().getLast();
			assertThat(help.kind()).isEqualTo(MessageBlock.Kind.INFO);
			for (String name : java.util.List.of("quit", "limit", "timeout", "describe", "refresh", "resume", "format", "orientation", "help")) {
				assertThat(help.text()).contains("/" + name);
			}
			assertThat(help.text()).doesNotContain("/tables", "/schema");
		}
	}

	@Test
	void unknownSlashCommandIsNotSentToTheDatabase() throws Exception {
		try (Session session = session()) {
			var update = session.submit("/not-a-command").get(5, TimeUnit.SECONDS);
			assertThat(update.ended()).isFalse();
			assertThat(update.messageBlocks().getLast().kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(update.messageBlocks().getLast().text()).isEqualTo("Unknown Slash command: /not-a-command");
			assertThat(session.submit("select 1").get(5, TimeUnit.SECONDS).messageBlocks().getLast().result().rows().getFirst())
				.containsExactly(1);
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
