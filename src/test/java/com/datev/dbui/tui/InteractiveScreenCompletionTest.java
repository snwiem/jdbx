package com.datev.dbui.tui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.datev.dbui.completion.Candidate;
import com.datev.dbui.completion.Candidate.Kind;
import com.datev.dbui.completion.Completion;
import com.datev.dbui.config.KeyBindings;
import com.datev.dbui.config.Settings;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.layout.Size;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import dev.tamboui.tui.event.PasteEvent;
import dev.tamboui.tui.event.TickEvent;
import dev.tamboui.tui.pilot.TuiTestRunner;

/** Terminal glue of Completion: when the popup opens, how keys move it, and that Enter does not send under it. */
class InteractiveScreenCompletionTest {

	private final List<String> sent = new CopyOnWriteArrayList<>();
	private final List<String> requests = new ArrayList<>();
	private CompletableFuture<Completion> answer;
	private final InteractiveScreen screen = new InteractiveScreen("dbui@localhost:5432/dbui", sent::add);

	private static Candidate column(String name) {
		return new Candidate(name, name, Kind.COLUMN);
	}

	/** Completion answers for the word at the end of the text: three candidates sharing no prefix. */
	private void backendAnswers(Completion completion) {
		answer = CompletableFuture.completedFuture(completion);
		screen.completion((text, cursor) -> {
			requests.add(text + "@" + cursor);
			return answer;
		});
	}

	private static Completion three() {
		return new Completion(0, 0, List.of(column("alpha"), column("beta"), column("gamma")));
	}

	private void tab() {
		screen.handle(KeyEvent.ofKey(KeyCode.TAB));
	}

	private static Buffer draw(InteractiveScreen screen) {
		var buffer = Buffer.empty(Rect.of(60, 20));
		screen.render(Frame.forTesting(buffer));
		return buffer;
	}

	private static String screenText(Buffer buffer) {
		var text = new StringBuilder();
		for (int y = 0; y < buffer.area().height(); y++) {
			for (int x = 0; x < buffer.area().width(); x++) {
				text.append(buffer.get(x, y).symbol());
			}
			text.append('\n');
		}
		return text.toString();
	}

