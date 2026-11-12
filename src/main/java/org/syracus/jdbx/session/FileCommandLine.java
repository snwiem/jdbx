package org.syracus.jdbx.session;

import java.nio.file.Path;

/** A literal file name, optionally quoted, followed by untouched SQL for /dump. No shell expansion. */
record FileCommandLine(Path file, String sql) {

	static FileCommandLine parse(String input, boolean dump) {
		String text = input.strip();
		if (text.isEmpty()) throw new IllegalArgumentException("A file name is required.");
		char quote = text.charAt(0);
		String file;
		int end;
		if (quote == '\'' || quote == '"') {
			end = text.indexOf(quote, 1);
			if (end < 0) throw new IllegalArgumentException("The quoted file name is not closed.");
			file = text.substring(1, end++);
			if (end < text.length() && !Character.isWhitespace(text.charAt(end))) throw new IllegalArgumentException("Separate the file name and SQL with whitespace.");
		}
		else {
			end = 0;
			while (end < text.length() && !Character.isWhitespace(text.charAt(end))) end++;
			file = text.substring(0, end);
		}
		String sql = text.substring(end).strip();
		if (file.isBlank() || (!dump && !sql.isEmpty())) throw new IllegalArgumentException("Supply one file name; quote it if it contains spaces.");
		return new FileCommandLine(Path.of(file), sql);
	}
}
