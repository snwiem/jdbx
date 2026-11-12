package de.snwiem.jdbx;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits SQL text into Statements at {@code ;}. It does not split inside strings, quoted identifiers, comments or
 * dollar-quoted strings (PostgreSQL syntax). Empty Statements are skipped.
 */
public class StatementSplitter {

	public List<String> split(String input) {
		List<String> statements = new ArrayList<>();
		int n = input.length();
		int start = 0;
		boolean content = false;
		int i = 0;
		while (i < n) {
			char c = input.charAt(i);
			if (c == ';') {
				add(statements, input, start, i, content);
				start = ++i;
				content = false;
			}
			else if (c == '-' && at(input, i + 1) == '-') {
				i = lineEnd(input, i);
			}
			else if (c == '/' && at(input, i + 1) == '*') {
				i = blockEnd(input, i);
			}
			else if (c == '\'') {
				boolean escapes = i > 0 && (input.charAt(i - 1) == 'E' || input.charAt(i - 1) == 'e')
						&& (i < 2 || !isIdentifierPart(input.charAt(i - 2)));
				i = quoteEnd(input, i, '\'', escapes);
				content = true;
			}
			else if (c == '"') {
				i = quoteEnd(input, i, '"', false);
				content = true;
			}
			else if (c == '$' && (i == 0 || !isIdentifierPart(input.charAt(i - 1))) && dollarTagEnd(input, i) > 0) {
				int tagEnd = dollarTagEnd(input, i);
				String tag = input.substring(i, tagEnd);
				int close = input.indexOf(tag, tagEnd);
				i = close < 0 ? n : close + tag.length();
				content = true;
			}
			else {
				if (!Character.isWhitespace(c)) {
					content = true;
				}
				i++;
			}
		}
		add(statements, input, start, n, content);
		return statements;
	}

	private static void add(List<String> statements, String input, int start, int end, boolean content) {
		if (content) {
			String text = input.substring(start, end).strip();
			if (!text.isEmpty()) {
				statements.add(text);
			}
		}
	}

	private static char at(String input, int i) {
		return i < input.length() ? input.charAt(i) : '\0';
	}

	private static boolean isIdentifierPart(char c) {
		return Character.isLetterOrDigit(c) || c == '_';
	}

	private static int lineEnd(String input, int from) {
		int end = input.indexOf('\n', from);
		return end < 0 ? input.length() : end;
	}

	private static int blockEnd(String input, int from) {
		int depth = 0;
		int i = from;
		while (i < input.length()) {
			if (input.startsWith("/*", i)) {
				depth++;
				i += 2;
			}
			else if (input.startsWith("*/", i)) {
				depth--;
				i += 2;
				if (depth == 0) {
					return i;
				}
			}
			else {
				i++;
			}
		}
		return input.length();
	}

	private static int quoteEnd(String input, int from, char quote, boolean backslashEscapes) {
		int i = from + 1;
		while (i < input.length()) {
			char c = input.charAt(i);
			if (backslashEscapes && c == '\\') {
				i += 2;
			}
			else if (c == quote) {
				if (at(input, i + 1) == quote) {
					i += 2;
				}
				else {
					return i + 1;
				}
			}
			else {
				i++;
			}
		}
		return input.length();
	}

	/** Returns the index after a dollar-quote tag ({@code $$} or {@code $tag$}) at {@code from}, or -1. */
	private static int dollarTagEnd(String input, int from) {
		int i = from + 1;
		if (i < input.length() && (Character.isLetter(input.charAt(i)) || input.charAt(i) == '_')) {
			while (i < input.length() && isIdentifierPart(input.charAt(i))) {
				i++;
			}
		}
		return at(input, i) == '$' ? i + 1 : -1;
	}
}
