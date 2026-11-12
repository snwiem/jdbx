package org.syracus.jdbx;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;

class BatchCommandVersionTest {

	@Test
	void versionPrintsTheBuildTextAndExitsSuccessfullyWithoutAConnection() {
		var out = new StringWriter();
		var err = new StringWriter();
		var line = new CommandLine(new BatchCommand()).setOut(new PrintWriter(out)).setErr(new PrintWriter(err));

		// No driver accepts this URL: executing the Batch command could not connect successfully.
		int exitCode = line.execute("--version", "--url", "jdbc:no-driver:version-test", "--user", "ignored",
			"--password", "ignored", "-c", "select 1");

		assertThat(exitCode).isZero();
		assertThat(out.toString()).isEqualTo("dbui " + BuildInfo.current().versionText() + System.lineSeparator());
		assertThat(err.toString()).isEmpty();
	}
}
