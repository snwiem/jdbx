package org.syracus.jdbx.session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.syracus.jdbx.config.KeyBindings;

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
	private final String versionText;

	HelpCatalog(List<Command> commands, KeyBindings keys, String versionText) {
		this.commands = List.copyOf(commands);
		this.keys = keys;
		this.versionText = versionText;
	}

	/** One row of the key bindings. {@code key} is the text of the first column. */
	record KeyRow(String group, String key, String description) {
	}

	private static final List<String> KEY_GROUP_ORDER = List.of("Editing", "History", "Completion", "Copy and paste", "Results and screen", "Dialogs");

	private static final String LEGACY_NOTES = """
		Legacy terminals: Ctrl-H = Backspace, Ctrl-I = Tab, Ctrl-J = LF, Ctrl-M = Enter.
		Without Kitty these are not distinct keys; dbui preserves bare LF as Ctrl-J.
		If the terminal sends Enter instead, Ctrl-J / Ctrl-M can send input. Use a distinct key
		or bracketed paste for multi-line input on legacy terminals.""";

	/** The text for {@code /help <argument>}: empty = overview; {@code commands}; {@code keys}. */
	String topic(String argument) {
		String word = argument.strip();
		if (word.isEmpty()) {
			return overview();
		}
		return switch (word.toLowerCase(java.util.Locale.ROOT)) {
			case "commands" -> commandsText(commands);
			case "keys" -> keysText(keyRows()) + "\n\n" + LEGACY_NOTES;
			default -> filter(word);
		};
	}

	/** Commands and key rows whose name or description contains {@code word}: plain text, any case, optional leading slash. */
	private String filter(String word) {
		String needle = (word.startsWith("/") ? word.substring(1) : word).toLowerCase(java.util.Locale.ROOT);
		var foundCommands = needle.isEmpty() ? List.<Command>of() : commands.stream()
			.filter(c -> c.name().toLowerCase(java.util.Locale.ROOT).contains(needle)
				|| c.description().toLowerCase(java.util.Locale.ROOT).contains(needle)).toList();
		var foundKeys = needle.isEmpty() ? List.<KeyRow>of() : keyRows().stream()
			.filter(r -> r.key().toLowerCase(java.util.Locale.ROOT).contains(needle)
				|| r.description().toLowerCase(java.util.Locale.ROOT).contains(needle)).toList();
		var sections = new ArrayList<String>();
		if (!foundCommands.isEmpty()) {
			sections.add(commandsText(foundCommands));
		}
		if (!foundKeys.isEmpty()) {
			sections.add(keysText(foundKeys));
		}
		return sections.isEmpty() ? "No help for \"" + word + "\". Try /help commands or /help keys." : String.join("\n\n", sections);
	}

	/** All commands, then all keys: the whole help in one text. */
	String full() {
		return commandsText(commands) + "\n\n" + keysText(keyRows()) + "\n\n" + LEGACY_NOTES;
	}

	private static String commandsText(List<Command> list) {
		Map<String, List<Command>> byGroup = new LinkedHashMap<>();
		for (String group : GROUP_ORDER) {
			byGroup.put(group, new ArrayList<>());
		}
		for (var command : list) {
			byGroup.computeIfAbsent(command.group(), g -> new ArrayList<>()).add(command);
		}
		var text = new StringBuilder("Slash commands\n");
		byGroup.forEach((group, members) -> {
			if (!members.isEmpty()) {
				text.append('\n').append(group).append('\n');
				members.forEach(c -> text.append("  ").append(c.synopsis()).append(" — ").append(c.description()).append('\n'));
			}
		});
		return text.toString().stripTrailing();
	}

	private static String keysText(List<KeyRow> rows) {
		var text = new StringBuilder("Key bindings\n");
		for (String group : KEY_GROUP_ORDER) {
			var members = rows.stream().filter(r -> r.group().equals(group)).toList();
			if (!members.isEmpty()) {
				text.append('\n').append(group).append('\n');
				members.forEach(r -> text.append("  ").append(keyRow(r.key(), r.description())).append('\n'));
			}
		}
		return text.toString().stripTrailing();
	}

	private List<KeyRow> keyRows() {
		var rows = new ArrayList<KeyRow>();
		rows.add(new KeyRow("Editing", "Enter", "Send input; accept a Ctrl-R match without sending"));
		rows.add(new KeyRow("Editing", keys.newline().display(), "Insert a newline; some modified keys need Kitty keyboard support"));
		rows.add(new KeyRow("Editing", "Left / Right", "Move the cursor; Ctrl-Left / Ctrl-Right move by word"));
		rows.add(new KeyRow("Editing", "Home / End", "Move to the start / end of a line"));
		rows.add(new KeyRow("Editing", "Backspace / Delete", "Delete before / after the cursor"));
		rows.add(new KeyRow("Editing", "Ctrl-W / Ctrl-Backspace", "Delete the previous word"));
		rows.add(new KeyRow("Editing", "Ctrl-Delete", "Delete the next word"));
		rows.add(new KeyRow("Editing", "Keypad navigation keys", "Same cursor actions as the main navigation keys"));
		if (java.util.stream.Stream.of(keys.newline(), keys.orientationCycle(), keys.orientationFallback(), keys.complete(),
				keys.completeAlternate()).noneMatch(key -> key.name().equals("shift-enter"))) {
			rows.add(new KeyRow("Editing", "Shift-Enter", "Insert a newline when the terminal distinguishes it from Enter"));
		}
		rows.add(new KeyRow("History", "Up / Down", "History at the first / last editor line; otherwise move the cursor"));
		rows.add(new KeyRow("History", "Ctrl-R", "Search history; press again for an older match"));
		rows.add(new KeyRow("History", "Esc", "Cancel history search and restore the draft"));
		rows.add(new KeyRow("Completion", keys.complete().display() + " / " + keys.completeAlternate().display(),
			"Request Completion (primary / fallback)"));
		rows.add(new KeyRow("Completion", "Completion Up / Down", "Select a candidate"));
		rows.add(new KeyRow("Completion", "Completion Tab / Enter", "Accept a candidate without sending input"));
		rows.add(new KeyRow("Completion", "Completion Esc", "Close Completion"));
		rows.add(new KeyRow("Copy and paste", "Shift + mouse drag",
			"Select text; dbui captures the mouse. Some terminals use another key (iTerm2: Option)"));
		rows.add(new KeyRow("Copy and paste", "Ctrl-Shift-C", "Copy the selection. A terminal key, not a dbui key (macOS: Cmd-C)"));
		rows.add(new KeyRow("Copy and paste", "Ctrl-Shift-V",
			"Paste. A terminal key; dbui reads it as a bracketed paste and never sends it"));
		rows.add(new KeyRow("Copy and paste", "/copy", "Copy the held query Result to the clipboard (a command, not a key)"));
		rows.add(new KeyRow("Results and screen", "Ctrl-C", "Cancel a running Statement, cancel search, or clear idle input; never exit"));
		rows.add(new KeyRow("Results and screen", "Ctrl-D", "Ignore; never exit (use /quit)"));
		rows.add(new KeyRow("Results and screen", keys.orientationCycle().display() + " / " + keys.orientationFallback().display(),
			"Cycle Orientation (primary / fallback)"));
		rows.add(new KeyRow("Results and screen", "Page Up / Page Down", "Scroll the Transcript"));
		rows.add(new KeyRow("Results and screen", "Mouse wheel", "Scroll the Transcript"));
		rows.add(new KeyRow("Dialogs", "Dialog Tab / Shift-Tab", "Move between fields and Save (not Orientation)"));
		rows.add(new KeyRow("Dialogs", "Dialog Enter", "Next field or Save; never send editor input"));
		rows.add(new KeyRow("Dialogs", "Dialog Ctrl-S", "Save the Profile"));
		rows.add(new KeyRow("Dialogs", "Dialog Ctrl-G / Ctrl-Enter", "Connect without saving (connection dialogs only)"));
		rows.add(new KeyRow("Dialogs", "Dialog Esc / Ctrl-C", "Cancel the dialog or masked prompt"));
		rows.add(new KeyRow("Dialogs", "Password Enter", "Connect with the masked password; never store it in history"));
		rows.add(new KeyRow("Dialogs", "Confirmation y / other", "Confirm Profile removal / cancel it"));
		return rows;
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
		var text = new StringBuilder("dbui " + versionText + ". Use /help commands, /help keys, or /help <word>.\n\n");
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
		return String.format("%-" + Math.max(KEY_COLUMN, key.length() + 1) + "s%s", key, description);
	}
}
