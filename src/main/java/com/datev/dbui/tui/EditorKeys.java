package com.datev.dbui.tui;

import dev.tamboui.tui.bindings.KeyTrigger;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;

/** Default key map; configuration can supply a different map in issue 0018. */
public record EditorKeys(KeyTrigger send, KeyTrigger newline, KeyTrigger orientationCycle, KeyTrigger orientationFallback,
		KeyTrigger complete, KeyTrigger completeAlternate) {

	public EditorKeys(KeyTrigger send, KeyTrigger newline) {
		this(send, newline, KeyTrigger.key(KeyCode.TAB, false, false, true), KeyTrigger.ctrl('o'),
			KeyTrigger.key(KeyCode.TAB), KeyTrigger.alt('/'));
	}

	/** True for the Completion keys. KeyTrigger ignores Shift unless required, so Shift-Tab is excluded here. */
	public boolean requestsCompletion(KeyEvent key) {
		if (key.isKey(KeyCode.TAB) && key.hasShift()) {
			return false;
		}
		return complete.matches(key) || completeAlternate.matches(key);
	}

	public static EditorKeys defaults() {
		return new EditorKeys(KeyTrigger.key(KeyCode.ENTER), KeyTrigger.ctrl('j'));
	}
}
