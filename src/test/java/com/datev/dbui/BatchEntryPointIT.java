package com.datev.dbui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Batch entry point seam: arguments, stdout, stderr and exit code. */
@Testcontainers
class BatchEntryPointIT {

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

	record Outcome(int exitCode, String stdout, String stderr) {
	}

	private static Outcome run(String... args) {
		return runWithStdin("", args);
	}

	private static Outcome runWithStdin(String stdin, String... args) {
		java.io.InputStream originalIn = System.in;
		PrintStream originalOut = System.out;
		PrintStream originalErr = System.err;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		try {
			System.setIn(new java.io.ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)));
			System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
			System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
			int exitCode = DbuiApplication.run(args);
			return new Outcome(exitCode, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
		}
		finally {
			System.setIn(originalIn);
			System.setOut(originalOut);
			System.setErr(originalErr);
		}
	}

	private static String[] connection(String... more) {
		String[] base = { "--host", POSTGRES.getHost(), "--port", String.valueOf(POSTGRES.getMappedPort(5432)),
				"--db", POSTGRES.getDatabaseName(), "--user", POSTGRES.getUsername(), "--password",
				POSTGRES.getPassword() };
		String[] all = new String[base.length + more.length];
		System.arraycopy(base, 0, all, 0, base.length);
		System.arraycopy(more, 0, all, base.length, more.length);
		return all;
	}

	@Test
	void runsOneStatementAndPrintsTableOnStdout() {
		Outcome outcome = run(connection("-c", "select 1 as id, 'alice' as name, null as nothing"));

		assertThat(outcome.stdout()).isEqualTo("""
				id | name  | nothing
				---+-------+--------
				1  | alice | NULL
				""");
		assertThat(outcome.exitCode()).isZero();
	}

	@Test
	void connectsWithApplicationNameDbui() {
		Outcome outcome = run(connection("-c", "show application_name"));

		assertThat(outcome.stdout()).contains("dbui");
		assertThat(outcome.exitCode()).isZero();
	}

	@Test
	void reportsMissingConnectionValuesOnStderr() {
		Outcome outcome = run("--host", "localhost", "-c", "select 1");

		assertThat(outcome.exitCode()).isNotZero();
		assertThat(outcome.stdout()).isEmpty();
		assertThat(outcome.stderr()).contains("--db").contains("--user").contains("--password");
	}

	@Test
	void failsWithoutInputWhenStdinIsNoTerminal() throws Exception {
		var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
			"-cp", System.getProperty("java.class.path"), DbuiApplication.class.getName()));
		command.addAll(List.of(connection()));
		Process process = new ProcessBuilder(command).start();
		try {
			process.getOutputStream().close();
			assertThat(process.waitFor(15, TimeUnit.SECONDS)).isTrue();
			assertThat(process.exitValue()).isEqualTo(2);
			assertThat(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEmpty();
			assertThat(new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8)).contains("No input");
		}
		finally {
			process.destroyForcibly();
		}
	}

	@Test
	void reportsSqlErrorOnStderr() {
		Outcome outcome = run(connection("-c", "select * from no_such_table"));

		assertThat(outcome.exitCode()).isNotZero();
		assertThat(outcome.stdout()).isEmpty();
		assertThat(outcome.stderr()).contains("no_such_table");
	}

	@Test
	void connectsThroughProfileAndNeverPromptsForMissingPassword(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
			throws Exception {
		java.nio.file.Files.writeString(dir.resolve("profiles.yaml"), """
				profiles:
				  it:
				    host: %s
				    port: %d
				    database: %s
				    user: %s
				    password: %s
				    schema: pg_catalog
				  nopass:
				    host: %s
				    port: %d
				    database: %s
				    user: %s
				""".formatted(POSTGRES.getHost(), POSTGRES.getMappedPort(5432), POSTGRES.getDatabaseName(),
				POSTGRES.getUsername(), POSTGRES.getPassword(), POSTGRES.getHost(), POSTGRES.getMappedPort(5432),
				POSTGRES.getDatabaseName(), POSTGRES.getUsername()));

		Outcome ok = run("--config-dir", dir.toString(), "-p", "it", "-c", "show search_path");
		assertThat(ok.exitCode()).isZero();
		assertThat(ok.stdout()).contains("pg_catalog");

		Outcome missing = run("--config-dir", dir.toString(), "-p", "nopass", "-c", "select 1");
		assertThat(missing.exitCode()).isNotZero();
		assertThat(missing.stdout()).isEmpty();
		assertThat(missing.stderr()).contains("password").contains("never prompts");
	}

	private static final String ONE = "id\n--\n1\n";

	private static final String TWO = "id\n--\n2\n";

	@Test
	void runsRepeatedCommandsInOrderSeparatedByBlankLine() {
		Outcome outcome = run(connection("-c", "select 1 as id", "-c", "select 2 as id"));

		assertThat(outcome.stdout()).isEqualTo(ONE + "\n" + TWO);
		assertThat(outcome.exitCode()).isZero();
	}

	@Test
	void runsSeveralStatementsOfOneCommand() {
		Outcome outcome = run(connection("-c", "select 1 as id; select 'a;b' as id;"));

		assertThat(outcome.stdout()).isEqualTo(ONE + "\nid\n---\na;b\n");
	}

	@Test
	void readsStatementsFromFile(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
		java.nio.file.Path file = dir.resolve("in.sql");
		java.nio.file.Files.writeString(file, "select 1 as id;\nselect 2 as id;\n");

		Outcome outcome = run(connection("-f", file.toString()));

		assertThat(outcome.stdout()).isEqualTo(ONE + "\n" + TWO);
		assertThat(outcome.exitCode()).isZero();
	}

	@Test
	void readsStdinWhenNoOtherInputIsGiven() {
		Outcome outcome = runWithStdin("select 1 as id; select 2 as id", connection());

		assertThat(outcome.stdout()).isEqualTo(ONE + "\n" + TWO);
		assertThat(outcome.exitCode()).isZero();
	}

	@Test
	void dashFileReadsStdin() {
		Outcome outcome = runWithStdin("select 1 as id", connection("-f", "-"));

		assertThat(outcome.stdout()).isEqualTo(ONE);
	}

	@Test
	void firstErrorStopsTheRun() {
		Outcome outcome = run(connection("-c", "select 1 as id; select * from no_such_table; select 2 as id"));

		assertThat(outcome.stdout()).isEqualTo(ONE);
		assertThat(outcome.stderr()).contains("no_such_table");
		assertThat(outcome.exitCode()).isNotZero();
	}

	@Test
	void errorInFirstCommandSkipsLaterCommands() {
		Outcome outcome = run(connection("-c", "select * from no_such_table", "-c", "select 2 as id"));

		assertThat(outcome.stdout()).isEmpty();
		assertThat(outcome.exitCode()).isNotZero();
	}

	@Test
	void writesCsvWithTimestampAndJsonbColumn() {
		Outcome outcome = run(connection("--format", "csv", "-c",
				"select 1 as id, null as nothing, '{\"a\": [1, 2]}'::jsonb as doc, timestamptz '2024-03-05 10:15:30+00' as ts"));

		String expectedTs = java.time.Instant.parse("2024-03-05T10:15:30Z").atZone(java.time.ZoneId.systemDefault())
				.toOffsetDateTime().format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);
		assertThat(outcome.stdout()).isEqualTo("id,nothing,doc,ts\n1,,\"{\"\"a\"\": [1, 2]}\",%s\n".formatted(expectedTs));
		assertThat(outcome.exitCode()).isZero();
	}

	@Test
	void showsDateTimeTimestampAsStoredAndTimestamptzInConfiguredTimeZone(@org.junit.jupiter.api.io.TempDir Path dir)
			throws Exception {
		java.nio.file.Files.writeString(dir.resolve("config.yaml"), "timezone: Asia/Tokyo\n");

		Outcome outcome = run(connection("--config-dir", dir.toString(), "--format", "csv", "-c", """
				select date '2024-03-05' as d, time '23:15:30' as t, timestamp '2024-03-05 23:15:30' as ts,
				timestamptz '2024-03-05 23:15:30+00' as tstz"""));

		assertThat(outcome.stderr()).isEmpty();
		assertThat(outcome.stdout())
				.isEqualTo("d,t,ts,tstz\n2024-03-05,23:15:30,2024-03-05T23:15:30,2024-03-06T08:15:30+09:00\n");
	}

	@Test
	void formatComesFromSettingsAndOptionWins(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
		java.nio.file.Files.writeString(dir.resolve("config.yaml"), "format: csv\n");

		Outcome fromSettings = run(connection("--config-dir", dir.toString(), "-c", "select 1 as id"));
		Outcome fromOption = run(connection("--config-dir", dir.toString(), "--format", "tsv", "-c", "select 1 as id, 2 as b"));

		assertThat(fromSettings.stdout()).isEqualTo("id\n1\n");
		assertThat(fromOption.stdout()).isEqualTo("id\tb\n1\t2\n");
	}

	@Test
	void markersAndMaxWidthComeFromSettings(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
		java.nio.file.Files.writeString(dir.resolve("config.yaml"), """
				null-marker: "<null>"
				multiline-marker: "<nl>"
				max-width: 5
				""");

		Outcome outcome = run(connection("--config-dir", dir.toString(), "-c",
				"select null as a, E'x\\ny' as b, 'abcdefgh' as c"));

		assertThat(outcome.stdout()).contains("<null>").contains("x<nl…").contains("abcd…").doesNotContain("abcde");
	}

	@Test
	void batchModeHasNoRowLimitByDefaultEvenWithRowLimitInSettings(@org.junit.jupiter.api.io.TempDir Path dir)
			throws Exception {
		java.nio.file.Files.writeString(dir.resolve("config.yaml"), "row-limit: 3\n");

		Outcome outcome = run(connection("--config-dir", dir.toString(), "--format", "csv", "-c",
				"select * from generate_series(1, 10) as n"));

		assertThat(outcome.stdout().lines().count()).isEqualTo(11);
		assertThat(outcome.stderr()).isEmpty();
	}

	@Test
	void limitCutsEachResultAndNotesTruncationOnStderr() {
		Outcome outcome = run(connection("--limit", "3", "--format", "csv", "-c",
				"select * from generate_series(1, 10) as n; select * from generate_series(1, 3) as m"));

		assertThat(outcome.stdout()).isEqualTo("n\n1\n2\n3\n\nm\n1\n2\n3\n");
		assertThat(outcome.stderr()).isEqualTo("truncated at 3 rows\n");
		assertThat(outcome.exitCode()).isZero();
	}

	@Test
	void limitMustBePositive() {
		Outcome outcome = run(connection("--limit", "0", "-c", "select 1"));

		assertThat(outcome.exitCode()).isNotZero();
		assertThat(outcome.stdout()).isEmpty();
	}

	@Test
	void writesJsonWithJsonbEmbeddedAsRealJson() {
		Outcome outcome = run(connection("--format", "json", "-c",
				"select 1 as id, null as nothing, '{\"a\": [1, 2]}'::jsonb as doc, true as ok, 'x'::bytea as bin"));

		assertThat(outcome.stdout()).isEqualTo("""
				[
				{"id":1,"nothing":null,"doc":{"a": [1, 2]},"ok":true,"bin":"\\\\x78"}
				]
				""");
		assertThat(outcome.exitCode()).isZero();
	}

	@Test
	void rejectsUnknownFormat() {
		Outcome outcome = run(connection("--format", "xml", "-c", "select 1"));

		assertThat(outcome.exitCode()).isNotZero();
		assertThat(outcome.stderr()).contains("xml");
	}

	@Test
	void helpListsFormatOption() {
		Outcome outcome = run("--help");

		assertThat(outcome.stdout()).contains("--format");
	}

	@Test
	void writesVerticalTable() {
		Outcome outcome = run(connection("--orientation", "vertical", "-c", "select 1 as id, 'alice' as name, null as nothing"));

		assertThat(outcome.stdout()).isEqualTo("id      | 1\nname    | alice\nnothing | NULL\n");
		assertThat(outcome.exitCode()).isZero();
	}

	@Test
	void writesVerticalCsvCaseInsensitive() {
		Outcome outcome = run(connection("--format", "csv", "--orientation", "VERTICAL", "-c", "select 1 as id, 'alice' as name"));

		assertThat(outcome.stdout()).isEqualTo("id,1\nname,alice\n");
	}

	@Test
	void usesOrientationFromSettingsAndOptionWins(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
		java.nio.file.Files.writeString(dir.resolve("config.yaml"), "orientation: vertical\n");

		Outcome fromSettings = run(connection("--config-dir", dir.toString(), "--format", "csv", "-c", "select 1 as id"));
		Outcome overridden = run(connection("--config-dir", dir.toString(), "--format", "csv", "--orientation", "horizontal",
				"-c", "select 1 as id"));

		assertThat(fromSettings.stdout()).isEqualTo("id,1\n");
		assertThat(overridden.stdout()).isEqualTo("id\n1\n");
	}

	@Test
	void rejectsUnknownOrientation() {
		Outcome outcome = run(connection("--orientation", "diagonal", "-c", "select 1"));

		assertThat(outcome.exitCode()).isNotZero();
		assertThat(outcome.stdout()).isEmpty();
		assertThat(outcome.stderr()).contains("diagonal").contains("horizontal, vertical");
	}

	@Test
	void helpListsOrientationOption() {
		assertThat(run("--help").stdout()).contains("--orientation");
	}

	private static final int MANY_ROWS = 3_000_000;

	/** Runs Batch mode in a JVM with a 64 MB heap; counts stdout lines without storing them. */
	private static long countLines(String... more) throws Exception {
		var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
				"-Xmx64m", "-cp", System.getProperty("java.class.path"), DbuiApplication.class.getName()));
		command.addAll(List.of(connection(more)));
		Process process = new ProcessBuilder(command).redirectError(new java.io.File("target/mem-stderr.txt")).start();
		try {
			process.getOutputStream().close();
			long lines = 0;
			byte[] buffer = new byte[65536];
			try (var in = process.getInputStream()) {
				for (int n; (n = in.read(buffer)) > 0;) {
					for (int i = 0; i < n; i++) {
						if (buffer[i] == '\n') {
							lines++;
						}
					}
				}
			}
			assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
			assertThat(process.exitValue()).isZero();
			return lines;
		}
		finally {
			process.destroyForcibly();
		}
	}

	private static final String MANY = "select g, md5(g::text) from generate_series(1, " + MANY_ROWS + ") g";

	@Test
	void writesMillionsOfRowsAsCsvWithSmallHeap() throws Exception {
		assertThat(countLines("--format", "csv", "-c", MANY)).isEqualTo(MANY_ROWS + 1L);
	}

	@Test
	void writesMillionsOfRowsAsJsonWithSmallHeap() throws Exception {
		assertThat(countLines("--format", "json", "-c", MANY)).isEqualTo(MANY_ROWS + 2L);
	}

	@Test
	void configuredTimeoutAlsoStopsASlowCursorFetchAfterTheFirst1000Rows(
			@org.junit.jupiter.api.io.TempDir Path config) throws Exception {
		java.nio.file.Files.writeString(config.resolve("config.yaml"), "timeout: 1\n");
		long started = System.nanoTime();
		Outcome outcome = run(connection("--config-dir", config.toString(), "--format", "csv", "-c",
			"select case when g = 1001 then pg_sleep(5) end as value from generate_series(1,2000) g"));
		assertThat(outcome.exitCode()).isNotZero();
		assertThat(outcome.stderr()).contains("canceling statement due to statement timeout");
		assertThat(java.time.Duration.ofNanos(System.nanoTime() - started)).isLessThan(java.time.Duration.ofSeconds(4));
	}

	@Test
	void configuredStreamingTimeoutDoesNotRelaxAShorterServerTimeout(
			@org.junit.jupiter.api.io.TempDir Path config) throws Exception {
		java.nio.file.Files.writeString(config.resolve("config.yaml"), "timeout: 7\n");
		Outcome outcome = run(connection("--config-dir", config.toString(), "-c",
			"set statement_timeout = '100ms'; show statement_timeout"));
		assertThat(outcome.exitCode()).as(outcome.stderr()).isZero();
		assertThat(outcome.stdout()).contains("100ms");
	}

	@Test
	void keepsAutocommitForStatementsAfterAStreamedQuery() {
		Outcome first = run(connection("-c", "select 1; create table stream_commit(a int); insert into stream_commit values (7)"));
		Outcome second = run(connection("-c", "vacuum; select a from stream_commit"));

		assertThat(first.exitCode()).isZero();
		assertThat(second.exitCode()).as(second.stderr()).isZero();
		assertThat(second.stdout()).contains("7");
	}
}
