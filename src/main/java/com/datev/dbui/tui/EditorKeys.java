package com.datev.dbui.tui;

import dev.tamboui.tui.bindings.KeyTrigger;
import dev.tamboui.tui.event.KeyCode;

/** Default key map; configuration can supply a different map in issue 0018. */
public record EditorKeys(KeyTrigger send, KeyTrigger newline, KeyTrigger orientationCycle, KeyTrigger orientationFallback) {

	public EditorKeys(KeyTrigger send, KeyTrigger newline) {
		this(send, newline, KeyTrigger.key(KeyCode.TAB, false, false, true), KeyTrigger.ctrl('o'));
	}

	public static EditorKeys defaults() {
		return new EditorKeys(KeyTrigger.key(KeyCode.ENTER), KeyTrigger.ctrl('j'));
	}
}
