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
	void commandsWithoutArgumentsRejectExtraTextWithoutExposingInternalArgumentIndexes() {
		var registry = new SlashCommandRegistry(List.of(new SlashCommand("quit", "End the Session.", null, false,
			argument -> List.of(MessageBlock.info("ended")))));
		var error = registry.parse("/quit now").execute().getFirst();
		assertThat(error.kind()).isEqualTo(MessageBlock.Kind.ERROR);
		assertThat(error.text()).isEqualTo("Usage: /quit\nUnexpected argument: now");
	}

	@Test
	void aRequiredArgumentCannotBeOmittedOrReusedFromAnEarlierInvocation() {
		var registry = new SlashCommandRegistry(List.of(new SlashCommand("format", "Change Format.",
			SlashCommand.argument("<table|json>", "1", List.of("table", "json")), false,
			argument -> List.of(MessageBlock.info(argument)))));
		assertThat(registry.parse("/format").execute().getFirst().kind()).isEqualTo(MessageBlock.Kind.ERROR);
		assertThat(registry.parse("/format json").execute().getFirst().text()).isEqualTo("json");
		assertThat(registry.parse("/format").execute().getFirst().text()).startsWith("Usage: /format");
	}

	@Test
	void picocliRejectsInvalidArgumentValuesBeforeExecutingTheCommand() {
		var registry = new SlashCommandRegistry(List.of(new SlashCommand("orientation", "Change Orientation.",
			SlashCommand.argument("<horizontal|vertical>", "1", List.of("horizontal", "vertical"),
				value -> com.datev.dbui.Orientation.parse(value).id()), false,
			argument -> List.of(MessageBlock.info("Changed to " + argument)))));
		var invalid = registry.parse("/orientation diagonal").execute().getFirst();
		assertThat(invalid.kind()).isEqualTo(MessageBlock.Kind.ERROR);
		assertThat(invalid.text()).startsWith("Usage: /orientation").contains("horizontal", "vertical", "diagonal")
			.doesNotContain("java.lang", "cannot convert")
			.contains("\nUnknown orientation 'diagonal'. Use one of: horizontal, vertical");
		assertThat(registry.parse("/orientation VERTICAL").execute().getFirst().text()).isEqualTo("Changed to vertical");
	}

	@Test
	void helpListsEveryRegisteredCommandAndEveryInteractiveKeyBinding() {
		var registry = new SlashCommandRegistry(List.of(
			new SlashCommand("help", "List commands and keys.", null, false, argument -> List.of()),
			new SlashCommand("future", "A newly registered command.", SlashCommand.argument("<name>", "1", List.of()), false, argument -> List.of())));
		for (var command : registry.commands()) {
			assertThat(registry.help()).contains("/" + command.name(), command.help());
		}
		assertThat(registry.help()).contains("/future <name> — A newly registered command.");
		assertThat(registry.help()).contains("<name>", "Enter", "Ctrl-J", "Up", "Down", "Ctrl-R", "Ctrl-C", "Ctrl-D",
			"Shift-Tab", "Ctrl-O", "Page Up", "Page Down", "Mouse wheel", "Esc", "Left", "Right", "Home", "End",
			"Ctrl-W", "Ctrl-Backspace", "Ctrl-Delete");
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
