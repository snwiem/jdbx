package de.snwiem.jdbx.tui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class SessionLoggingTest {

	@Test
	void logOutputGoesToTheSessionFileOnlyWhileAttached(@TempDir Path directory) throws Exception {
		Path file = directory.resolve("session.log");
		try (var logging = new SessionLogging(file)) {
			LoggerFactory.getLogger("some.library").warn("during session");
		}
		LoggerFactory.getLogger("some.library").warn("after session");
		assertThat(Files.readString(file)).contains("during session").doesNotContain("after session");
	}
}
