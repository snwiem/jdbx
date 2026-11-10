package com.datev.dbui.session;

/**
 * The {@code /readonly [on|off]} Slash command: parsing only. {@link Session} applies it to the connection.
 * Kept apart from the other Slash commands so that it can move to the Slash command registry (issue 0012).
 */
final class ReadOnlyCommand {

	static final String USAGE = "Usage: /readonly [on|off]";

	enum Kind { NOT_MINE, SHOW, ON, OFF, USAGE }

	private ReadOnlyCommand() { }

	static Kind parse(String input) {
		String[] parts = input.strip().split("\\s+");
		if (!parts[0].equals("/readonly")) {
			return Kind.NOT_MINE;
		}
		if (parts.length == 1) {
			return Kind.SHOW;
		}
		if (parts.length == 2 && parts[1].equals("on")) {
			return Kind.ON;
		}
		if (parts.length == 2 && parts[1].equals("off")) {
			return Kind.OFF;
		}
		return Kind.USAGE;
	}

	static String describe(boolean readOnly) {
		return readOnly ? "read-only" : "read-write";
	}
}
