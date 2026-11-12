package org.syracus.jdbx.tui;

import java.util.List;

import org.syracus.jdbx.completion.Candidate;
import org.syracus.jdbx.completion.Completion;

/** The state of the open Completion list: which candidates are visible and which one is selected. No terminal. */
final class CompletionPopup {

	static final int MAX_VISIBLE = 8;

	private final Completion completion;
	private int selected;
	private int first;

	CompletionPopup(Completion completion) {
		this.completion = completion;
	}

	Completion completion() {
		return completion;
	}

	Candidate selected() {
		return completion.candidates().get(selected);
	}

	/** The row of the selected candidate inside {@link #visible()}. */
	int selectedRow() {
		return selected - first;
	}

	List<Candidate> visible() {
		return completion.candidates().subList(first, Math.min(completion.candidates().size(), first + MAX_VISIBLE));
	}

	int firstVisible() {
		return first;
	}

	boolean hasMoreBelow() {
		return first + MAX_VISIBLE < completion.candidates().size();
	}

	void up() {
		move(-1);
	}

	void down() {
		move(1);
	}

	private void move(int delta) {
		int size = completion.candidates().size();
		selected = Math.floorMod(selected + delta, size);
		if (selected < first) {
			first = selected;
		}
		else if (selected >= first + MAX_VISIBLE) {
			first = selected - MAX_VISIBLE + 1;
		}
	}

	Completion.Edit accept(String text) {
		return completion.apply(text, selected());
	}
}
