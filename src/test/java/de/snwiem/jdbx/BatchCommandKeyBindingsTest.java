package de.snwiem.jdbx;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import picocli.CommandLine;

class BatchCommandKeyBindingsTest {

	@ParameterizedTest
	@ValueSource(strings = {"invalid", "tab"})
	void invalidBindingsFailAtStartupBeforeConnecting(String key, @TempDir Path directory) throws Exception {
		Files.writeString(directory.resolve("config.yaml"), "key-bindings:\n  newline: " + key + "\n");
		var out = new StringWriter();
		var err = new StringWriter();
		var line = new CommandLine(new BatchCommand()).setOut(new PrintWriter(out)).setErr(new PrintWriter(err));
		assertThat(line.execute("--config-dir", directory.toString(), "-c", "select 1")).isEqualTo(1);
		assertThat(out.toString()).isEmpty();
		assertThat(err.toString()).startsWith("Error: config.yaml:").contains("newline", key)
			.doesNotContain("Exception", "Default key bindings apply");
	}
}
