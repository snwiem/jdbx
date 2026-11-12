package org.syracus.jdbx.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.syracus.jdbx.config.KeyBindings;
import org.syracus.jdbx.session.HelpCatalog.Command;

class HelpCatalogTest {

	static final List<Command> COMMANDS = List.of(
		new Command("format", "/format <table|json>", "Change the Format for new Results.", "Query and results"),
		new Command("describe", "/describe [<schema|table>]", "List schemas, tables and views, or describe a table.", "Schema"),
		new Command("history", "/history [<number>|clear]", "List the input history, load an entry or clear it.", "History and Sessions"),
		new Command("help", "/help [commands|keys|<word>]", "Show help.", "General"),
		new Command("quit", "/quit", "End the Session.", "General"),
		new Command("future", "/future", "A newly registered command.", "Other"));

	private static HelpCatalog catalog(KeyBindings keys) {
		return new HelpCatalog(COMMANDS, keys, "0.0.1-SNAPSHOT (f9a81f5)");
	}

	@Test
	void theOverviewStartsWithTheVersionAndListsEveryCommandNameUnderItsGroupAndFiveEssentialKeys() {
		// Issue 0040 replaces the generic title with the supplied build version; the overview body stays unchanged.
		assertThat(catalog(KeyBindings.defaults()).topic("")).isEqualTo(String.join("\n",
			"dbui 0.0.1-SNAPSHOT (f9a81f5). Use /help commands, /help keys, or /help <word>.",
			"",
			"Query and results     /format",
			"Schema                /describe",
			"History and Sessions  /history",
			"General               /help /quit",
			"Other                 /future",
			"",
			"Essential keys",
			"Enter                     Send input",
			"Ctrl-J                    Insert a newline",
			"Tab                       Request Completion",
			"Ctrl-R                    Search history",
			"Ctrl-C                    Cancel a running Statement, or clear the input"));
	}

	@Test
	void theOverviewShowsTheConfiguredKeys() {
		var keys = KeyBindings.from(Map.of("newline", "alt-n", "complete", "ctrl-space"));
		assertThat(catalog(keys).topic("  ")).contains("Alt-N                     Insert a newline")
			.contains("Ctrl-Space                Request Completion").doesNotContain("Ctrl-J");
	}

	@Test
	void commandsListsAllCommandsUnderGroupHeadings() {
		assertThat(catalog(KeyBindings.defaults()).topic("commands")).isEqualTo(String.join("\n",
			"Slash commands",
			"",
			"Query and results",
			"  /format <table|json> — Change the Format for new Results.",
			"",
			"Schema",
			"  /describe [<schema|table>] — List schemas, tables and views, or describe a table.",
			"",
			"History and Sessions",
			"  /history [<number>|clear] — List the input history, load an entry or clear it.",
			"",
			"General",
			"  /help [commands|keys|<word>] — Show help.",
			"  /quit — End the Session.",
			"",
			"Other",
			"  /future — A newly registered command."));
	}

	@Test
	void keysListsAllKeyRowsUnderGroupHeadingsThenTheLegacyNotes() {
		String keys = catalog(KeyBindings.defaults()).topic("keys");
		assertThat(keys).startsWith("Key bindings\n\nEditing\n  Enter                     Send input; accept a Ctrl-R match without sending\n"
			+ "  Ctrl-J                    Insert a newline; some modified keys need Kitty keyboard support\n");
		var headings = List.of("\nEditing\n", "\nHistory\n", "\nCompletion\n", "\nCopy and paste\n", "\nResults and screen\n", "\nDialogs\n", "\nLegacy terminals:");
		int last = -1;
		for (String heading : headings) {
			int at = keys.indexOf(heading);
			assertThat(at).as(heading).isGreaterThan(last);
			last = at;
		}
		assertThat(section(keys, "History", "Completion")).containsExactly(
			"  Up / Down                 History at the first / last editor line; otherwise move the cursor",
			"  Ctrl-R                    Search history; press again for an older match",
			"  Esc                       Cancel history search and restore the draft");
		assertThat(section(keys, "Completion", "Copy and paste")).containsExactly(
			"  Tab / Alt-/               Request Completion (primary / fallback)",
			"  Completion Up / Down      Select a candidate",
			"  Completion Tab / Enter    Accept a candidate without sending input",
			"  Completion Esc            Close Completion");
		assertThat(section(keys, "Copy and paste", "Results and screen")).containsExactly(
			"  Shift + mouse drag        Select text; dbui captures the mouse. Some terminals use another key (iTerm2: Option)",
			"  Ctrl-Shift-C              Copy the selection. A terminal key, not a dbui key (macOS: Cmd-C)",
			"  Ctrl-Shift-V              Paste. A terminal key; dbui reads it as a bracketed paste and never sends it",
			"  /copy                     Copy the held query Result to the clipboard (a command, not a key)");
		assertThat(section(keys, "Results and screen", "Dialogs")).containsExactly(
			"  Ctrl-C                    Cancel a running Statement, cancel search, or clear idle input; never exit",
			"  Ctrl-D                    Ignore; never exit (use /quit)",
			"  Shift-Tab / Ctrl-O        Cycle Orientation (primary / fallback)",
			"  Page Up / Page Down       Scroll the Transcript",
			"  Mouse wheel               Scroll the Transcript");
		assertThat(section(keys, "Dialogs", "Legacy terminals:")).hasSize(7).contains(
			"  Dialog Tab / Shift-Tab    Move between fields and Save (not Orientation)",
			"  Confirmation y / other    Confirm Profile removal / cancel it");
		assertThat(keys).endsWith("Legacy terminals: Ctrl-H = Backspace, Ctrl-I = Tab, Ctrl-J = LF, Ctrl-M = Enter.\n"
			+ "Without Kitty these are not distinct keys; dbui preserves bare LF as Ctrl-J.\n"
			+ "If the terminal sends Enter instead, Ctrl-J / Ctrl-M can send input. Use a distinct key\n"
			+ "or bracketed paste for multi-line input on legacy terminals.");
	}

