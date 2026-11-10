package com.datev.dbui.tui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.datev.dbui.Result;
import com.datev.dbui.session.MessageBlock;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.layout.Size;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyModifiers;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.PasteEvent;
import dev.tamboui.tui.pilot.TuiTestRunner;

class InteractiveScreenTest {

	@Test
	void editorIgnoresInputWhileAStatementRuns() {
		var inputs = new CopyOnWriteArrayList<String>();
		var screen = new InteractiveScreen("dbui@localhost:5432/dbui", inputs::add);
		screen.busy(true);
		screen.handle(new PasteEvent("must not run"));
		screen.handle(KeyEvent.ofKey(KeyCode.ENTER));
		screen.handle(KeyEvent.ofChar('j', KeyModifiers.CTRL));
		screen.busy(false);
		screen.handle(KeyEvent.ofKey(KeyCode.ENTER));
		assertThat(inputs).isEmpty();
	}

	@Test
	void bracketedPasteStaysOneInputAndNavigationEditsAcrossLinesAndWords() throws Exception {
		var inputs = new CopyOnWriteArrayList<String>();
		var screen = new InteractiveScreen("dbui@localhost:5432/dbui", inputs::add);
		try (var terminal = TuiTestRunner.runTest((event, runner) -> screen.handle(event), screen::render, new Size(60, 20))) {
			terminal.runner().dispatch(new PasteEvent("select one\r\nfrom two"));
			terminal.pilot().pause();
			assertThat(inputs).isEmpty();
			terminal.pilot().press(KeyCode.HOME);
			terminal.pilot().press(KeyCode.UP);
			terminal.pilot().press(KeyCode.RIGHT, KeyModifiers.CTRL);
			terminal.runner().dispatch(new PasteEvent("distinct "));
			terminal.pilot().press(KeyCode.DOWN);
			terminal.pilot().press(KeyCode.END);
			terminal.pilot().press(";");
			terminal.pilot().press(KeyCode.ENTER);
			terminal.pilot().pause();
			assertThat(inputs).containsExactly("select distinct one\nfrom two;");
			terminal.pilot().quit();
		}
	}

	@Test
	void transcriptScrollsWhileEditorAndStatusBarStayFixedAfterResize() {
		var screen = new InteractiveScreen("dbui@localhost:5432/dbui", input -> { });
		var values = IntStream.rangeClosed(1, 30).mapToObj(n -> List.<Object>of(n)).toList();
		screen.update(List.of(MessageBlock.input("first"), MessageBlock.result(new Result(List.of("id"), values))));
		screen.handle(new PasteEvent("select\n1"));
		for (var size : List.of(new Size(60, 20), new Size(40, 10), new Size(100, 40))) {
			var bottom = draw(screen, size);
			assertThat(line(bottom, size.height() - 2)).startsWith(" dbui@localhost:5432/dbui");
			assertThat(line(bottom, size.height() - 5)).startsWith("select");
			assertThat(line(bottom, size.height() - 4)).startsWith("1");
			assertThat(line(bottom, size.height() - 3)).startsWith("─");
			for (int page = 0; page < 50; page++) {
				screen.handle(KeyEvent.ofKey(KeyCode.PAGE_UP));
			}
			var top = draw(screen, size);
			assertThat(line(top, 1)).startsWith(" first");
			assertThat(line(top, size.height() - 5)).startsWith("select");
			assertThat(line(top, size.height() - 2)).isEqualTo(line(bottom, size.height() - 2));
			screen.handle(MouseEvent.scrollDown(2, 1));
			var wheeled = draw(screen, size);
			assertThat(line(wheeled, 1)).doesNotStartWith(" first");
			for (int page = 0; page < 50; page++) {
				screen.handle(KeyEvent.ofKey(KeyCode.PAGE_DOWN));
			}
			draw(screen, size);
			screen.handle(MouseEvent.scrollUp(2, 1));
			assertThat(line(draw(screen, size), 1)).isNotEqualTo(line(drawAtBottom(screen, size), 1));
		}
		for (var size : List.of(new Size(1, 1), new Size(3, 4), new Size(30, 8))) {
			draw(screen, size);
		}
	}

	private static Buffer drawAtBottom(InteractiveScreen screen, Size size) {
		for (int page = 0; page < 50; page++) {
			screen.handle(KeyEvent.ofKey(KeyCode.PAGE_DOWN));
		}
		return draw(screen, size);
	}

	private static Buffer draw(InteractiveScreen screen, Size size) {
		var buffer = Buffer.empty(Rect.of(size.width(), size.height()));
		screen.render(Frame.forTesting(buffer));
		return buffer;
	}

	private static String line(Buffer buffer, int y) {
		var text = new StringBuilder();
		for (int x = 0; x < buffer.area().width(); x++) {
			text.append(buffer.get(x, y).symbol());
		}
		return text.toString();
	}

	@Test
	void ctrlCClearsInputAndCtrlDDoesNotExitOrDeleteText() throws Exception {
		var inputs = new CopyOnWriteArrayList<String>();
		var screen = new InteractiveScreen("dbui@localhost:5432/dbui", inputs::add);
		try (var terminal = TuiTestRunner.runTest((event, runner) -> screen.handle(event), screen::render, new Size(60, 20))) {
			terminal.pilot().press("x");
			terminal.pilot().press('c', KeyModifiers.CTRL);
			terminal.pilot().press("k", "e", "p", "t");
			terminal.pilot().press(KeyCode.HOME);
			terminal.pilot().press('d', KeyModifiers.CTRL);
			terminal.pilot().press(KeyCode.ENTER);
			terminal.pilot().pause();
			assertThat(inputs).containsExactly("kept");
			terminal.pilot().quit();
		}
	}

	@Test
	void ctrlJInsertsANewlineInsteadOfSending() throws Exception {
		var inputs = new CopyOnWriteArrayList<String>();
		var screen = new InteractiveScreen("dbui@localhost:5432/dbui", inputs::add);
		try (var terminal = TuiTestRunner.runTest((event, runner) -> screen.handle(event), screen::render, new Size(60, 20))) {
			terminal.pilot().press("a");
			terminal.pilot().press('j', KeyModifiers.CTRL);
			terminal.pilot().press("b");
			terminal.pilot().pause();
			assertThat(inputs).isEmpty();
			terminal.pilot().press(KeyCode.ENTER);
			terminal.pilot().pause();
			assertThat(inputs).containsExactly("a\nb");
			terminal.pilot().quit();
		}
	}

	@Test
	void enterSendsOneInputAndClearsEditor() throws Exception {
		var inputs = new CopyOnWriteArrayList<String>();
		var screen = new InteractiveScreen("dbui@localhost:5432/dbui", inputs::add);
		try (var terminal = TuiTestRunner.runTest((event, runner) -> screen.handle(event), screen::render, new Size(60, 20))) {
			terminal.pilot().press("s", "e", "l", "e", "c", "t", " ", "1");
			terminal.pilot().press(KeyCode.ENTER);
			terminal.pilot().pause();
			assertThat(inputs).containsExactly("select 1");
			terminal.pilot().press(KeyCode.ENTER);
			terminal.pilot().pause();
			assertThat(inputs).containsExactly("select 1");
			terminal.pilot().quit();
		}
	}
}
