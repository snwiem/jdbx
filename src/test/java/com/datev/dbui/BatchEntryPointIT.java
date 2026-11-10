package com.datev.dbui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

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
}
