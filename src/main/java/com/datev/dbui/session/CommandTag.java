package com.datev.dbui.session;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Derives a PostgreSQL-like command tag for a Statement without a Result.
 * Deliberately simple: it looks only at the first keywords of the Statement text, because JDBC reports
 * only an update count. INSERT, UPDATE, DELETE, MERGE and COPY show the count; CREATE, DROP and ALTER show
 * two keywords; anything else shows the first keyword.
 */
final class CommandTag {

	private static final Pattern LEADING_COMMENTS = Pattern.compile("\\A(?:\\s+|--[^\\n]*(?:\\n|\\z)|/\\*.*?\\*/)*", Pattern.DOTALL);
	private static final Pattern WORDS = Pattern.compile("[A-Za-z_]+");

	private CommandTag() {
	}

	static String of(String sql, long updateCount) {
		var words = WORDS.matcher(LEADING_COMMENTS.matcher(sql).replaceFirst(""));
		String first = words.find() ? words.group().toUpperCase(Locale.ROOT) : "";
		return switch (first) {
			case "INSERT" -> "INSERT 0 " + updateCount;
			case "UPDATE", "DELETE", "MERGE", "COPY" -> first + " " + updateCount;
			case "CREATE", "DROP", "ALTER" -> words.find() ? first + " " + words.group().toUpperCase(Locale.ROOT) : first;
			default -> first;
		};
	}
}
