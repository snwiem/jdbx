package org.syracus.jdbx.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionDirectoryTest {

	@Test
	void stateBaseOrderIsJdbxStateDirThenXdgStateHomeThenHome() {
		assertThat(SessionDirectory.stateBase(Map.of("JDBX_STATE_DIR", "/s", "XDG_STATE_HOME", "/x"), Path.of("/home/u")))
			.isEqualTo(Path.of("/s"));
		assertThat(SessionDirectory.stateBase(Map.of("XDG_STATE_HOME", "/x"), Path.of("/home/u"))).isEqualTo(Path.of("/x/jdbx"));
		assertThat(SessionDirectory.stateBase(Map.of(), Path.of("/home/u"))).isEqualTo(Path.of("/home/u/.local/state/jdbx"));
		assertThat(SessionDirectory.stateBase(Map.of("XDG_STATE_HOME", ""), Path.of("/home/u")))
			.isEqualTo(Path.of("/home/u/.local/state/jdbx"));
	}

	@Test
	void eachDirectoryGetsAUniqueIdWithTimestampAndRandomSuffix(@TempDir Path base) throws Exception {
		var first = SessionDirectory.create(base);
		var second = SessionDirectory.create(base);
		assertThat(first.id()).isNotEqualTo(second.id()).matches("\\d{8}T\\d{6}Z-[0-9a-f]{8}");
		assertThat(first.path()).isEqualTo(base.resolve("sessions").resolve(first.id())).isDirectory();
		assertThat(first.logFile()).isEqualTo(first.path().resolve("session.log"));
		assertThat(Files.exists(first.logFile())).isTrue();
	}
}
