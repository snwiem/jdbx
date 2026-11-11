package com.datev.dbui;

import java.util.ArrayList;
import java.util.List;

/**
 * A small SQL scanner shared by Completion and the Dump query check: it knows words, quoted identifiers, strings, dollar-quoted strings and
 * comments so that nothing inside them is read as SQL. It does not parse.
 */
public final class SqlTokens {

	public enum Type {
		WORD, QUOTED, STRING, COMMENT, DOT, COMMA, SEMICOLON, OTHER
	}

	/** {@code closed} is false for an unterminated quote, string or block comment, and for a line comment. */
	public record Token(Type type, int start, int end, boolean closed) {

		public String text(String source) {
			return source.substring(start, end);
		}
	}

	private SqlTokens() { }

	public static List<Token> scan(String text) {
		List<Token> tokens = new ArrayList<>();
		int n = text.length();
		int i = 0;
		while (i < n) {
			char c = text.charAt(i);
			if (Character.isWhitespace(c)) {
				i++;
			}
			else if (c == '-' && at(text, i + 1) == '-') {
				int end = text.indexOf('\n', i);
				end = end < 0 ? n : end;
				tokens.add(new Token(Type.COMMENT, i, end, false));
				i = end;
			}
			else if (c == '/' && at(text, i + 1) == '*') {
				int end = blockCommentEnd(text, i);
				tokens.add(new Token(Type.COMMENT, i, end, text.startsWith("*/", end - 2) && end - i >= 4));
				i = end;
			}
			else if (c == '\'') {
				boolean escapes = i > 0 && (text.charAt(i - 1) == 'E' || text.charAt(i - 1) == 'e')
						&& (i < 2 || !isWordPart(text.charAt(i - 2)));
				int end = quoteEnd(text, i, '\'', escapes);
				tokens.add(new Token(Type.STRING, i, end, closedQuote(text, i, end, '\'')));
				i = end;
			}
			else if (c == '"') {
				int end = quoteEnd(text, i, '"', false);
				tokens.add(new Token(Type.QUOTED, i, end, closedQuote(text, i, end, '"')));
				i = end;
			}
			else if (c == '$' && dollarTagEnd(text, i) > 0) {
				int tagEnd = dollarTagEnd(text, i);
				int close = text.indexOf(text.substring(i, tagEnd), tagEnd);
				int end = close < 0 ? n : close + (tagEnd - i);
				tokens.add(new Token(Type.STRING, i, end, close >= 0));
				i = end;
			}
			else if (isWordPart(c)) {
				int end = i + 1;
				while (end < n && (isWordPart(text.charAt(end)) || text.charAt(end) == '$')) {
					end++;
				}
				tokens.add(new Token(Type.WORD, i, end, true));
				i = end;
			}
			else {
				Type type = switch (c) {
					case '.' -> Type.DOT;
					case ',' -> Type.COMMA;
					case ';' -> Type.SEMICOLON;
					default -> Type.OTHER;
				};
				tokens.add(new Token(type, i, i + 1, true));
				i++;
			}
		}
		return tokens;
	}

	private static boolean closedQuote(String text, int start, int end, char quote) {
		return end - start >= 2 && text.charAt(end - 1) == quote && end <= text.length() && quoteCount(text, start, end, quote) % 2 == 0;
	}

	private static int quoteCount(String text, int start, int end, char quote) {
		int count = 0;
		for (int i = start; i < end; i++) {
			if (text.charAt(i) == quote) {
				count++;
			}
		}
		return count;
	}

	private static char at(String text, int i) {
		return i < text.length() ? text.charAt(i) : '\0';
	}

	static boolean isWordPart(char c) {
		return Character.isLetterOrDigit(c) || c == '_';
	}

	private static int blockCommentEnd(String text, int from) {
		int depth = 0;
		int i = from;
		while (i < text.length()) {
			if (text.startsWith("/*", i)) {
				depth++;
				i += 2;
			}
			else if (text.startsWith("*/", i)) {
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
		return text.length();
	}

	private static int quoteEnd(String text, int from, char quote, boolean backslashEscapes) {
		int i = from + 1;
		while (i < text.length()) {
			char c = text.charAt(i);
			if (backslashEscapes && c == '\\') {
				i += 2;
			}
			else if (c == quote) {
				if (at(text, i + 1) == quote) {
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
		return text.length();
	}

	private static int dollarTagEnd(String text, int from) {
		int i = from + 1;
		if (i < text.length() && (Character.isLetter(text.charAt(i)) || text.charAt(i) == '_')) {
			while (i < text.length() && isWordPart(text.charAt(i))) {
				i++;
			}
		}
		return at(text, i) == '$' ? i + 1 : -1;
	}
}
