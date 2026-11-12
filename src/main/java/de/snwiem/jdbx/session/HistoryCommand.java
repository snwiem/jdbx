package org.syracus.jdbx.session;

import java.util.List;

/** The logic of {@code /history}: list, load one entry, clear. Plain code: no Session, no terminal. */
final class HistoryCommand {

	/** {@code editorText} is null unless an entry is loaded. {@code clear} asks the Session to empty the history. */
	record Outcome(List<MessageBlock> blocks, String editorText, boolean clear) {
	}

	static Outcome run(String argument, List<String> entries, boolean enabled, int maxWidth) {
		String arg = argument.strip();
		boolean number = arg.matches("[1-9][0-9]*");
		if (!arg.isEmpty() && !arg.equals("clear") && !number) {
			return message(MessageBlock.error("Usage: /history [<number>|clear]"));
		}
		if (!enabled) {
			return message(MessageBlock.info("The history is off."));
		}
		if (entries.isEmpty() && !number) {
			return message(MessageBlock.info("The history is empty."));
		}
		if (arg.equals("clear")) {
			return new Outcome(List.of(MessageBlock.info("History cleared (" + count(entries.size()) + ").")), null, true);
		}
		if (number) {
			if (arg.length() > 9 || Integer.parseInt(arg) > entries.size()) {
				return message(MessageBlock.error("No history entry " + arg + ". The history has " + count(entries.size()) + "."));
			}
			return new Outcome(List.of(), entries.get(Integer.parseInt(arg) - 1), false);
		}
		return message(MessageBlock.info(list(entries, maxWidth)));
	}

	private static String count(int entries) {
		return entries + (entries == 1 ? " entry" : " entries");
	}

	private static Outcome message(MessageBlock block) {
		return new Outcome(List.of(block), null, false);
	}

	/** One row per entry: the number, then the first line. More lines are marked with {@code " …"}. */
	private static String list(List<String> entries, int maxWidth) {
		int digits = String.valueOf(entries.size()).length();
		var rows = new java.util.ArrayList<String>();
		for (int i = 0; i < entries.size(); i++) {
			String entry = entries.get(i);
			var lineBreak = java.util.regex.Pattern.compile("\r\n|\r|\n").matcher(entry);
			boolean moreLines = lineBreak.find();
			String first = moreLines ? entry.substring(0, lineBreak.start()) : entry;
			rows.add(String.format("%" + digits + "d  %s%s", i + 1, cut(first, maxWidth), moreLines ? " …" : ""));
		}
		return String.join("\n", rows);
	}

	/** Like the {@code table} Format: at most {@code max} characters including the {@code …}; 0 means no cut. */
	private static String cut(String text, int max) {
		if (max <= 0 || text.codePointCount(0, text.length()) <= max) {
			return text;
		}
		return text.substring(0, text.offsetByCodePoints(0, Math.max(max - 1, 0))) + "…";
	}
}
