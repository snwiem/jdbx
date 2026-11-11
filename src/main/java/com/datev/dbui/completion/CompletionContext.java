package com.datev.dbui.completion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.datev.dbui.SqlTokens;
import com.datev.dbui.SqlTokens.Token;
import com.datev.dbui.SqlTokens.Type;

/**
 * What a {@link CandidateSource} needs to know about the request: the word being completed, where the cursor
 * is in the Statement, and which tables the Statement mentions. Built by {@link CompletionEngine}.
 */
public final class CompletionContext {

	/** The kind of position, found from nearby text only. */
	public enum Position {
		/** Inside a string or comment: nothing to suggest. */
		NONE,
		/** The input is a Slash command: SQL sources stay silent. */
		SLASH,
		/** Where a keyword fits and nothing else is known. */
		KEYWORD,
		/** After FROM, JOIN, INTO, UPDATE, TABLE or a comma in such a list. */
		TABLE,
		/** After SELECT, WHERE, ON, GROUP BY, ORDER BY, SET or an operator. */
		EXPRESSION,
		/** After {@code name.} outside a table position: a column of a table or a table of a schema ({@link #qualifier()}). */
		QUALIFIED,
		/** After {@code schema.} in a table position: a table or view of that schema. */
		QUALIFIED_TABLE
	}

	/** A table the Statement mentions. {@code schema} is null when the Statement does not name one. */
	public record TableRef(String schema, String table, String alias) {
	}

	private final String text;
	private final SchemaModel schema;
	private Position position = Position.KEYWORD;
	private int start;
	private int end;
	private String prefix = "";
	private boolean quoted;
	private List<String> qualifier = List.of();
	private List<TableRef> tables = List.of();

	CompletionContext(String text, int cursor, SchemaModel schema) {
		this.text = text;
		this.schema = schema;
		this.start = cursor;
		this.end = cursor;
		analyze(cursor);
	}

	public String text() {
		return text;
	}

	public SchemaModel schema() {
		return schema;
	}

	public Position position() {
		return position;
	}

	/** Start of the text that a candidate replaces. */
	public int start() {
		return start;
	}

	/** End of the text that a candidate replaces. */
	public int end() {
		return end;
	}

	/** What the user typed of the word, up to the cursor, without the opening quote. */
	public String prefix() {
		return prefix;
	}

	/** Whether the word is a quoted identifier: candidates are then always inserted quoted. */
	public boolean quoted() {
		return quoted;
	}

	/** For {@link Position#QUALIFIED}: the names before the last dot, folded like PostgreSQL does. */
	public List<String> qualifier() {
		return qualifier;
	}

	/** Tables from FROM, JOIN, UPDATE and INTO of the Statement under the cursor. */
	public List<TableRef> tables() {
		return tables;
	}

	/** Whether {@code name} starts with the typed prefix. Case does not matter. */
	public boolean matches(String name) {
		return name.regionMatches(true, 0, prefix, 0, prefix.length());
	}

	/** The text to insert for an identifier: quoted when the name needs it or when the user began a quote. */
	public String identifier(String name) {
		return quoted || needsQuotes(name) ? '"' + name.replace("\"", "\"\"") + '"' : name;
	}

	/** A keyword in lower case when the user typed lower case, upper case otherwise. */
	public String keyword(String keyword) {
		boolean lower = !prefix.isEmpty() && prefix.equals(prefix.toLowerCase(Locale.ROOT));
		return lower ? keyword.toLowerCase(Locale.ROOT) : keyword.toUpperCase(Locale.ROOT);
	}

