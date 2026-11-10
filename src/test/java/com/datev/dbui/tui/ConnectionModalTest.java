package com.datev.dbui.tui;

import static org.assertj.core.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.datev.dbui.config.*;
import com.datev.dbui.session.SessionInteraction;
import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.layout.Size;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.*;
import dev.tamboui.tui.pilot.TuiTestRunner;

class ConnectionModalTest {
	private static String draw(InteractiveScreen screen, int width, int height) {
		var buffer = Buffer.empty(Rect.of(width, height)); screen.render(Frame.forTesting(buffer));
		var text = new StringBuilder();
		for (int y = 0; y < height; y++) { for (int x = 0; x < width; x++) text.append(buffer.get(x, y).symbol()); text.append('\n'); }
		return text.toString();
	}

	@Test
	void passwordPromptMasksTypedAndPastedInputAndEscapeCancels() {
		var inputs = new ArrayList<String>();
		var passwords = new ArrayList<String>();
		var cancelled = new AtomicInteger();
		var screen = new InteractiveScreen("connection", inputs::add);
		screen.onInteraction(save -> { }, passwords::add, cancelled::incrementAndGet);
		screen.interaction(new SessionInteraction.Password("alice@db"));
		screen.handle(new PasteEvent("super-secret"));
		assertThat(draw(screen, 90, 20)).contains("Password", "************").doesNotContain("super-secret");
		screen.handle(KeyEvent.ofKey(KeyCode.ENTER));
		assertThat(passwords).containsExactly("super-secret");
		assertThat(inputs).isEmpty();
		screen.handle(KeyEvent.ofKey(KeyCode.ESCAPE));
		assertThat(cancelled.get()).isEqualTo(1);
		for (var size : List.of(new Size(1,1), new Size(30,8), new Size(110,40))) draw(screen, size.width(), size.height());
	}

	@Test
	void tabShiftTabAndSaveWorkWithoutSendingDialogValuesAsStatements() throws Exception {
		var inputs = new ArrayList<String>();
		var saved = new ArrayList<Boolean>();
		var cancelled = new AtomicInteger();
		var screen = new InteractiveScreen("not connected", inputs::add);
		screen.onInteraction(saved::add, password -> { }, cancelled::incrementAndGet);
		var model = new ProfileDialogModel(new Profile(null, null, "localhost", null, "db", "alice", null, false, null, Map.of()), null);
		screen.interaction(new SessionInteraction.Dialog(model, true, null));
		try (var terminal = TuiTestRunner.runTest((event, runner) -> screen.handle(event), screen::render, new Size(100, 24))) {
			terminal.runner().dispatch(new PasteEvent("dev"));
			terminal.pilot().press(KeyCode.TAB);
			terminal.pilot().press(KeyCode.TAB, KeyModifiers.SHIFT);
			terminal.pilot().press("2");
			terminal.pilot().press('s', KeyModifiers.CTRL);
			terminal.pilot().pause();
			assertThat(model.value(ProfileDialogModel.Field.NAME)).isEqualTo("dev2");
			assertThat(saved).containsExactly(true);
			assertThat(inputs).isEmpty();
			assertThat(draw(screen, 100, 24)).contains("Edit Connection Profile", "Password", "Ctrl-Enter");
			var blocks = new ArrayList<com.datev.dbui.session.MessageBlock>();
			for (int i = 0; i < 10; i++) blocks.add(com.datev.dbui.session.MessageBlock.info("earlier event"));
			blocks.add(com.datev.dbui.session.MessageBlock.error("Name is required to save a Profile."));
			screen.update(blocks);
			assertThat(draw(screen, 100, 24)).contains("Name is required to save a Profile.");
			terminal.pilot().press(KeyCode.ESCAPE);
			terminal.pilot().pause();
			assertThat(cancelled.get()).isEqualTo(1);
			terminal.pilot().quit();
		}
	}
}
