package org.syracus.jdbx.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class FileCommandLineTest {

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void plainAndQuotedFileNamesAreLiteral(boolean dump) {
		assertThat(FileCommandLine.parse("  out.csv  ", dump)).isEqualTo(new FileCommandLine(Path.of("out.csv"), ""));
		assertThat(FileCommandLine.parse("'my result.csv'", dump)).isEqualTo(new FileCommandLine(Path.of("my result.csv"), ""));
		assertThat(FileCommandLine.parse("\"my result.csv\"", dump)).isEqualTo(new FileCommandLine(Path.of("my result.csv"), ""));
		assertThat(FileCommandLine.parse("~/out.csv", dump).file()).isEqualTo(Path.of("~/out.csv"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "out.csv", "'my result.csv'", "\"my result.csv\"" })
	void dumpKeepsSqlQuotesAndInternalWhitespaceUntouched(String name) {
		var command = FileCommandLine.parse(name + "  select 'a  b' as \"x y\"; -- trailing", true);
		assertThat(command.sql()).isEqualTo("select 'a  b' as \"x y\"; -- trailing");
	}

	@ParameterizedTest
	@MethodSource("invalidInputs")
	void invalidFileNamesHaveClearErrors(String input, boolean dump, String error) {
		assertThatIllegalArgumentException().isThrownBy(() -> FileCommandLine.parse(input, dump)).withMessageContaining(error);
	}

	static Stream<Arguments> invalidInputs() {
		return Stream.of(
			Arguments.of("  ", false, "A file name is required"),
			Arguments.of("", true, "A file name is required"),
			Arguments.of("'unclosed", true, "not closed"),
			Arguments.of("\"unclosed", false, "not closed"),
			Arguments.of("'out.csv'text", true, "Separate the file name"),
			Arguments.of("\"out.csv\"text", false, "Separate the file name"),
			Arguments.of("''", true, "Supply one file name"),
			Arguments.of("\" \"", false, "Supply one file name"),
			Arguments.of("'out.csv' now", false, "Supply one file name"),
			Arguments.of("\"out.csv\" now", false, "Supply one file name")
		);
	}

	@Test
	void saveRejectsTrailingTextAfterAnUnquotedFileName() {
		assertThatIllegalArgumentException().isThrownBy(() -> FileCommandLine.parse("out.csv now", false))
			.withMessageContaining("Supply one file name");
	}
}
