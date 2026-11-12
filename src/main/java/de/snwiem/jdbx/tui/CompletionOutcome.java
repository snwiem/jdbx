package de.snwiem.jdbx.tui;

import de.snwiem.jdbx.completion.Completion;

/**
 * What a finished Completion request does to the editor: nothing but a note, one edit, or an edit plus a popup.
 * {@code edit}, {@code popup} and {@code note} are each null when they do not apply.
 */
record CompletionOutcome(Completion.Edit edit, CompletionPopup popup, String note) {

	static CompletionOutcome of(String text, Completion completion) {
		var candidates = completion.candidates();
		if (candidates.isEmpty()) {
			return new CompletionOutcome(null, null, "no completions");
		}
		if (candidates.size() == 1) {
			return new CompletionOutcome(completion.apply(text, candidates.get(0)), null, null);
		}
		var common = completion.applyCommonPrefix(text);
		if (common == null) {
			return new CompletionOutcome(null, new CompletionPopup(completion), null);
		}
		return new CompletionOutcome(common, new CompletionPopup(completion.afterCommonPrefix(common)), null);
	}
}