	static boolean needsQuotes(String name) {
		if (name.isEmpty() || !(name.charAt(0) == '_' || name.charAt(0) >= 'a' && name.charAt(0) <= 'z')) {
			return true;
		}
		for (int i = 1; i < name.length(); i++) {
			char c = name.charAt(i);
			if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '$')) {
				return true;
			}
		}
		return Keywords.RESERVED.contains(name);
	}

	private void analyze(int cursor) {
		if (text.stripLeading().startsWith("/") && !text.stripLeading().startsWith("/*")) {
			position = Position.SLASH;
			while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) {
				start--;
			}
			while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
				end++;
			}
			prefix = text.substring(start, cursor);
			return;
		}
		List<Token> all = SqlTokens.scan(text);
		Token word = null;
		for (Token t : all) {
			if ((t.type() == Type.WORD || t.type() == Type.QUOTED) && t.start() < cursor && cursor <= t.end()) {
				word = t;
			}
			boolean inside = t.start() < cursor && (cursor < t.end() || !t.closed() && cursor == t.end());
			if ((t.type() == Type.STRING || t.type() == Type.COMMENT) && inside) {
				position = Position.NONE;
				return;
			}
		}
		if (word != null && word.type() == Type.WORD) {
			start = word.start();
			end = word.end();
			prefix = text.substring(word.start(), cursor);
		}
		else if (word != null && word.type() == Type.QUOTED) {
			start = word.start();
			end = word.end();
			quoted = true;
			int contentEnd = Math.min(cursor, word.closed() ? word.end() - 1 : word.end());
			prefix = text.substring(word.start() + 1, Math.max(word.start() + 1, contentEnd)).replace("\"\"", "\"");
		}
		analyzeStatement(all, word);
	}

	private static final Set<String> TABLE_WORDS = Set.of("FROM", "JOIN", "INTO", "UPDATE", "TABLE");
	private static final Set<String> CLAUSE_WORDS = Set.of("FROM", "JOIN", "INTO", "UPDATE", "TABLE", "SELECT", "WHERE", "ON",
		"BY", "SET", "HAVING", "USING", "RETURNING");

	/** The tokens of the Statement under the cursor, without comments and without the word being completed. */
	private void analyzeStatement(List<Token> all, Token word) {
		List<Token> stmt = new ArrayList<>();
		int lower = -1;
		for (Token t : all) {
			if (t.type() == Type.SEMICOLON && t.end() <= start) {
				lower = t.end();
			}
		}
		for (Token t : all) {
			if (t.start() < lower || t == word || t.type() == Type.COMMENT) {
				continue;
			}
			if (t.type() == Type.SEMICOLON && t.start() >= end) {
				break;
			}
			if (t.type() != Type.SEMICOLON) {
				stmt.add(t);
			}
		}
		tables = tableRefs(stmt);
		int before = 0;
		while (before < stmt.size() && stmt.get(before).end() <= start) {
			before++;
		}
		// stmt[0..before) lies before the word.
		String clause = clauseBefore(stmt, before);
		Token prev = before > 0 ? stmt.get(before - 1) : null;
		if (prev != null && prev.type() == Type.DOT) {
			List<String> parts = new ArrayList<>();
			int i = before - 2;
			while (i >= 0 && isName(stmt.get(i)) && parts.size() < 2) {
				parts.add(0, name(stmt.get(i)));
				i--;
				if (i >= 0 && stmt.get(i).type() == Type.DOT) {
					i--;
				}
				else {
					break;
				}
			}
			qualifier = parts;
			position = parts.isEmpty() ? Position.KEYWORD
				: "FROM".equals(clause) || "JOIN".equals(clause) || "INTO".equals(clause) || "UPDATE".equals(clause)
					|| "TABLE".equals(clause) ? Position.QUALIFIED_TABLE : Position.QUALIFIED;
		}
		else if (clause == null) {
			position = Position.KEYWORD;
		}
		else if (TABLE_WORDS.contains(clause)) {
			boolean afterKeyword = prev != null && prev.type() == Type.WORD && TABLE_WORDS.contains(upper(prev));
			boolean afterComma = prev != null && prev.type() == Type.COMMA && clause.equals("FROM");
			position = afterKeyword || afterComma ? Position.TABLE : Position.KEYWORD;
		}
		else {
			position = Position.EXPRESSION;
		}
	}

	/** The nearest clause keyword before index {@code before}, skipping parenthesized groups. */
	private String clauseBefore(List<Token> stmt, int before) {
		int depth = 0;
		for (int i = before - 1; i >= 0; i--) {
			Token t = stmt.get(i);
			if (t.type() == Type.OTHER && text.charAt(t.start()) == ')') {
				depth++;
			}
			else if (t.type() == Type.OTHER && text.charAt(t.start()) == '(') {
				depth = Math.max(0, depth - 1);
			}
			else if (depth == 0 && t.type() == Type.WORD && CLAUSE_WORDS.contains(upper(t))) {
				return upper(t);
			}
		}
		return null;
	}

	private List<TableRef> tableRefs(List<Token> stmt) {
		List<TableRef> refs = new ArrayList<>();
		for (int i = 0; i < stmt.size(); i++) {
			Token t = stmt.get(i);
			if (t.type() != Type.WORD || !TABLE_WORDS.contains(upper(t))) {
				continue;
			}
			boolean list = upper(t).equals("FROM");
			int j = i + 1;
			if (j < stmt.size() && stmt.get(j).type() == Type.WORD && upper(stmt.get(j)).equals("ONLY")) {
				j++;
			}
			while (j < stmt.size() && isName(stmt.get(j))) {
				List<String> parts = new ArrayList<>(List.of(name(stmt.get(j++))));
				while (j + 1 < stmt.size() && stmt.get(j).type() == Type.DOT && isName(stmt.get(j + 1))) {
					parts.add(name(stmt.get(j + 1)));
					j += 2;
				}
				String alias = null;
				if (j < stmt.size() && stmt.get(j).type() == Type.WORD && upper(stmt.get(j)).equals("AS")
						&& j + 1 < stmt.size() && isName(stmt.get(j + 1))) {
					alias = name(stmt.get(j + 1));
					j += 2;
				}
				else if (j < stmt.size() && isName(stmt.get(j)) && !isKeyword(stmt.get(j))) {
					alias = name(stmt.get(j++));
				}
				int n = parts.size();
				refs.add(new TableRef(n > 1 ? parts.get(n - 2) : null, parts.get(n - 1), alias));
				if (list && j < stmt.size() && stmt.get(j).type() == Type.COMMA) {
					j++;
				}
				else {
					break;
				}
			}
		}
		return refs;
	}

	private boolean isKeyword(Token t) {
		return t.type() == Type.WORD && (Keywords.ALL.contains(upper(t)) || Keywords.RESERVED.contains(t.text(text).toLowerCase(Locale.ROOT)));
	}

	private boolean isName(Token t) {
		return t.type() == Type.WORD || t.type() == Type.QUOTED;
	}

	private String upper(Token t) {
		return t.text(text).toUpperCase(Locale.ROOT);
	}

	/** An identifier as PostgreSQL reads it: unquoted names fold to lower case, quoted names keep their case. */
	private String name(Token t) {
		String raw = t.text(text);
		if (t.type() == Type.QUOTED) {
			int endQuote = t.closed() ? raw.length() - 1 : raw.length();
			return raw.substring(1, Math.max(1, endQuote)).replace("\"\"", "\"");
		}
		return raw.toLowerCase(Locale.ROOT);
	}
}
