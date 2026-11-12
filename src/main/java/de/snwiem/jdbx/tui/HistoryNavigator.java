package org.syracus.jdbx.tui;

import java.util.List;
import java.util.Optional;

/** Up and Down through the history of the Session. The text typed before the first Up (the draft) comes back at the end. */
final class HistoryNavigator {

	private List<String> entries = List.of();
	private int position;
	private String draft = "";

	/** Replaces the entries and goes back to the draft position. */
	void entries(List<String> entries) {
		this.entries = List.copyOf(entries);
		position = this.entries.size();
	}

	/** The next older entry, or empty when there is none. {@code current} is kept as draft on the first step. */
	Optional<String> previous(String current) {
		if (position == 0) {
			return Optional.empty();
		}
		if (position == entries.size()) {
			draft = current;
		}
		return Optional.of(entries.get(--position));
	}

	/** The next newer entry, the draft after the newest, or empty when the editor shows the draft already. */
	Optional<String> next() {
		if (position >= entries.size()) {
			return Optional.empty();
		}
		position++;
		return Optional.of(position == entries.size() ? draft : entries.get(position));
	}

	/** Forget the walk, for example after a submit. */
	void reset() {
		position = entries.size();
	}
}
