package com.datev.dbui.schema;

import java.util.Optional;
import java.util.Set;

/** A Slash command of the schema family with its raw argument text. */
public record SchemaCommandLine(String command, String argument) {

	private static final Set<String> COMMANDS = Set.of("schema", "tables", "describe", "refresh");

	public static Optional<SchemaCommandLine> parse(String input) {
		String s = input.strip();
		if (!s.startsWith("/")) {
			return Optional.empty();
		}
		int space = 1;
		while (space < s.length() && !Character.isWhitespace(s.charAt(space))) {
			space++;
		}
		String command = s.substring(1, space);
		if (!COMMANDS.contains(command)) {
			return Optional.empty();
		}
		return Optional.of(new SchemaCommandLine(command, s.substring(space).strip()));
	}
}
