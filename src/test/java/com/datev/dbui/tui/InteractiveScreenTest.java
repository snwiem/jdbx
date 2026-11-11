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
	void shiftTabAndCtrlOCycleOrientationWithoutSendingOrClearingTheEditorDraft() throws Exception {
		var inputs = new CopyOnWriteArrayList<String>();
		var cycles = new java.util.concurrent.atomic.AtomicInteger();
		var screen = new InteractiveScreen("dbui@localhost:5432/dbui", inputs::add, () -> { },
			com.datev.dbui.config.Settings.defaults(), cycles::incrementAndGet);
		screen.history(List.of("select 1"));
		try (var terminal = TuiTestRunner.runTest((event, runner) -> screen.handle(event), screen::render, new Size(120, 20))) {
			terminal.runner().dispatch(new PasteEvent("draft"));
			terminal.pilot().press(KeyCode.UP);
			terminal.pilot().press(KeyCode.TAB, KeyModifiers.SHIFT);
			terminal.pilot().pause();
			assertThat(cycles.get()).isEqualTo(1);
			assertThat(inputs).isEmpty();
			screen.settings(com.datev.dbui.config.Settings.defaults().withOutput("table", "vertical"));
			assertThat(line(draw(screen, new Size(120, 20)), 18)).contains("Orientation: vertical");
			terminal.pilot().press('o', KeyModifiers.CTRL);
			terminal.pilot().pause();
			assertThat(cycles.get()).isEqualTo(2);
			assertThat(inputs).isEmpty();
			screen.busy(true);
			terminal.pilot().press(KeyCode.TAB, KeyModifiers.SHIFT);
			terminal.pilot().press('o', KeyModifiers.CTRL);
			terminal.pilot().pause();
			assertThat(cycles.get()).isEqualTo(2);
			screen.busy(false);
			terminal.pilot().press(KeyCode.DOWN);
			terminal.pilot().press(KeyCode.ENTER);
			terminal.pilot().pause();
			assertThat(inputs).endsWith("draft");
			terminal.pilot().quit();
		}
	}

	@Test
	void statusBarShowsTheCurrentFormatAndOrientation() {
		var screen = new InteractiveScreen("dbui@localhost:5432/dbui", input -> { }, () -> { },
			com.datev.dbui.config.Settings.defaults().withOutput("csv", "vertical"));
		assertThat(line(draw(screen, new Size(140, 20)), 18)).contains("Format: csv", "Orientation: vertical");
		assertThat(line(draw(screen, new Size(140, 20)), 19)).contains("Row limit: 1000");
	}

	@Test
	void anEmptyFrozenCsvResultDoesNotFallBackToTable() {
		var screen = new InteractiveScreen("dbui@localhost:5432/dbui", input -> { });
		var result = new Result(List.of(), List.of(List.of()));
		var block = MessageBlock.result(result, com.datev.dbui.config.Settings.defaults().withOutput("csv", "vertical"));
		assertThat(block.text()).isEmpty();
		screen.update(List.of(MessageBlock.info("before"), block, MessageBlock.info("after")));
		assertThat(line(draw(screen, new Size(100, 20)), 7)).contains("after");
	}

	@Test
	void transcriptDrawsTheFormatFrozenOnEachResultInsteadOfAlwaysUsingTable() {
		var screen = new InteractiveScreen("dbui@localhost:5432/dbui", input -> { });
		var settings = com.datev.dbui.config.Settings.defaults().withOutput("json", "vertical");
		screen.update(List.of(MessageBlock.result(new Result(List.of("id"), List.of(List.of(7))), settings)));
		var buffer = draw(screen, new Size(80, 20));
		var transcript = IntStream.range(0, 15).mapToObj(y -> line(buffer, y)).collect(java.util.stream.Collectors.joining("\n"));
		assertThat(transcript).contains("{\"id\":7}", "[", "]").doesNotContain("--");
	}

	@Test
	void screenDrawsConfiguredFormatterOptionsAndCurrentExecutionStatus() {
		var settings = new com.datev.dbui.config.Settings(5, null, "table", "horizontal", false,
			"∅", "↵", 5, java.time.ZoneId.of("UTC"));
		var screen = new InteractiveScreen("dbui@localhost:5432/dbui", input -> { }, () -> { }, settings);
		screen.update(List.of(MessageBlock.result(new Result(List.of("null", "lines", "long"),
			List.of(java.util.Arrays.asList(null, "a\nb", "abcdefgh"))))));
		assertThat(line(draw(screen, new Size(100, 20)), 19)).contains("Row limit: 5");
		screen.status(new com.datev.dbui.session.ExecutionStatus(5, true, java.time.Duration.ofSeconds(12),
			new com.datev.dbui.session.ExecutionStatus.ResultSummary(5, java.time.Duration.ofMillis(123), true)));
		var buffer = draw(screen, new Size(100, 20));
		assertThat(line(buffer, 19)).contains("Row limit: 5 · running… 12 s · 5 rows · 0.123 s · truncated");
		var all = IntStream.range(0, 15).mapToObj(y -> line(buffer, y)).collect(java.util.stream.Collectors.joining("\n"));
		assertThat(all).contains("∅", "a↵b", "abcd…").doesNotContain("abcdefgh");
	}

	@Test
	void statusBarShowsModeAlsoWithoutColor() {
		var screen = new InteractiveScreen("dbui@localhost:5432/dbui", input -> { }, EditorKeys.defaults(), false);
		assertThat(line(draw(screen, new Size(100, 20)), 19)).contains("read-write · Row limit");
		screen.status(new com.datev.dbui.session.ExecutionStatus(5, false, java.time.Duration.ZERO, null, true, true));
		assertThat(line(draw(screen, new Size(100, 20)), 19)).contains("read-only · Row limit");
		screen.status(new com.datev.dbui.session.ExecutionStatus(5, false, java.time.Duration.ZERO, null, true, false));
		assertThat(line(draw(screen, new Size(100, 20)), 19)).contains("not connected · Row limit");
	}

	@Test
	void ctrlCCancelsWhileLockedAndOtherKeysAndPasteAreIgnored() throws Exception {
		var inputs = new CopyOnWriteArrayList<String>();
		var cancellations = new java.util.concurrent.atomic.AtomicInteger();
		var screen = new InteractiveScreen("dbui@localhost:5432/dbui", inputs::add, cancellations::incrementAndGet,
			com.datev.dbui.config.Settings.defaults());
		screen.busy(true);
		try (var terminal = TuiTestRunner.runTest((event, runner) -> screen.handle(event), screen::render, new Size(80, 20))) {
			terminal.pilot().press("x");
			terminal.runner().dispatch(new PasteEvent("must not run"));
			terminal.pilot().press(KeyCode.ENTER);
			terminal.pilot().press('d', KeyModifiers.CTRL);
			terminal.pilot().press('j', KeyModifiers.CTRL);
			terminal.pilot().press('c', KeyModifiers.CTRL);
			terminal.pilot().pause();
			assertThat(cancellations.get()).isEqualTo(1);
			screen.busy(false);
			terminal.pilot().press(KeyCode.ENTER);
			terminal.pilot().pause();
			assertThat(inputs).isEmpty();
			terminal.pilot().press('c', KeyModifiers.CTRL);
			terminal.pilot().pause();
			assertThat(cancellations.get()).isEqualTo(1);
			terminal.pilot().quit();
		}
	}

	@Test
	void resultControlStringsNeverReachRenderedCellsOrTerminalOutput() throws Exception {
		var screen = new InteractiveScreen("dbui@localhost:5432/dbui", input -> { });
		var controls = List.of("\u001b[2J", "\u001b]0;title\u0007", "\u001b[31m", "\u001b[201~", "\u001b]52;c;aGVsbG8=\u0007");
		screen.update(List.of(MessageBlock.result(new Result(List.of("control"),
			controls.stream().map(value -> List.<Object>of(value)).toList()))));
		var buffer = draw(screen, new Size(100, 24));
		for (int y = 0; y < 24; y++) {
			assertThat(line(buffer, y)).doesNotContain("\u001b", "\u0007");
		}
		// Disable the legitimate Kitty negotiation ESC, so raw output here can only be application data.
		var terminalCells = new StringBuilder();
		var backend = new dev.tamboui.terminal.TestBackend(100, 24) {
			@Override
			public void draw(dev.tamboui.buffer.DiffResult diff) throws java.io.IOException {
				for (int i = 0; i < diff.size(); i++) {
					terminalCells.append(diff.getCell(i).symbol());
				}
				super.draw(diff);
			}
		};
		var config = dev.tamboui.tui.TuiConfig.builder().backend(backend)
			.keyboardProtocol(dev.tamboui.terminal.KeyboardProtocol.LEGACY).build();
		try (var terminal = TuiTestRunner.runTest((event, runner) -> screen.handle(event), screen::render, new Size(100, 24), config)) {
			terminal.pilot().pause();
			assertThat(terminalCells.toString()).contains("control", "title").doesNotContain("\u001b", "\u0007");
			assertThat(terminal.backend().rawOutput()).doesNotContain("\u001b", "\u0007");
			terminal.pilot().quit();
		}
	}

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

	@Test
	void startupConfigErrorsRemainErrorBandsWhenTheSessionRefreshes() {
		var screen = new InteractiveScreen("connection", input -> { }, EditorKeys.defaults(), false);
		screen.startupErrors(List.of("config.yaml: key-bindings.newline: invalid key 'bad'. Default key bindings apply."));
		screen.update(List.of(MessageBlock.info("connected")));
		var buffer = draw(screen, new Size(140, 20));
		assertThat(line(buffer, 1)).contains("error: config.yaml", "key-bindings.newline", "'bad'", "Default key bindings apply.");
		assertThat(buffer.get(0, 1).style()).isEqualTo(dev.tamboui.style.Style.EMPTY.bold().reversed());
		assertThat(line(buffer, 4)).contains("connected");
	}

	@Test
	void withoutColorTheWholeScreenWritesNoColorCodes() {
		var screen = new InteractiveScreen("dbui@localhost:5432/dbui", input -> { }, EditorKeys.defaults(), false);
		screen.update(List.of(MessageBlock.input("select 1"), MessageBlock.info("CREATE TABLE"), MessageBlock.error("bad")));
		var buffer = draw(screen, new Size(40, 14));
		for (int y = 0; y < 14; y++) {
			for (int x = 0; x < 40; x++) {
				assertThat(buffer.get(x, y).style().fg()).as("fg at %d,%d", x, y).isEmpty();
				assertThat(buffer.get(x, y).style().bg()).as("bg at %d,%d", x, y).isEmpty();
			}
		}
		assertThat(line(buffer, 12)).contains("dbui@localhost");
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
	void statusBarShowsTheConfiguredNewlineKey(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
		java.nio.file.Files.writeString(dir.resolve("config.yaml"), "key-bindings:\n  newline: alt-n\n");
		var screen = new InteractiveScreen("connection", input -> { }, () -> { },
			new com.datev.dbui.config.ConfigLoader(dir, java.util.Map.of()).settings());
		assertThat(line(draw(screen, new Size(140, 20)), 19)).contains("Alt-N newline").doesNotContain("Ctrl-J");
	}

	@Test
	void configuredNewlineKeyWorksThroughThePilot(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
		java.nio.file.Files.writeString(dir.resolve("config.yaml"), "key-bindings:\n  newline: alt-n\n");
		var settings = new com.datev.dbui.config.ConfigLoader(dir, java.util.Map.of()).settings();
		var inputs = new CopyOnWriteArrayList<String>();
		var screen = new InteractiveScreen("connection", inputs::add, () -> { }, settings);
		try (var terminal = TuiTestRunner.runTest((event, runner) -> screen.handle(event), screen::render, new Size(60, 20))) {
			terminal.pilot().press("a");
			terminal.pilot().press('n', KeyModifiers.ALT);
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
	void changedNewlineDoesNotKeepCtrlJAsAnUndocumentedNewline(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
		java.nio.file.Files.writeString(dir.resolve("config.yaml"), "key-bindings:\n  newline: alt-n\n");
		var inputs = new CopyOnWriteArrayList<String>();
		var screen = new InteractiveScreen("connection", inputs::add, () -> { },
			new com.datev.dbui.config.ConfigLoader(dir, java.util.Map.of()).settings());
		screen.handle(new PasteEvent("a"));
		screen.handle(KeyEvent.ofChar('j', KeyModifiers.CTRL));
		screen.handle(new PasteEvent("b"));
		screen.handle(KeyEvent.ofKey(KeyCode.ENTER));
		assertThat(inputs).containsExactly("ab");
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.MethodSource("configuredNewlineKeys")
	void supportedKeyNamesProduceANewline(String name, KeyEvent key,
			@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
		java.nio.file.Files.writeString(dir.resolve("config.yaml"), """
			key-bindings:
			  orientation-cycle: alt-o
			  complete: alt-c
			  complete-alternate: alt-e
			  newline: %s
			""".formatted(name));
		var inputs = new CopyOnWriteArrayList<String>();
		var screen = new InteractiveScreen("connection", inputs::add, () -> { },
			new com.datev.dbui.config.ConfigLoader(dir, java.util.Map.of()).settings());
		screen.handle(new PasteEvent("a"));
		screen.handle(key);
		screen.handle(new PasteEvent("b"));
		screen.handle(KeyEvent.ofKey(KeyCode.ENTER));
		assertThat(inputs).containsExactly("a\nb");
	}

	static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> configuredNewlineKeys() {
		return java.util.stream.Stream.of(
			org.junit.jupiter.params.provider.Arguments.of("tab", KeyEvent.ofKey(KeyCode.TAB)),
			org.junit.jupiter.params.provider.Arguments.of("shift-tab", KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.SHIFT)),
			org.junit.jupiter.params.provider.Arguments.of("shift-enter", KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.SHIFT)),
			org.junit.jupiter.params.provider.Arguments.of("ctrl-tab", KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.CTRL)),
			org.junit.jupiter.params.provider.Arguments.of("alt-tab", KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.ALT)),
			org.junit.jupiter.params.provider.Arguments.of("ctrl-enter", KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.CTRL)),
			org.junit.jupiter.params.provider.Arguments.of("alt-enter", KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.ALT)),
			org.junit.jupiter.params.provider.Arguments.of("ctrl-space", KeyEvent.ofChar(' ', KeyModifiers.CTRL)),
			org.junit.jupiter.params.provider.Arguments.of("alt-space", KeyEvent.ofChar(' ', KeyModifiers.ALT)),
			org.junit.jupiter.params.provider.Arguments.of("ctrl-/", KeyEvent.ofChar('/', KeyModifiers.CTRL)),
			org.junit.jupiter.params.provider.Arguments.of("alt-/", KeyEvent.ofChar('/', KeyModifiers.ALT)),
			org.junit.jupiter.params.provider.Arguments.of("CTRL-N", KeyEvent.ofChar('n', KeyModifiers.CTRL)),
			org.junit.jupiter.params.provider.Arguments.of("alt-n", KeyEvent.ofChar('n', KeyModifiers.ALT)));
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

	private static InteractiveScreen screenWithHistory(java.util.function.Consumer<String> send, String... entries) {
		var screen = new InteractiveScreen("dbui@localhost:5432/dbui", send);
		screen.history(List.of(entries));
		return screen;
	}

	private static void type(InteractiveScreen screen, String text) {
		text.chars().forEach(c -> screen.handle(KeyEvent.ofChar((char) c)));
	}

	@Test
	void upAndDownUseHistoryOnlyAtTheFirstAndLastLineOfTheEditor() {
		var inputs = new CopyOnWriteArrayList<String>();
		var screen = screenWithHistory(inputs::add, "select 1", "select 2\nfrom t");
		screen.handle(new PasteEvent("x\ny"));
		screen.handle(KeyEvent.ofKey(KeyCode.UP)); // moves inside the buffer, history stays out
		screen.handle(KeyEvent.ofKey(KeyCode.UP)); // first line: newest history entry (multi-line)
		draw(screen, new Size(60, 20));
		screen.handle(KeyEvent.ofKey(KeyCode.UP)); // inside the entry: first line of the entry
		screen.handle(KeyEvent.ofKey(KeyCode.DOWN)); // back to its last line
		screen.handle(KeyEvent.ofKey(KeyCode.DOWN)); // below the last line: the draft
		screen.handle(KeyEvent.ofKey(KeyCode.ENTER));
		assertThat(inputs).containsExactly("x\ny");
		screen.handle(KeyEvent.ofKey(KeyCode.UP));
		screen.handle(KeyEvent.ofKey(KeyCode.UP)); // an entry that was inside, then at the top
		screen.handle(KeyEvent.ofKey(KeyCode.UP));
		screen.handle(KeyEvent.ofKey(KeyCode.ENTER));
		assertThat(inputs.getLast()).isEqualTo("select 1");
	}

	@Test
	void ctrlROpensASearchLineAndEnterAcceptsTheMatchWithoutSendingIt() {
		var inputs = new CopyOnWriteArrayList<String>();
		var screen = screenWithHistory(inputs::add, "select 1", "insert into t values (1)", "update t set a = 2");
		type(screen, "draft");
		screen.handle(KeyEvent.ofChar('r', KeyModifiers.CTRL));
		type(screen, "ins");
		var buffer = draw(screen, new Size(60, 20));
		assertThat(line(buffer, 17)).contains("reverse-search").contains("ins");
		assertThat(line(buffer, 16)).contains("insert into t values (1)");
		screen.handle(KeyEvent.ofKey(KeyCode.ENTER));
		assertThat(inputs).isEmpty();
		assertThat(line(draw(screen, new Size(60, 20)), 17)).doesNotContain("reverse-search");
		screen.handle(KeyEvent.ofKey(KeyCode.ENTER));
		assertThat(inputs).containsExactly("insert into t values (1)");
	}

	@Test
	void ctrlCCancelsTheStatementOrSearchOrClearsIdleInputWithoutColliding() {
		var inputs = new CopyOnWriteArrayList<String>();
		var cancellations = new java.util.concurrent.atomic.AtomicInteger();
		var screen = new InteractiveScreen("dbui@localhost:5432/dbui", inputs::add, cancellations::incrementAndGet,
			com.datev.dbui.config.Settings.defaults());
		screen.history(List.of("select 1"));
		type(screen, "draft");
		screen.handle(KeyEvent.ofChar('r', KeyModifiers.CTRL));
		type(screen, "sel");
		screen.handle(KeyEvent.ofChar('c', KeyModifiers.CTRL));
		var buffer = draw(screen, new Size(60, 20));
		assertThat(line(buffer, 17)).doesNotContain("reverse-search");
		assertThat(line(buffer, 16)).startsWith("draft");
		assertThat(cancellations.get()).isZero();
		screen.handle(KeyEvent.ofKey(KeyCode.ENTER));
		assertThat(inputs).containsExactly("draft");

		screen.busy(true);
		screen.handle(KeyEvent.ofChar('r', KeyModifiers.CTRL));
		screen.handle(KeyEvent.ofKey(KeyCode.UP));
		screen.handle(new PasteEvent("ignored"));
		screen.handle(KeyEvent.ofChar('c', KeyModifiers.CTRL));
		assertThat(cancellations.get()).isEqualTo(1);
		assertThat(line(draw(screen, new Size(60, 20)), 17)).doesNotContain("reverse-search");
		screen.busy(false);
		screen.handle(KeyEvent.ofKey(KeyCode.ENTER));
		assertThat(inputs).containsExactly("draft");

		type(screen, "discard");
		screen.handle(KeyEvent.ofChar('c', KeyModifiers.CTRL));
		screen.handle(KeyEvent.ofKey(KeyCode.ENTER));
		assertThat(inputs).containsExactly("draft");
		assertThat(cancellations.get()).isEqualTo(1);
	}

	@Test
	void escapeCancelsTheSearchAndRestoresTheDraft() {
		var inputs = new CopyOnWriteArrayList<String>();
		var screen = screenWithHistory(inputs::add, "select 1", "insert into t values (1)");
		type(screen, "draft");
		screen.handle(KeyEvent.ofChar('r', KeyModifiers.CTRL));
		type(screen, "sel");
		screen.handle(KeyEvent.ofKey(KeyCode.ESCAPE));
		screen.handle(KeyEvent.ofKey(KeyCode.ENTER));
		assertThat(inputs).containsExactly("draft");
	}
}
