package com.datev.dbui.session;

/**
 * Argument validation and mode text for the registry's {@code /readonly [on|off]} Slash command.
 * {@link Session} applies it to the connection.
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
