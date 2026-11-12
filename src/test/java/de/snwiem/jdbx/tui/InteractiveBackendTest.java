package org.syracus.jdbx.tui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.Test;

import dev.tamboui.tui.event.EventParser;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.PasteEvent;

/** Terminal byte seam: Pilot bypasses the backend and cannot verify legacy LF or paste framing. */
class InteractiveBackendTest {

	@Test
	void legacyAndKittyCtrlJStayDistinctFromEnterAndBracketedPaste() throws Exception {
		String bytes = "\n\r\033[106;5u\033[200~select\n1\r\n\033[201~\n";
		try (var terminal = new DumbTerminal(new ByteArrayInputStream(bytes.getBytes(StandardCharsets.UTF_8)),
			new ByteArrayOutputStream()); var backend = new InteractiveBackend(terminal)) {
			assertThat(backend.peek(100)).isEqualTo(27);
			assertThat(backend.peek(100)).isEqualTo(27);
			assertCtrlJ((KeyEvent) EventParser.readEvent(backend, 100));
			assertThat(((KeyEvent) EventParser.readEvent(backend, 100)).code()).isEqualTo(KeyCode.ENTER);
			assertCtrlJ((KeyEvent) EventParser.readEvent(backend, 100));
			var paste = (PasteEvent) EventParser.readEvent(backend, 100);
			assertThat(paste.text()).isEqualTo("select\n1\r\n");
			assertCtrlJ((KeyEvent) EventParser.readEvent(backend, 100));
			assertThat(backend.read(100)).isEqualTo(-1);
		}
	}

	private static void assertCtrlJ(KeyEvent key) {
		assertThat(key.code()).isEqualTo(KeyCode.CHAR);
		assertThat(key.hasCtrl()).isTrue();
		assertThat(key.isCharIgnoreCase('j')).isTrue();
	}
}
