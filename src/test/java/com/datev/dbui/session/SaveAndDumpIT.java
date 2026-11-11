package com.datev.dbui.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class SaveAndDumpIT {

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

	@TempDir Path directory;

	private Session session() throws Exception {
		return new Session(DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()),
			SessionDirectory.create(directory.resolve("state")));
	}

	private static MessageBlock submit(Session session, String input) throws Exception {
		return session.submit(input).get(10, TimeUnit.SECONDS).messageBlocks().getLast();
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = {
		"delete from dump_guard", "with x as (select 1) insert into dump_guard values (9) returning id",
		"with x as (delete from dump_guard returning id) select * from x",
		"with x as (update dump_guard set id=9 returning id) select * from x",
		"with x as (select 1) update dump_guard set id=9 returning id",
		"select * into dump_copy from dump_guard", "explain analyze delete from dump_guard",
		"copy dump_guard to stdout", "do $$ begin delete from dump_guard; end $$", "show all; set work_mem='8MB'",
		"select * from dump_guard for share", "select * from dump_guard for key share"
	})
	void dumpRejectsTheWholeInputBeforeEvenItsFirstQueryRuns(String rejected) throws Exception {
		Path target = directory.resolve("guard.csv");
		Files.writeString(target, "original");
		try (Session session = session()) {
			submit(session, "create temporary table dump_guard(id int); insert into dump_guard values (1); create temporary sequence dump_probe");
			var band = submit(session, "/dump " + target + " select nextval('dump_probe'); " + rejected);
			assertThat(band.kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(band.text()).contains("query", "Nothing was run");
			assertThat(submit(session, "select is_called from dump_probe").result().rows()).containsExactly(java.util.List.of(false));
			assertThat(submit(session, "select id from dump_guard").result().rows()).containsExactly(java.util.List.of(1));
			assertThat(Files.readString(target)).isEqualTo("original");
		}
	}

	@Test
	void dumpRefusesNonstandardStringsBeforeRunningAnyStatement() throws Exception {
		Path target = directory.resolve("nonstandard.csv");
		Files.writeString(target, "original");
		try (Session session = session()) {
			submit(session, "create temporary sequence nonstandard_probe");
			submit(session, "set standard_conforming_strings = off");
			var band = submit(session, "/dump " + target + " select nextval('nonstandard_probe'); "
				+ "select '\\''; commit; create table dump_bypass(id int); select ''''");
			assertThat(band.kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(band.text()).contains("standard_conforming_strings", "Nothing was run");
			assertThat(submit(session, "select is_called from nonstandard_probe").result().rows()).containsExactly(java.util.List.of(false));
			assertThat(submit(session, "select to_regclass('dump_bypass') is null").result().rows()).containsExactly(java.util.List.of(true));
			assertThat(Files.readString(target)).isEqualTo("original");
			try (var files = Files.list(directory)) { assertThat(files.map(p -> p.getFileName().toString())).noneMatch(n -> n.startsWith(".dbui-")); }
		}
	}

	@Test
	void dumpRejectsFunctionWritesAtTheServerWithoutChangingTheSessionReadOnlySetting() throws Exception {
		Path target = directory.resolve("side-effect.csv");
		try (Session session = session()) {
			submit(session, "create table dump_function_target(id int); "
				+ "create function pg_temp.dump_write() returns int language sql as $$ insert into dump_function_target values (1) returning id $$");
			var band = submit(session, "/dump " + target + " select pg_temp.dump_write()");
			assertThat(band.kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(band.text()).contains("read-only");
			assertThat(target).doesNotExist();
			assertThat(session.settings().readOnly()).isFalse();
			assertThat(submit(session, "select count(*) from dump_function_target").result().rows()).containsExactly(java.util.List.of(0L));
			assertThat(submit(session, "insert into dump_function_target values (2)").kind()).isEqualTo(MessageBlock.Kind.INFO);
		}
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "json", "json-pretty" })
	void dumpWritesOneNestedJsonDocumentForSeveralResultsIncludingAnEmptyResult(String format) throws Exception {
		Path target = directory.resolve(format + ".json");
		try (Session session = session()) {
			submit(session, "/format " + format);
			submit(session, "/orientation vertical");
			var band = submit(session, "/dump " + target + " select 7 as id; select 1 as id where false; select '{\"a\":[1,2]}'::jsonb as doc");
			assertThat(band.kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(band.text()).contains("2 rows");
			String text = Files.readString(target);
			var tokener = new org.json.JSONTokener(text);
			var all = (org.json.JSONArray) tokener.nextValue();
			assertThat(tokener.nextClean()).isEqualTo((char) 0);
			assertThat(all.length()).isEqualTo(3);
			assertThat(all.getJSONArray(0).getJSONObject(0).getInt("id")).isEqualTo(7);
			assertThat(all.getJSONArray(1).length()).isZero();
			assertThat(all.getJSONArray(2).getJSONObject(0).getJSONObject("doc").getJSONArray("a").toString()).isEqualTo("[1,2]");
			if (format.equals("json-pretty")) assertThat(text).startsWith("[\n  [\n    {\n      \"id\": 7\n    }\n  ],\n  [],\n");
		}
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "json", "json-pretty" })
	void aSingleResultDumpIsOneValidOuterArray(String format) throws Exception {
		Path target = directory.resolve("single-" + format + ".json");
		try (Session session = session()) {
			submit(session, "/format " + format);
			assertThat(submit(session, "/dump " + target + " select 7 as id").kind()).isEqualTo(MessageBlock.Kind.INFO);
			var tokener = new org.json.JSONTokener(Files.readString(target));
			var all = (org.json.JSONArray) tokener.nextValue();
			assertThat(tokener.nextClean()).isEqualTo((char) 0);
			assertThat(all.length()).isEqualTo(1);
			assertThat(all.getJSONArray(0).length()).isEqualTo(1);
			assertThat(all.getJSONArray(0).getJSONObject(0).getInt("id")).isEqualTo(7);
		}
	}

	@Test
	void completedDumpAndSaveFilesHavePrivatePosixPermissions() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"));
		Path dumped = directory.resolve("private-dump.csv"), saved = directory.resolve("private-save.csv");
		Files.writeString(dumped, "old");
		Files.setPosixFilePermissions(dumped, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
		try (Session session = session()) {
			assertThat(submit(session, "/dump " + dumped + " select 7 as id").kind()).isEqualTo(MessageBlock.Kind.INFO);
			submit(session, "select 8 as id");
			assertThat(submit(session, "/save " + saved).kind()).isEqualTo(MessageBlock.Kind.INFO);
			for (Path target : java.util.List.of(dumped, saved)) assertThat(Files.getPosixFilePermissions(target))
				.as("completed file %s", target).isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
		}
	}

	@Test
	void dumpAndSaveAcceptQuotedFileNamesWithSpacesAndKeepSqlQuotesLiteral() throws Exception {
		Path target = directory.resolve("quoted dump.csv");
		try (Session session = session()) {
			submit(session, "/format csv");
			assertThat(submit(session, "/dump \"" + target + "\" select 'a  b' as \"x y\"").kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(Files.readString(target)).isEqualTo("x y\na  b\n");
			submit(session, "select 2 as id");
			Path saved = directory.resolve("quoted save.csv");
			var band = submit(session, "/save '" + saved + "'");
			assertThat(band.kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(band.text()).isEqualTo("Saved 1 rows to " + saved + ".");
			assertThat(Files.readString(saved)).isEqualTo("id\n2\n");
		}
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "table:horizontal", "table:vertical", "markdown:horizontal", "markdown:vertical",
		"csv:horizontal", "csv:vertical", "tsv:horizontal", "tsv:vertical" })
	void saveAndDumpUseTheCurrentFormatAndOrientation(String output) throws Exception {
		String expected = switch (output) {
			case "table:horizontal" -> "id | name\n---+-----\n7  | A\n8  | B\n";
			case "table:vertical" -> "id   | 7 | 8\nname | A | B\n";
			case "markdown:horizontal" -> "| id | name |\n| --- | --- |\n| 7 | A |\n| 8 | B |\n";
			case "markdown:vertical" -> "| Column | Row 1 | Row 2 |\n| --- | --- | --- |\n| id | 7 | 8 |\n| name | A | B |\n";
			case "csv:horizontal" -> "id,name\n7,A\n8,B\n";
			case "csv:vertical" -> "id,7,8\nname,A,B\n";
			case "tsv:horizontal" -> "id\tname\n7\tA\n8\tB\n";
			case "tsv:vertical" -> "id\t7\t8\nname\tA\tB\n";
			default -> throw new AssertionError(output);
		};
		String sql = "select * from (values (7,'A'),(8,'B')) as x(id,name)";
		try (Session session = session()) {
			submit(session, sql);
			submit(session, "/format " + output.split(":")[0]);
			submit(session, "/orientation " + output.split(":")[1]);
			Path saved = directory.resolve("saved"), dumped = directory.resolve("dumped");
			assertThat(submit(session, "/save " + saved).kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(submit(session, "/dump " + dumped + " " + sql).kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(Files.readString(saved)).isEqualTo(expected);
			assertThat(Files.readString(dumped)).isEqualTo(expected);
		}
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = {
		"/* outer /* delete */ nested */ select 'update; into' as \"insert\"",
		"with recursive x(n) as (values(1) union all select n+1 from x where n<3) select n from x",
		"with x as (select $$delete; update$$ as v) select v from x", "(select 1 as id) union (select 2)", "show work_mem", "values(1),(2)"
	})
	void dumpAcceptsReadQueriesWithoutInterpretingQuotesAndCommentsAsKeywords(String sql) throws Exception {
		try (Session session = session()) {
			submit(session, "/readonly on");
			assertThat(submit(session, "/dump " + directory.resolve("allowed") + " " + sql).kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(session.settings().readOnly()).isTrue();
		}
	}

	@Test
	void dumpDoesNotRollBackOrCommitTheUsersOpenTransaction() throws Exception {
		Path target = directory.resolve("transaction.csv");
		try (Session session = session()) {
			submit(session, "create temporary table dump_transaction(id int); begin; insert into dump_transaction values (1)");
			var band = submit(session, "/dump " + target + " select id from dump_transaction");
			assertThat(band.kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(band.text()).contains("Finish the open transaction");
			assertThat(target).doesNotExist();
			assertThat(submit(session, "select id from dump_transaction").result().rows()).containsExactly(java.util.List.of(1));
			submit(session, "commit");
			assertThat(submit(session, "/dump " + target + " select id from dump_transaction").kind()).isEqualTo(MessageBlock.Kind.INFO);
		}
	}

	@Test
	void verticalDumpPreservesCsvQuotingAndNullValuesAndCanReplaceAnExistingFile() throws Exception {
		Path target = directory.resolve("quoted.csv");
		Files.writeString(target, "old");
		try (Session session = session()) {
			submit(session, "/format csv");
			submit(session, "/orientation vertical");
			assertThat(submit(session, "/dump " + target + " select null::text as \"a,b\", E'a,\"b\\nc' as value").kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(Files.readString(target)).isEqualTo("\"a,b\",\nvalue,\"a,\"\"b\nc\"\n");
			try (var files = Files.list(directory)) { assertThat(files.map(p -> p.getFileName().toString())).noneMatch(n -> n.startsWith(".dbui-")); }
		}
	}

	@Test
	void cancellingAVerticalDumpRemovesItsColumnFilesAndKeepsTheTarget() throws Exception {
		Path target = directory.resolve("vertical-cancel.csv");
		Files.writeString(target, "original");
		try (Session session = session()) {
			submit(session, "/format csv");
			submit(session, "/orientation vertical");
			var pending = session.submit("/dump " + target + " select n, case when n>1000 then pg_sleep(0.01) end from generate_series(1,5000) n");
			// Wait for the disk transpose to start, rather than cancelling before any column file exists.
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			boolean spooling = false;
			while (!spooling && System.nanoTime() < deadline) {
				try (var files = Files.list(directory)) { spooling = files.anyMatch(p -> p.getFileName().toString().startsWith(".dbui-columns-")); }
				if (!spooling) Thread.sleep(10);
			}
			assertThat(spooling).isTrue();
			session.cancel();
			assertThat(pending.get(5, TimeUnit.SECONDS).messageBlocks().getLast().kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(Files.readString(target)).isEqualTo("original");
			try (var files = Files.list(directory)) { assertThat(files.map(p -> p.getFileName().toString())).noneMatch(n -> n.startsWith(".dbui-")); }
		}
	}

	@Test
	void anUnexpectedDumpFailureShowsAnErrorAndLogsTheTraceWithoutEndingTheSession() throws Exception {
		var real = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
		var boundary = (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[] { java.sql.Connection.class }, (proxy, method, args) -> {
				try {
					Object value = method.invoke(real, args);
					if (method.getName().equals("createStatement")) return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
						new Class<?>[] { java.sql.Statement.class }, (p, m, a) -> {
							if (m.getName().equals("execute") && a[0].toString().contains("__dump_fail")) throw new IllegalStateException("synthetic JDBC boundary failure");
							try { return m.invoke(value, a); }
							catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
						});
					return value;
				}
				catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
			});
		Path target = directory.resolve("runtime.csv");
		try (Session session = new Session(boundary, SessionDirectory.create(directory.resolve("runtime-state")))) {
			var band = submit(session, "/dump " + target + " select 1 as __dump_fail");
			assertThat(band.kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(band.text()).contains("Unexpected error", "synthetic JDBC boundary failure").doesNotContain("IllegalStateException");
			assertThat(target).doesNotExist();
			assertThat(Files.readString(session.logFile())).contains("IllegalStateException", "synthetic JDBC boundary failure");
			assertThat(submit(session, "select 42").result().rows()).containsExactly(java.util.List.of(42));
		}
	}

	@Test
	void connectionLossDuringDumpKeepsTheFileAndReconnectsOnlyForTheNextInput() throws Exception {
		var parameters = new com.datev.dbui.config.ConnectionParameters(POSTGRES.getJdbcUrl(), java.util.Map.of(
			"user", POSTGRES.getUsername(), "password", POSTGRES.getPassword()), false);
		var context = new SessionContext(directory.resolve("reconnect-state"), com.datev.dbui.config.Settings.defaults(), parameters, null, name -> null);
		Path target = directory.resolve("connection.csv");
		Files.writeString(target, "original");
		try (Session session = Session.connect(SessionDirectory.create(context.stateBase()), context)) {
			Object pid = submit(session, "select pg_backend_pid()").result().rows().getFirst().getFirst();
			var pending = session.submit("/dump " + target + " select 1; select pg_sleep(30)");
			Thread.sleep(200);
			try (var admin = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()); var statement = admin.createStatement()) {
				statement.execute("select pg_terminate_backend(" + pid + ")");
			}
			var band = pending.get(5, TimeUnit.SECONDS).messageBlocks().getLast();
			assertThat(band.kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(band.text()).contains("Connection lost");
			assertThat(Files.readString(target)).isEqualTo("original");
			assertThat(session.status().connected()).isFalse();
			assertThat(submit(session, "select 42").result().rows()).containsExactly(java.util.List.of(42));
			assertThat(session.status().connected()).isTrue();
			assertThat(Files.readString(target)).isEqualTo("original");
		}
	}

	@Test
	void dumpLeavesTheSessionReadOnlyGuardInForce() throws Exception {
		try (Session session = session()) {
			submit(session, "create table dump_read_only_target(id int)");
			submit(session, "/readonly on");
			assertThat(submit(session, "/dump " + directory.resolve("read-only.csv") + " select 1 as id").kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(session.settings().readOnly()).isTrue();
			assertThat(submit(session, "insert into dump_read_only_target values (1)").text()).contains("read-only");
		}
	}

	@Test
	void dumpToAnExistingDirectoryNamesTheTargetAndRunsNothing() throws Exception {
		Path target = Files.createDirectory(directory.resolve("not-a-file"));
		try (Session session = session()) {
			submit(session, "create temporary sequence directory_probe");
			var band = submit(session, "/dump " + target + " select nextval('directory_probe')");
			assertThat(band.kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(band.text()).contains(target.toString(), "directory");
			assertThat(submit(session, "select is_called from directory_probe").result().rows()).containsExactly(java.util.List.of(false));
			assertThat(target).isDirectory();
			try (var files = Files.list(directory)) { assertThat(files.map(p -> p.getFileName().toString())).noneMatch(n -> n.startsWith(".dbui-")); }
		}
	}

	@Test
	void dumpToAMissingDirectoryFailsWithoutRunningQueriesOrLeavingTemporaryFiles() throws Exception {
		try (Session session = session()) {
			submit(session, "create temporary sequence missing_directory_probe");
			Path target = directory.resolve("missing").resolve("file.csv");
			var band = submit(session, "/dump " + target + " select nextval('missing_directory_probe')");
			assertThat(band.kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(band.text()).contains(target.toString());
			assertThat(submit(session, "select is_called from missing_directory_probe").result().rows()).containsExactly(java.util.List.of(false));
			assertThat(target).doesNotExist();
			try (var files = Files.list(directory)) { assertThat(files.map(p -> p.getFileName().toString())).noneMatch(n -> n.startsWith(".dbui-")); }
		}
	}

	@Test
	void aSlowCancelPacketDoesNotBlockStatusOrAnotherCancelRequest() throws Exception {
		var entered = new java.util.concurrent.CountDownLatch(1);
		var release = new java.util.concurrent.CountDownLatch(1);
		var real = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
		var pg = real.unwrap(org.postgresql.PGConnection.class);
		var slow = (org.postgresql.PGConnection) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[] { org.postgresql.PGConnection.class }, (proxy, method, args) -> {
				if (method.getName().equals("cancelQuery")) { entered.countDown(); release.await(5, TimeUnit.SECONDS); }
				try { return method.invoke(pg, args); }
				catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
			});
		var boundary = (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[] { java.sql.Connection.class }, (proxy, method, args) -> {
				if (method.getName().equals("unwrap") && args[0] == org.postgresql.PGConnection.class) return slow;
				try { return method.invoke(real, args); }
				catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
			});
		try (Session session = new Session(boundary, SessionDirectory.create(directory.resolve("slow-cancel-state")))) {
			var pending = session.submit("/dump " + directory.resolve("slow.csv") + " select pg_sleep(30)");
			try (var observer = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
				var activity = observer.prepareStatement("select state = 'active' and query = ? from pg_stat_activity where pid = ?")) {
				activity.setString(1, "select pg_sleep(30)");
				activity.setInt(2, pg.getBackendPID());
				long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
				boolean active = false;
				while (!active && System.nanoTime() < deadline) {
					try (var rows = activity.executeQuery()) { active = rows.next() && rows.getBoolean(1); }
					if (!active) Thread.sleep(10);
				}
				assertThat(active).as("Dump Statement is active before cancellation").isTrue();
			}
			session.cancel();
			try {
				assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
				assertThat(java.util.concurrent.CompletableFuture.supplyAsync(session::status).get(1, TimeUnit.SECONDS).running()).isTrue();
				// Check the cancel() call itself, not the asynchronous packet it returns.
				java.util.concurrent.CompletableFuture.runAsync(() -> { session.cancel(); }).get(1, TimeUnit.SECONDS);
			}
			finally { release.countDown(); }
			assertThat(pending.get(5, TimeUnit.SECONDS).messageBlocks().getLast().kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(submit(session, "select 42").result().rows()).containsExactly(java.util.List.of(42));
		}
	}

	@Test
	void dumpWithoutSqlOrEarlierSqlHasAClearErrorAndDoesNotCreateAFile() throws Exception {
		Path target = directory.resolve("missing.csv");
		try (Session session = session()) {
			assertThat(submit(session, "/dump " + target).text()).contains("No SQL input", "submit an input");
			assertThat(submit(session, "/dump " + target + " -- only a comment").kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(target).doesNotExist();
		}
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(booleans = { false, true })
	void failedOrCancelledDumpNeverPublishesPartialDataAndLeavesTheSessionUsable(boolean existing) throws Exception {
		Path target = directory.resolve("atomic.csv");
		if (existing) Files.writeString(target, "original");
		try (Session session = session()) {
			submit(session, "/format csv");
			assertThat(submit(session, "/dump " + target + " select 1 as id; select 1/0").kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertUnchanged(target, existing);
			var pending = session.submit("/dump " + target + " select 1 as id; select pg_sleep(30)");
			Thread.sleep(200);
			assertThat(session.status().running()).isTrue();
			session.cancel();
			assertThat(pending.get(5, TimeUnit.SECONDS).messageBlocks().getLast().kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertUnchanged(target, existing);
			assertThat(submit(session, "select 42 as id").result().rows()).containsExactly(java.util.List.of(42));
			try (var files = Files.list(directory)) { assertThat(files.map(p -> p.getFileName().toString())).noneMatch(n -> n.startsWith(".dbui-")); }
		}
	}

	private static void assertUnchanged(Path target, boolean existing) throws Exception {
		if (existing) assertThat(Files.readString(target)).isEqualTo("original");
		else assertThat(target).doesNotExist();
	}

	@Test
	void dumpTimeoutProtectsLaterCursorFetchesAndLeavesTheTargetUnchanged() throws Exception {
		Path target = directory.resolve("timeout.csv");
		Files.writeString(target, "original");
		try (Session session = session()) {
			submit(session, "/timeout 1");
			submit(session, "/format csv");
			var band = submit(session, "/dump " + target + " select n, case when n > 1000 then pg_sleep(0.01) end from generate_series(1,2000) n");
			assertThat(band.kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(band.text()).contains("57014");
			assertThat(Files.readString(target)).isEqualTo("original");
			assertThat(submit(session, "select 42").result().rows()).containsExactly(java.util.List.of(42));
		}
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.CsvSource({ "csv,horizontal", "json,horizontal", "json-pretty,vertical", "csv,vertical", "tsv,vertical", "table,vertical", "markdown,vertical" })
	void dumpStreamsThreeMillionRowsWithASmallHeap(String format, String orientation) throws Exception {
		Path target = directory.resolve("large." + format);
		Path log = directory.resolve("process.log");
		var command = new java.util.ArrayList<>(java.util.List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
			"-Xmx64m", "-cp", System.getProperty("java.class.path"), SaveAndDumpIT.class.getName(),
			POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(), target.toString(), format, orientation));
		Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
		try {
			assertThat(process.waitFor(90, TimeUnit.SECONDS)).isTrue();
			assertThat(process.exitValue()).withFailMessage(Files.readString(log)).isZero();
			assertThat(Files.size(target)).isGreaterThan(20_000_000);
		}
		finally { process.destroyForcibly(); }
	}

	/** Child process for the low-heap Session-seam check, not a second application entry point. */
	public static void main(String[] args) throws Exception {
		try (Session session = new Session(DriverManager.getConnection(args[0], args[1], args[2]), SessionDirectory.create(Path.of(args[3]).getParent().resolve("child-state")))) {
			submit(session, "/format " + args[4]);
			submit(session, "/orientation " + args[5]);
			var band = session.submit("/dump " + args[3] + " select generate_series(1,3000000) as id").get(80, TimeUnit.SECONDS).messageBlocks().getLast();
			if (band.kind() != MessageBlock.Kind.INFO || !band.text().contains("3000000 rows")) throw new AssertionError(band.text());
		}
	}

	@Test
	void saveWithNoOrEmptyResultWritesNothingAndKeepsAnExistingFile() throws Exception {
		Path target = directory.resolve("unchanged");
		try (Session session = session()) {
			assertThat(submit(session, "/save " + target).kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(target).doesNotExist();
			Files.writeString(target, "original");
			submit(session, "select 1 as id where false");
			assertThat(submit(session, "/save " + target).text()).contains("No non-empty Result");
			assertThat(Files.readString(target)).isEqualTo("original");
		}
	}

	@Test
	void dumpRunsAllStatementsOfTheLastSqlInputWithoutTheRowLimit() throws Exception {
		Path target = directory.resolve("all.csv");
		try (Session session = session()) {
			submit(session, "/limit 1");
			submit(session, "select generate_series(1,3) as id; /* ; */ with x as (select 'a;b' as v) select v from x");
			submit(session, "/format csv");
			var dumped = submit(session, "/dump " + target);
			assertThat(dumped.kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(dumped.text()).contains("4 rows", "all.csv");
			assertThat(Files.readString(target)).isEqualTo("id\n1\n2\n3\n\nv\na;b\n");
			assertThat(submit(session, "/save " + directory.resolve("held.csv")).text()).contains("1 rows");
			assertThat(Files.readString(directory.resolve("held.csv"))).isEqualTo("v\na;b\n");
		}
	}

	@Test
	void saveWritesTheHeldResultInTheCurrentFormatAndWarnsAboutTruncation() throws Exception {
		Path target = directory.resolve("held.json");
		try (Session session = session()) {
			submit(session, "/limit 2");
			submit(session, "select generate_series(1,3) as id");
			submit(session, "/format json");
			var saved = submit(session, "/save " + target);
			assertThat(saved.kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(saved.text()).contains("2 rows", "held.json", "truncated");
			assertThat(Files.readString(target)).isEqualTo("[\n{\"id\":1},\n{\"id\":2}\n]\n");
			assertThat(new org.json.JSONArray(Files.readString(target)).length()).isEqualTo(2);
		}
	}
}
