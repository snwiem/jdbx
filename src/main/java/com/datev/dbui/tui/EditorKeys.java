package com.datev.dbui.tui;

import dev.tamboui.tui.bindings.KeyTrigger;
import dev.tamboui.tui.event.KeyCode;

/**
 * Default key map; configuration can supply a different map in issue 0018. {@code complete} opens Completion;
 * {@code completeAlternate} does the same for terminals where Ctrl-Space does not arrive.
 */
public record EditorKeys(KeyTrigger send, KeyTrigger newline, KeyTrigger complete, KeyTrigger completeAlternate) {

	public EditorKeys(KeyTrigger send, KeyTrigger newline) {
		this(send, newline, KeyTrigger.ctrl(' '), KeyTrigger.alt('/'));
	}

	public static EditorKeys defaults() {
		return new EditorKeys(KeyTrigger.key(KeyCode.ENTER), KeyTrigger.ctrl('j'));
	}
}
