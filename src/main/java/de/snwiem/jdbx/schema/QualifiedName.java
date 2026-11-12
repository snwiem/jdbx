package de.snwiem.jdbx.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A table name as the user types it: {@code name} or {@code schema.name}. Unquoted parts are folded to
 * lower case like PostgreSQL does; quoted parts keep their case. {@code schema} is null when absent.
 */
public record QualifiedName(String schema, String name) {

	public static QualifiedName parse(String text) {
		List<String> parts = new ArrayList<>();
		var part = new StringBuilder();
		boolean quoted = false;
		boolean partQuoted = false;
		String s = text.strip();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (quoted) {
				if (c == '"') {
					if (i + 1 < s.length() && s.charAt(i + 1) == '"') {
						part.append('"');
						i++;
					}
					else {
						quoted = false;
					}
				}
				else {
					part.append(c);
				}
			}
			else if (c == '"') {
				quoted = true;
				partQuoted = true;
			}
			else if (c == '.') {
				parts.add(finish(part, partQuoted));
				part.setLength(0);
				partQuoted = false;
			}
			else {
				part.append(c);
			}
		}
		if (quoted) {
			throw new IllegalArgumentException("Unterminated quoted name: " + text);
		}
		parts.add(finish(part, partQuoted));
		if (parts.size() > 2) {
			throw new IllegalArgumentException("Too many name parts: " + text);
		}
		return parts.size() == 2 ? new QualifiedName(parts.get(0), parts.get(1)) : new QualifiedName(null, parts.get(0));
	}

	private static String finish(StringBuilder part, boolean quoted) {
		String value = part.toString();
		if (!quoted) {
			value = value.strip().toLowerCase(Locale.ROOT);
		}
		if (value.isEmpty()) {
			throw new IllegalArgumentException("Empty name part");
		}
		return value;
	}
}
