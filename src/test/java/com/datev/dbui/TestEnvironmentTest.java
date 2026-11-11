package com.datev.dbui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import com.datev.dbui.config.ConfigLoader;
import com.datev.dbui.session.SessionDirectory;
import org.junit.jupiter.api.Test;

/** Build seam: default application paths must not escape into the caller's home. */
class TestEnvironmentTest {

	@Test
	void defaultConfigAndStateUseBuildDirectoriesInsteadOfTheCallersHome() {
		Path root = Path.of("target", "test-environment").toAbsolutePath();
		Path home = Path.of(System.getProperty("user.home"));

		assertThat(ConfigLoader.directory(null, System.getenv(), home)).isEqualTo(root.resolve("config"));
		assertThat(SessionDirectory.defaultStateBase()).isEqualTo(root.resolve("state"));
		assertThat(home).isEqualTo(root.resolve("home"));
		assertThat(System.getenv("HOME")).isEqualTo(root.resolve("home").toString());
		assertThat(System.getenv("XDG_CONFIG_HOME")).isEqualTo(root.resolve("xdg-config").toString());
		assertThat(System.getenv("XDG_STATE_HOME")).isEqualTo(root.resolve("xdg-state").toString());
	}
}