	@Test
	void shiftEnterIsDescribedAsANewlineOnlyWhenNoActionUsesIt() {
		assertThat(catalog(KeyBindings.defaults()).topic("keys"))
			.contains("  Shift-Enter               Insert a newline when the terminal distinguishes it from Enter");
		var keys = catalog(KeyBindings.from(Map.of("complete", "shift-enter"))).topic("keys");
		assertThat(keys).contains("Shift-Enter / Alt-/").doesNotContain("Shift-Enter               Insert a newline");
	}

	@Test
	void aWordFiltersCommandsAndKeyRowsByNameAndDescriptionKeepingGroupHeadings() {
		assertThat(catalog(KeyBindings.defaults()).topic("history")).isEqualTo(String.join("\n",
			"Slash commands",
			"",
			"History and Sessions",
			"  /history [<number>|clear] — List the input history, load an entry or clear it.",
			"",
			"Key bindings",
			"",
			"History",
			"  Up / Down                 History at the first / last editor line; otherwise move the cursor",
			"  Ctrl-R                    Search history; press again for an older match",
			"  Esc                       Cancel history search and restore the draft",
			"",
			"Dialogs",
			"  Password Enter            Connect with the masked password; never store it in history"));
	}

	@Test
	void copyAndPasteAreFoundByWordAndNameTheTerminalKeys() {
		var catalog = catalog(KeyBindings.defaults());
		assertThat(catalog.topic("paste")).contains("Copy and paste", "Ctrl-Shift-V").doesNotContain("Ctrl-Shift-C");
		assertThat(catalog.topic("ctrl-shift-c")).contains("Ctrl-Shift-C              Copy the selection");
		assertThat(catalog.topic("select")).contains("Shift + mouse drag");
		assertThat(catalog.topic("clipboard")).contains("/copy                     Copy the held query Result");
	}

	@Test
	void aCommandNameWithOrWithoutTheSlashFindsOnlyThatCommandAndNoKeySection() {
		String expected = String.join("\n", "Slash commands", "", "Schema",
			"  /describe [<schema|table>] — List schemas, tables and views, or describe a table.");
		assertThat(catalog(KeyBindings.defaults()).topic("describe")).isEqualTo(expected);
		assertThat(catalog(KeyBindings.defaults()).topic(" /describe ")).isEqualTo(expected);
	}

	@Test
	void keysAreFoundByNameIgnoringCaseAndByDescription() {
		String byName = catalog(KeyBindings.defaults()).topic("CTRL-R");
		assertThat(byName).doesNotContain("Slash commands").contains("Ctrl-R                    Search history",
			"Enter                     Send input; accept a Ctrl-R match without sending");
		String byDescription = catalog(KeyBindings.defaults()).topic("newline");
		assertThat(byDescription).contains("Ctrl-J                    Insert a newline", "Shift-Enter               Insert a newline when")
			.doesNotContain("Search history");
	}

	@Test
	void noMatchGivesAHintAndTheWordIsPlainText() {
		var catalog = catalog(KeyBindings.defaults());
		assertThat(catalog.topic("xyz")).isEqualTo("No help for \"xyz\". Try /help commands or /help keys.");
		assertThat(catalog.topic("/xyz")).isEqualTo("No help for \"/xyz\". Try /help commands or /help keys.");
		assertThat(catalog.topic(".*")).isEqualTo("No help for \".*\". Try /help commands or /help keys.");
		assertThat(catalog.topic("x(")).isEqualTo("No help for \"x(\". Try /help commands or /help keys.");
		assertThat(catalog.topic("(")).contains("(primary / fallback)").doesNotContain("No help");
		assertThat(catalog.topic("/")).isEqualTo("No help for \"/\". Try /help commands or /help keys.");
	}

	@Test
	void commandsAndKeysTopicsIgnoreCase() {
		var catalog = catalog(KeyBindings.defaults());
		assertThat(catalog.topic("COMMANDS")).isEqualTo(catalog.topic("commands"));
		assertThat(catalog.topic(" Keys ")).isEqualTo(catalog.topic("keys"));
	}

	/** The rows between a heading line and the next heading line. */
	private static List<String> section(String text, String heading, String next) {
		int from = text.indexOf("\n" + heading + "\n") + heading.length() + 2;
		int to = text.indexOf("\n" + next, from);
		return text.substring(from, to).lines().filter(l -> !l.isBlank()).toList();
	}
}
