package de.snwiem.jdbx.tui;

import java.util.List;
import java.util.Locale;

/** Incremental reverse search over the history: newest match first, case-insensitive substring. */
final class ReverseSearch {

	private final List<String> entries;
	private final StringBuilder query = new StringBuilder();
	private int index;

	ReverseSearch(List<String> entries) {
		this.entries = List.copyOf(entries);
		this.index = -1;
	}

	String query() {
		return query.toString();
	}

	/** The entry that matches now, or {@code null} when the query is empty or nothing matches. */
	String match() {
		return index < 0 ? null : entries.get(index);
	}

	boolean failing() {
		return query.length() > 0 && index < 0;
	}

	void type(char character) {
		query.append(character);
		index = find(entries.size() - 1);
	}

	void backspace() {
		if (query.length() > 0) {
			query.setLength(query.length() - 1);
			index = find(entries.size() - 1);
		}
	}

	/** Ctrl-R again: the next older match. Stays on the current one when there is none. */
	void older() {
		if (index > 0) {
			int found = find(index - 1);
			if (found >= 0) {
				index = found;
			}
		}
	}

	private int find(int from) {
		if (query.length() == 0) {
			return -1;
		}
		String needle = query.toString().toLowerCase(Locale.ROOT);
		for (int i = from; i >= 0; i--) {
			if (entries.get(i).toLowerCase(Locale.ROOT).contains(needle)) {
				return i;
			}
		}
		return -1;
	}
}
