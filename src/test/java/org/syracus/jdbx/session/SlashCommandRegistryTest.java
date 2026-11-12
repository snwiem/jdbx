package org.syracus.jdbx.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.syracus.jdbx.Orientation;
import org.syracus.jdbx.config.KeyBindings;
import org.syracus.jdbx.config.Settings;

class SlashCommandRegistryTest {

	@Test
	void slashArgumentsAreLiteralTextNotShellArgumentsOrArgumentFiles(@TempDir Path dir) throws Exception {
		var registry = new SlashCommandRegistry(List.of(new SlashCommand("describe", "Describe a table.",
			SlashCommand.argument("<table>", "1", List.of()), true,
			argument -> List.of(MessageBlock.info(argument)))));
		var file = Files.writeString(dir.resolve("args"), "not_the_name");
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
				value -> Orientation.parse(value).id()), false,
			argument -> List.of(MessageBlock.info("Changed to " + argument)))));
		var invalid = registry.parse("/orientation diagonal").execute().getFirst();
		assertThat(invalid.kind()).isEqualTo(MessageBlock.Kind.ERROR);
		assertThat(invalid.text()).startsWith("Usage: /orientation").contains("horizontal", "vertical", "diagonal")
			.doesNotContain("java.lang", "cannot convert")
			.contains("\nUnknown orientation 'diagonal'. Use one of: horizontal, vertical");
		assertThat(registry.parse("/orientation VERTICAL").execute().getFirst().text()).isEqualTo("Changed to vertical");
	}

	@Test
	void helpShowsConfiguredEditorBindingsBeforeConnecting(@TempDir Path dir) throws Exception {
		var bindings = KeyBindings.from(Map.of("newline", "alt-n", "orientation-cycle", "ctrl-t",
			"orientation-fallback", "alt-o", "complete", "ctrl-space", "complete-alternate", "alt-c"));
		var settings = Settings.defaults().withKeyBindings(bindings);
		try (var session = new Session(SessionDirectory.create(dir), settings)) {
			var update = session.submit("/help keys").get();
			assertThat(update.messageBlocks().getLast().text())
				.contains("Alt-N", "Ctrl-T / Alt-O", "Ctrl-Space / Alt-C", "Completion Tab / Enter")
				.doesNotContain("Ctrl-J                    Insert a newline", "Shift-Tab / Ctrl-O", "Tab / Alt-/");
		}
	}

	@Test
	void helpDoesNotDescribeShiftEnterAsANewlineWhenItRequestsCompletion() {
		var registry = new SlashCommandRegistry(List.of());
		var keys = KeyBindings.from(Map.of("complete", "shift-enter"));
		assertThat(registry.help(keys)).contains("Shift-Enter / Alt-/")
			.doesNotContain("Shift-Enter               Insert a newline");
	}

	@Test
	void helpExplainsLegacyControlAliasesAndBracketedPaste() {
		var help = new SlashCommandRegistry(List.of()).help();
		assertThat(help).contains("Ctrl-H = Backspace", "Ctrl-I = Tab", "Ctrl-J = LF", "Ctrl-M = Enter",
			"Kitty", "dbui preserves bare LF as Ctrl-J", "bracketed paste");
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
