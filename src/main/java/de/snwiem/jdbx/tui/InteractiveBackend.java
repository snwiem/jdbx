package de.snwiem.jdbx.tui;

import java.io.IOException;

import org.jline.terminal.Terminal;

import dev.tamboui.backend.jline3.JLineBackend;

/** Adapts the editor input stream to the JLine 3 backend. */
final class InteractiveBackend extends JLineBackend {

	private final EditorInput input = new EditorInput();

	InteractiveBackend() throws IOException {
		super();
	}

	InteractiveBackend(Terminal terminal) {
		super(terminal);
	}

	@Override
	public int read(int timeoutMs) throws IOException {
		return input.read(timeoutMs, super::read);
	}

	@Override
	public int peek(int timeoutMs) throws IOException {
		return input.peek(timeoutMs, super::peek);
	}
}
