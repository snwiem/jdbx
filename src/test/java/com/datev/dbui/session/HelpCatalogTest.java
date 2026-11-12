package com.datev.dbui.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.datev.dbui.config.KeyBindings;
import com.datev.dbui.session.HelpCatalog.Command;

class HelpCatalogTest {

	static final List<Command> COMMANDS = List.of(
		new Command("format", "/format <table|json>", "Change the Format for new Results.", "Query and results"),
		new Command("describe", "/describe [<schema|table>]", "List schemas, tables and views, or describe a table.", "Schema"),
		new Command("history", "/history [<number>|clear]", "List the input history, load an entry or clear it.", "History and Sessions"),
		new Command("help", "/help [commands|keys|<word>]", "Show help.", "General"),
		new Command("quit", "/quit", "End the Session.", "General"),
		new Command("future", "/future", "A newly registered command.", "Other"));

	private static HelpCatalog catalog(KeyBindings keys) {
		return new HelpCatalog(COMMANDS, keys);
	}

	@Test
	void theOverviewListsEveryCommandNameUnderItsGroupAndFiveEssentialKeys() {
		assertThat(catalog(KeyBindings.defaults()).topic("")).isEqualTo(String.join("\n",
			"dbui help. Use /help commands, /help keys, or /help <word>.",
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
}
