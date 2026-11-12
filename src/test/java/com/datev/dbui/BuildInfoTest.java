package com.datev.dbui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.Test;

class BuildInfoTest {

	@Test
	void aCleanBuildShowsTheProjectVersionAndShortCommit() {
		var properties = new Properties();
		properties.setProperty("git.build.version", "0.0.1-SNAPSHOT");
		properties.setProperty("git.commit.id.abbrev", "f9a81f5");
		properties.setProperty("git.dirty", "false");

		assertThat(new BuildInfo(properties).versionText()).isEqualTo("0.0.1-SNAPSHOT (f9a81f5)");
	}

	@Test
	void aDirtyBuildMarksTheShortCommit() {
		var properties = new Properties();
		properties.setProperty("git.build.version", "0.0.1-SNAPSHOT");
		properties.setProperty("git.commit.id.abbrev", "f9a81f5");
		properties.setProperty("git.dirty", "true");

		assertThat(new BuildInfo(properties).versionText()).isEqualTo("0.0.1-SNAPSHOT (f9a81f5-dirty)");
	}

	@Test
	void aBuildWithoutCommitInformationShowsAnUnknownCommit() {
		var properties = new Properties();
		properties.setProperty("git.build.version", "0.0.1-SNAPSHOT");
		properties.setProperty("git.dirty", "true");

		assertThat(new BuildInfo(properties).versionText()).isEqualTo("0.0.1-SNAPSHOT (unknown)");
	}

	@Test
	void aBuildWithoutThePropertiesFileShowsAnUnknownVersion() {
		assertThat(new BuildInfo(null).versionText()).isEqualTo("unknown version");
	}

	@Test
	void aBlankCommitCountsAsMissingInformation() {
		var properties = new Properties();
		properties.setProperty("git.build.version", "0.0.1-SNAPSHOT");
		properties.setProperty("git.commit.id.abbrev", " \t ");
		properties.setProperty("git.dirty", "true");

		assertThat(new BuildInfo(properties).versionText()).isEqualTo("0.0.1-SNAPSHOT (unknown)");
	}

	@Test
	void aBlankProjectVersionCountsAsMissingInformation() {
		var properties = new Properties();
		properties.setProperty("git.build.version", " \t ");
		properties.setProperty("git.commit.id.abbrev", "f9a81f5");

		assertThat(new BuildInfo(properties).versionText()).isEqualTo("unknown version");
	}

	@Test
	void theCurrentBuildFindsTheGeneratedProjectVersion() {
		String projectVersion = System.getProperty("dbui.project.version");
		assertThat(projectVersion).as("Maven project version supplied by Surefire").isNotBlank();
		assertThat(BuildInfo.current().version()).isEqualTo(projectVersion);
	}
}
