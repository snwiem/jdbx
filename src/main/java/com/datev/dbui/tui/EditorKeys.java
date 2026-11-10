package com.datev.dbui.tui;

import dev.tamboui.tui.bindings.KeyTrigger;
import dev.tamboui.tui.event.KeyCode;

/** Default key map; configuration can supply a different map in issue 0018. */
public record EditorKeys(KeyTrigger send, KeyTrigger newline) {

	public static EditorKeys defaults() {
		return new EditorKeys(KeyTrigger.key(KeyCode.ENTER), KeyTrigger.ctrl('j'));
	}
}
