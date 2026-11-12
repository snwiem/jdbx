package de.snwiem.jdbx.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.snwiem.jdbx.Result;
import de.snwiem.jdbx.config.Settings;

class CopyCommandTest {

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(booleans = { false, true })
	void noOrEmptyResultGivesInfoWithoutChangingTheClipboard(boolean empty) {
		var sent = new ArrayList<String>();
		var command = new CopyCommand(sent::add);
		Result result = empty ? new Result(List.of("id"), List.of()) : null;

		var feedback = command.execute(result, Settings.defaults(), null);

		assertThat(feedback.kind()).isEqualTo(MessageBlock.Kind.INFO);
		assertThat(feedback.text()).isEqualTo("No non-empty query Result is held. Nothing was sent to the terminal clipboard.");
		assertThat(sent).isEmpty();
	}

	@Test
	void refusesMoreThan64KbOfFormattedUtf8BytesWithoutSendingAnything() {
		var sent = new ArrayList<String>();
		var command = new CopyCommand(sent::add);
		// CSV: v + newline (2 bytes), 32766 two-byte characters, aa + newline (3 bytes) = 65537.
		var result = new Result(List.of("v"), List.of(List.of("é".repeat(32766) + "aa")));

		var feedback = command.execute(result, Settings.defaults().withOutput("csv", "horizontal"), null);

		assertThat(feedback.kind()).isEqualTo(MessageBlock.Kind.INFO);
		assertThat(feedback.text()).contains("64 KB", "/save", "Nothing was sent");
		assertThat(sent).isEmpty();
	}

	@Test
	void copiesATruncatedResultAndNamesItsOriginalRowLimit() {
		var sent = new ArrayList<String>();
		var command = new CopyCommand(sent::add);
		var result = new Result(List.of("id"), List.of(List.of(7), List.of(8)));

		var feedback = command.execute(result, Settings.defaults().withOutput("csv", "horizontal"), 2);

		assertThat(sent).containsExactly("\u001b]52;c;aWQKNwo4Cg==\u0007");
		assertThat(feedback.kind()).isEqualTo(MessageBlock.Kind.INFO);
		assertThat(feedback.text()).isEqualTo("Sent 2 rows to the terminal clipboard (csv), truncated at 2 rows (Row limit).");
	}

	@Test
	void acceptsExactly64KbBeforeBase64Expansion() {
		var sent = new ArrayList<String>();
		var command = new CopyCommand(sent::add);
		String value = "é".repeat(32766) + "a";
		var result = new Result(List.of("v"), List.of(List.of(value)));

		var feedback = command.execute(result, Settings.defaults().withOutput("csv", "horizontal"), null);

		assertThat(sent).hasSize(1);
		assertThat(decoded(sent.getFirst())).isEqualTo("v\n" + value + "\n");
		assertThat(feedback.text()).isEqualTo("Sent 1 rows to the terminal clipboard (csv).");
	}

	@Test
	void aClipboardWriteFailureGivesAnErrorInsteadOfSuccess() {
		var command = new CopyCommand(sequence -> { throw new java.io.IOException("terminal closed"); });

		var feedback = command.execute(new Result(List.of("id"), List.of(List.of(7))), Settings.defaults(), null);

		assertThat(feedback.kind()).isEqualTo(MessageBlock.Kind.ERROR);
		assertThat(feedback.text()).isEqualTo("Cannot copy: terminal closed");
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = {
		"table:horizontal", "table:vertical", "markdown:horizontal", "markdown:vertical",
		"csv:horizontal", "csv:vertical", "tsv:horizontal", "tsv:vertical",
		"json:horizontal", "json:vertical", "json-pretty:horizontal", "json-pretty:vertical"
	})
	void everyFormatAndOrientationUsesTheSaveTextNotScreenText(String output) {
		String expected = switch (output) {
			case "table:horizontal" -> "id | name\n---+-----\n7  | A\n8  | B\n";
			case "table:vertical" -> "id   | 7 | 8\nname | A | B\n";
			case "markdown:horizontal" -> "| id | name |\n| --- | --- |\n| 7 | A |\n| 8 | B |\n";
			case "markdown:vertical" -> "| Column | Row 1 | Row 2 |\n| --- | --- | --- |\n| id | 7 | 8 |\n| name | A | B |\n";
			case "csv:horizontal" -> "id,name\n7,A\n8,B\n";
			case "csv:vertical" -> "id,7,8\nname,A,B\n";
			case "tsv:horizontal" -> "id\tname\n7\tA\n8\tB\n";
			case "tsv:vertical" -> "id\t7\t8\nname\tA\tB\n";
			case "json:horizontal", "json:vertical" -> "[\n{\"id\":7,\"name\":\"A\"},\n{\"id\":8,\"name\":\"B\"}\n]\n";
			default -> "[\n  {\n    \"id\": 7,\n    \"name\": \"A\"\n  },\n  {\n    \"id\": 8,\n    \"name\": \"B\"\n  }\n]\n";
		};
		var sent = new ArrayList<String>();
		var result = new Result(List.of("id", "name"), List.of(List.of(7, "A"), List.of(8, "B")));

		new CopyCommand(sent::add).execute(result, Settings.defaults().withOutput(output.split(":")[0], output.split(":")[1]), null);

		assertThat(sent).hasSize(1);
		assertThat(decoded(sent.getFirst())).isEqualTo(expected);
	}

	private static String decoded(String sequence) {
		assertThat(sequence).startsWith("\u001b]52;c;").endsWith("\u0007");
		return new String(java.util.Base64.getDecoder().decode(sequence.substring(7, sequence.length() - 1)),
			java.nio.charset.StandardCharsets.UTF_8);
	}

	@Test
	void copiesTheExactCurrentFormatAndOrientationAsUtf8Osc52() {
		var sent = new ArrayList<String>();
		var command = new CopyCommand(sent::add);
		var result = new Result(List.of("id", "name"), List.of(List.of(7, "Ä"), List.of(8, "猫")));

		var feedback = command.execute(result, Settings.defaults().withOutput("csv", "vertical"), null);

		assertThat(sent).containsExactly("\u001b]52;c;aWQsNyw4Cm5hbWUsw4Qs54yrCg==\u0007");
		assertThat(feedback.kind()).isEqualTo(MessageBlock.Kind.INFO);
		assertThat(feedback.text()).isEqualTo("Sent 2 rows to the terminal clipboard (csv).");
	}
}
