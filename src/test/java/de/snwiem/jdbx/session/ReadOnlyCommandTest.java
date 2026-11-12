package de.snwiem.jdbx.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReadOnlyCommandTest {

	@Test
	void parsesTheSlashCommandAndLeavesOtherInputAlone() {
		assertThat(ReadOnlyCommand.parse("/readonly")).isEqualTo(ReadOnlyCommand.Kind.SHOW);
		assertThat(ReadOnlyCommand.parse("  /readonly on ")).isEqualTo(ReadOnlyCommand.Kind.ON);
		assertThat(ReadOnlyCommand.parse("/readonly off")).isEqualTo(ReadOnlyCommand.Kind.OFF);
		assertThat(ReadOnlyCommand.parse("/readonly maybe")).isEqualTo(ReadOnlyCommand.Kind.USAGE);
		assertThat(ReadOnlyCommand.parse("/readonly on off")).isEqualTo(ReadOnlyCommand.Kind.USAGE);
		assertThat(ReadOnlyCommand.parse("/readonlyx")).isEqualTo(ReadOnlyCommand.Kind.NOT_MINE);
		assertThat(ReadOnlyCommand.parse("select 1")).isEqualTo(ReadOnlyCommand.Kind.NOT_MINE);
	}
}
