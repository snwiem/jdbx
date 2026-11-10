package com.datev.dbui.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class SlashCommandRegistryTest {

	@Test
	void slashArgumentsAreLiteralTextNotShellArgumentsOrArgumentFiles(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
		var registry = new SlashCommandRegistry(List.of(new SlashCommand("describe", "Describe a table.",
			SlashCommand.argument("<table>", "1", List.of()), true,
			argument -> List.of(MessageBlock.info(argument)))));
		var file = java.nio.file.Files.writeString(dir.resolve("args"), "not_the_name");
		for (String argument : List.of("@" + file, "--", "-name", "\"Mixed Schema\".\"Order  Items\"", "\"a\"\"b\"", "\"open")) {
			assertThat(registry.parse("/describe " + argument).execute().getFirst().text()).isEqualTo(argument);
		}
	}

	@Test
	void aRegisteredCommandParsesAnArgumentAndAppearsInHelpAndCompletion() {
		var registry = new SlashCommandRegistry(List.of(new SlashCommand("format", "Change the Format.",
			SlashCommand.argument("<table|json>", "1", List.of("table", "json")), false,
			argument -> List.of(MessageBlock.info("Format: " + argument)))));

		assertThat(registry.parse(" /format json ").execute().getFirst().text()).isEqualTo("Format: json");
		assertThat(registry.help()).contains("/format", "<table|json>", "Change the Format.");
		assertThat(registry.completionCandidates("format")).containsExactly("table", "json");
	}
}
