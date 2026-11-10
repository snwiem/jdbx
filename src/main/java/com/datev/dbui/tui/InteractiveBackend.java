package com.datev.dbui.tui;

import java.io.IOException;

import dev.tamboui.backend.jline3.JLineBackend;

/** Preserves legacy Ctrl-J (LF) as a distinct key, without changing bracketed paste. */
final class InteractiveBackend extends JLineBackend {

	private static final String CTRL_J = "\033[106;5u";
	private static final String PASTE_START = "\033[200~";
	private static final String PASTE_END = "\033[201~";

	private String pending = "";
	private int pendingIndex;
	private final StringBuilder suffix = new StringBuilder();
	private boolean pasting;

	InteractiveBackend() throws IOException {
		super();
	}

	@Override
	public int read(int timeoutMs) throws IOException {
		if (pendingIndex < pending.length()) {
			return pending.charAt(pendingIndex++);
		}
		int character = super.read(timeoutMs);
		if (character < 0) {
			return character;
		}
		suffix.appendCodePoint(character);
		if (suffix.length() > PASTE_START.length()) {
			suffix.delete(0, suffix.length() - PASTE_START.length());
		}
		if (suffix.toString().equals(PASTE_START)) {
			pasting = true;
		}
		else if (suffix.toString().equals(PASTE_END)) {
			pasting = false;
		}
		if (character == '\n' && !pasting) {
			pending = CTRL_J;
			pendingIndex = 1;
			return pending.charAt(0);
		}
		return character;
	}

	@Override
	public int peek(int timeoutMs) throws IOException {
		if (pendingIndex < pending.length()) {
			return pending.charAt(pendingIndex);
		}
		int character = super.peek(timeoutMs);
		return character == '\n' && !pasting ? CTRL_J.charAt(0) : character;
	}
}
