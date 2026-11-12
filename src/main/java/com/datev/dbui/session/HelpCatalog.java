package com.datev.dbui.session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.datev.dbui.config.KeyBindings;

/** The text of {@code /help}: an overview, all commands, all keys, or a filter. Plain code: no Session, no terminal. */
final class HelpCatalog {

	/** A Slash command as the help shows it. {@code group} is a heading; {@code synopsis} starts with the slash. */
	record Command(String name, String synopsis, String description, String group) {
	}

	/** Known groups come first, in this order. Any other group follows in the order of its first command. */
	private static final List<String> GROUP_ORDER = List.of("Query and results", "Schema", "Connection", "History and Sessions", "General");

	private static final int KEY_COLUMN = 26;

	private final List<Command> commands;
	private final KeyBindings keys;

	HelpCatalog(List<Command> commands, KeyBindings keys) {
		this.commands = List.copyOf(commands);
		this.keys = keys;
	}

	String topic(String argument) {
		return overview();
	}

	private String overview() {
		Map<String, List<String>> names = new LinkedHashMap<>();
		for (String group : GROUP_ORDER) {
			names.put(group, new ArrayList<>());
		}
		for (var command : commands) {
			names.computeIfAbsent(command.group(), g -> new ArrayList<>()).add("/" + command.name());
		}
		names.values().removeIf(List::isEmpty);
		int width = names.keySet().stream().mapToInt(String::length).max().orElse(0) + 1;
		var text = new StringBuilder("dbui help. Use /help commands, /help keys, or /help <word>.\n\n");
		names.forEach((group, list) -> text.append(String.format("%-" + width + "s %s%n", group, String.join(" ", list))));
		text.append("\nEssential keys\n");
		text.append(keyRow("Enter", "Send input")).append('\n');
		text.append(keyRow(keys.newline().display(), "Insert a newline")).append('\n');
		text.append(keyRow(keys.complete().display(), "Request Completion")).append('\n');
		text.append(keyRow("Ctrl-R", "Search history")).append('\n');
		text.append(keyRow("Ctrl-C", "Cancel a running Statement, or clear the input"));
		return text.toString();
	}

	private static String keyRow(String key, String description) {
		return String.format("%-" + KEY_COLUMN + "s%s", key, description);
	}
}