	@Test
	void nothingOpensWhileTheUserTypesOrPressesOtherKeys() {
		backendAnswers(three());
		screen.handle(new PasteEvent("select * from "));
		for (char c : "ord".toCharArray()) {
			screen.handle(KeyEvent.ofChar(c));
		}
		screen.handle(KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.SHIFT));
		screen.handle(KeyEvent.ofKey(KeyCode.DOWN));
		screen.handle(KeyEvent.ofChar(' '));
		screen.handle(KeyEvent.ofChar(' ', KeyModifiers.CTRL));
		screen.handle(KeyEvent.ofChar('/', KeyModifiers.CTRL));
		screen.handle(KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.CTRL));
		screen.handle(KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.ALT));
		assertThat(requests).isEmpty();
		assertThat(screenText(draw(screen))).doesNotContain("alpha", "beta");
	}

	@Test
	void tabAsksForCompletionWithTextAndCursorOffsetAndOpensThePopup() {
		backendAnswers(three());
		screen.handle(new PasteEvent("select\n1 f"));
		screen.handle(KeyEvent.ofKey(KeyCode.LEFT));
		tab();
		assertThat(requests).containsExactly("select\n1 f@9");
		assertThat(screenText(draw(screen))).contains("alpha", "beta", "gamma");
	}

	@Test
	void shiftTabDoesNotRequestCompletion() {
		var cycles = new AtomicInteger();
		var screen = new InteractiveScreen("connection", sent::add, () -> { },
			Settings.defaults(), cycles::incrementAndGet);
		screen.completion((text, cursor) -> {
			requests.add(text);
			return CompletableFuture.completedFuture(three());
		});
		screen.handle(KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.SHIFT));
		assertThat(cycles.get()).isEqualTo(1);
		assertThat(requests).isEmpty();
	}

	@Test
	void configuredCompletionAndOrientationKeysReplaceTheDefaults() {
		var bindings = KeyBindings.from(Map.of("orientation-cycle", "alt-o", "orientation-fallback", "ctrl-t",
			"complete", "ctrl-space", "complete-alternate", "alt-c"));
		var cycles = new AtomicInteger();
		var screen = new InteractiveScreen("connection", sent::add, () -> { },
			Settings.defaults().withKeyBindings(bindings), cycles::incrementAndGet);
		screen.completion((text, cursor) -> {
			requests.add(text);
			return CompletableFuture.completedFuture(three());
		});
		screen.handle(KeyEvent.ofKey(KeyCode.TAB));
		screen.handle(KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.SHIFT));
		screen.handle(KeyEvent.ofChar('/', KeyModifiers.ALT));
		screen.handle(KeyEvent.ofChar('o', KeyModifiers.CTRL));
		assertThat(requests).isEmpty();
		assertThat(cycles.get()).isZero();
		screen.handle(KeyEvent.ofChar(' ', KeyModifiers.CTRL));
		assertThat(requests).hasSize(1);
		screen.handle(KeyEvent.ofChar('o', KeyModifiers.ALT)); // closes the popup and cycles
		screen.handle(KeyEvent.ofChar('t', KeyModifiers.CTRL));
		assertThat(cycles.get()).isEqualTo(2);
		screen.handle(KeyEvent.ofChar('c', KeyModifiers.ALT));
		assertThat(requests).hasSize(2);
		screen.handle(KeyEvent.ofKey(KeyCode.TAB)); // popup Tab stays fixed
		assertThat(requests).hasSize(2);
		screen.handle(KeyEvent.ofKey(KeyCode.ENTER));
		assertThat(sent).singleElement().asString().endsWith("alpha");
	}

	@Test
	void configuredShiftTabCanRequestCompletionWhenOrientationUsesAnotherKey() {
		var bindings = KeyBindings.from(Map.of("orientation-cycle", "alt-o", "complete", "shift-tab"));
		var cycles = new AtomicInteger();
		var screen = new InteractiveScreen("connection", sent::add, () -> { },
			Settings.defaults().withKeyBindings(bindings), cycles::incrementAndGet);
		screen.completion((text, cursor) -> {
			requests.add(text);
			return CompletableFuture.completedFuture(three());
		});
		screen.handle(KeyEvent.ofKey(KeyCode.TAB));
		assertThat(requests).isEmpty();
		screen.handle(KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.SHIFT));
		assertThat(requests).hasSize(1);
		assertThat(cycles.get()).isZero();
	}

	@Test
	void tabWithAnOpenPopupAcceptsAndDoesNotRequestAgain() {
		backendAnswers(new Completion(0, 0, List.of(column("alpha"), column("beta"))));
		tab();
		tab();
		assertThat(requests).hasSize(1);
		assertThat(screenText(draw(screen))).contains("alpha").doesNotContain("beta");
	}

	@Test
	void aPastedTabDoesNotRequestCompletion() {
		backendAnswers(three());
		screen.handle(new PasteEvent("a\tb"));
		assertThat(requests).isEmpty();
	}

	@Test
	void ctrlSpaceIsNoLongerADefaultKey() {
		backendAnswers(three());
		screen.handle(KeyEvent.ofChar(' ', KeyModifiers.CTRL));
		assertThat(requests).isEmpty();
	}

	@Test
	void altSlashIsTheFallbackKey() {
		backendAnswers(three());
		screen.handle(KeyEvent.ofChar('/', KeyModifiers.ALT));
		assertThat(requests).hasSize(1);
		assertThat(screenText(draw(screen))).contains("alpha");
	}

	@Test
	void downAndTabAcceptTheSelectedCandidate() {
		backendAnswers(three());
		screen.handle(new PasteEvent("x "));
		backendAnswers(new Completion(2, 2, List.of(column("alpha"), column("beta"), column("gamma"))));
		tab();
		screen.handle(KeyEvent.ofKey(KeyCode.DOWN));
		screen.handle(KeyEvent.ofKey(KeyCode.TAB));
		screen.handle(KeyEvent.ofChar('!'));
		assertThat(screenText(draw(screen))).contains("x beta!").doesNotContain("gamma");
	}

	@Test
	void shiftTabClosesCompletionAndCyclesOrientationInsteadOfAccepting() {
		var cycles = new AtomicInteger();
		var screen = new InteractiveScreen("connection", sent::add, () -> { },
			Settings.defaults(), cycles::incrementAndGet);
		screen.completion((text, cursor) -> CompletableFuture.completedFuture(three()));
		tab();
		screen.handle(KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.SHIFT));
		assertThat(cycles.get()).isEqualTo(1);
		screen.handle(KeyEvent.ofKey(KeyCode.ENTER));
		assertThat(sent).isEmpty();
	}

	@Test
	void enterAcceptsAndDoesNotSendWhileThePopupIsOpen() {
		backendAnswers(new Completion(0, 0, List.of(column("alpha"), column("beta"))));
		tab();
		screen.handle(KeyEvent.ofKey(KeyCode.UP));
		screen.handle(KeyEvent.ofKey(KeyCode.ENTER));
		assertThat(sent).isEmpty();
		assertThat(screenText(draw(screen))).contains("beta").doesNotContain("alpha");
		screen.handle(KeyEvent.ofKey(KeyCode.ENTER));
		assertThat(sent).containsExactly("beta");
	}

	@Test
	void escapeClosesThePopupWithoutChangingTheText() {
		backendAnswers(three());
		screen.handle(new PasteEvent("ab"));
		tab();
		screen.handle(KeyEvent.ofKey(KeyCode.ESCAPE));
		var text = screenText(draw(screen));
		assertThat(text).doesNotContain("alpha").contains("ab");
		screen.handle(KeyEvent.ofKey(KeyCode.ENTER));
		assertThat(sent).containsExactly("ab");
	}

	@Test
	void anyOtherKeyClosesThePopupAndEditsNormally() {
		backendAnswers(three());
		tab();
		screen.handle(KeyEvent.ofChar('q'));
		var text = screenText(draw(screen));
		assertThat(text).doesNotContain("alpha").contains("q");
	}

	@Test
	void aSingleCandidateIsInsertedWithoutAPopup() {
		backendAnswers(new Completion(0, 2, List.of(column("customers"))));
		screen.handle(new PasteEvent("cu"));
		tab();
		var text = screenText(draw(screen));
		assertThat(text).contains("customers");
		screen.handle(KeyEvent.ofKey(KeyCode.ENTER));
		assertThat(sent).containsExactly("customers");
	}

	@Test
	void theCommonPrefixIsInsertedFirstAndThePopupListsTheCandidates() {
		backendAnswers(new Completion(0, 1, List.of(column("customers"), column("customer_notes"))));
		screen.handle(new PasteEvent("c"));
		tab();
		var text = screenText(draw(screen));
		assertThat(text).contains("customer_notes", "customers");
		screen.handle(KeyEvent.ofKey(KeyCode.DOWN));
		screen.handle(KeyEvent.ofKey(KeyCode.TAB));
		var after = screenText(draw(screen));
		assertThat(after).contains("customer_notes").doesNotContain("customers");
	}

	@Test
	void noCandidatesShowANoteInTheStatusBarUntilTheNextKey() {
		backendAnswers(new Completion(0, 0, List.of()));
		tab();
		assertThat(screenText(draw(screen))).contains("no completions");
		screen.handle(KeyEvent.ofChar('a'));
		assertThat(screenText(draw(screen))).doesNotContain("no completions");
	}

	@Test
	void whileTheSchemaLoadsTheStatusBarSaysSoAndTheListAppearsWhenItIsReady() {
		backendAnswers(three());
		answer = new CompletableFuture<>();
		tab();
		assertThat(screenText(draw(screen))).contains("loading schema…").doesNotContain("alpha");
		screen.handle(TickEvent.of(0));
		assertThat(screenText(draw(screen))).contains("loading schema…");
		answer.complete(three());
		assertThat(screen.handle(TickEvent.of(1))).isTrue();
		assertThat(screenText(draw(screen))).contains("alpha").doesNotContain("loading schema…");
	}

	@Test
	void aKeyWhileLoadingCancelsTheRequestAndALateAnswerIsIgnored() {
		backendAnswers(three());
		answer = new CompletableFuture<>();
		tab();
		screen.handle(KeyEvent.ofChar('z'));
		answer.complete(three());
		screen.handle(TickEvent.of(1));
		assertThat(screenText(draw(screen))).doesNotContain("alpha", "loading schema…");
	}

	@Test
	void popupShowsEightRowsAtMost() {
		backendAnswers(new Completion(0, 0, IntStream.range(0, 12).mapToObj(i -> column("c" + (10 + i))).toList()));
		tab();
		var text = screenText(draw(screen));
		assertThat(text).contains("c17").doesNotContain("c18");
	}

	@Test
	void tabThroughThePilotOpensThePopupAndEscapeClosesIt() throws Exception {
		backendAnswers(three());
		try (var terminal = TuiTestRunner.runTest((event, runner) -> screen.handle(event), screen::render, new Size(60, 20))) {
			terminal.pilot().press(KeyCode.TAB);
			terminal.pilot().pause();
			assertThat(requests).hasSize(1);
			terminal.pilot().press(KeyCode.ESCAPE);
			terminal.pilot().pause();
			terminal.pilot().quit();
		}
	}

	@Test
	void ctrlCClosesThePopupAndClearsTheEditor() {
		backendAnswers(three());
		screen.handle(new PasteEvent("abc"));
		tab();
		screen.handle(KeyEvent.ofChar('c', KeyModifiers.CTRL));
		assertThat(screenText(draw(screen))).doesNotContain("alpha", "abc");
	}
}
