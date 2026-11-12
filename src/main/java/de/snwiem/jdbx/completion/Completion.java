package org.syracus.jdbx.completion;

import java.util.List;

/** The candidates for one request and the range {@code [start, end)} of the editor text they replace. */
public record Completion(int start, int end, List<Candidate> candidates) {

	/** The edited text and cursor offset after an edit. */
	public record Edit(String text, int cursor) {
	}

	public Completion {
		candidates = List.copyOf(candidates);
	}

	/** Replaces the range of {@code text} by the candidate's insert text; the cursor ends after it. */
	public Edit apply(String text, Candidate candidate) {
		return new Edit(text.substring(0, start) + candidate.insert() + text.substring(end), start + candidate.insert().length());
	}

	/**
	 * Replaces the range by the longest common prefix of all candidates when that is longer than what is typed.
	 * Returns null when it adds nothing. {@link #end()} of the follow-up Completion is {@code start + prefix length}.
	 */
	public Edit applyCommonPrefix(String text) {
		if (candidates.isEmpty()) {
			return null;
		}
		String common = candidates.get(0).insert();
		for (Candidate candidate : candidates) {
			String other = candidate.insert();
			int length = 0;
			while (length < common.length() && length < other.length() && common.charAt(length) == other.charAt(length)) {
				length++;
			}
			common = common.substring(0, length);
		}
		if (common.length() <= end - start) {
			return null;
		}
		return new Edit(text.substring(0, start) + common + text.substring(end), start + common.length());
	}

	/** The same candidates for the range after {@link #applyCommonPrefix}. */
	public Completion afterCommonPrefix(Edit edit) {
		return new Completion(start, edit.cursor(), candidates);
	}
}
