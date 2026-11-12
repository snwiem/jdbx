package org.syracus.jdbx.tui;

import static org.syracus.jdbx.config.ProfileDialogModel.Field.*;
import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.syracus.jdbx.config.KeyBindings;
import org.syracus.jdbx.config.Profile;
import org.syracus.jdbx.config.ProfileDialogModel;
import org.syracus.jdbx.session.MessageBlock;
import org.syracus.jdbx.session.SessionInteraction;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Position;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Modifier;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.*;

/** Screen tests of the Profile dialog, the password prompt and the removal confirmation (issue 0022). */
class ProfileDialogScreenTest {
	private final List<Boolean> saved = new ArrayList<>();
	private final List<String> passwords = new ArrayList<>();
	private final AtomicInteger cancelled = new AtomicInteger();

	private InteractiveScreen screen(boolean color) {
		var screen = new InteractiveScreen("not connected", input -> { }, KeyBindings.defaults(), color);
		screen.onInteraction(saved::add, passwords::add, cancelled::incrementAndGet);
		var blocks = new ArrayList<MessageBlock>();
		for (int i = 0; i < 40; i++) blocks.add(MessageBlock.info("TRANSCRIPT" + i));
		screen.update(blocks);
		return screen;
	}

	private static ProfileDialogModel model(String name, String host, String password) {
		return new ProfileDialogModel(new Profile(name, null, host, null, "db", "alice", null, null, null, Map.of()), password);
	}

	private record Drawn(Buffer buffer, Frame frame, int width, int height) {
		String row(int y) {
			var text = new StringBuilder();
			for (int x = 0; x < width; x++) { var cell = buffer.get(x, y); if (!cell.isContinuation()) text.append(cell.symbol().isEmpty() ? " " : cell.symbol()); }
			return text.toString();
		}
		String text() { var all = new StringBuilder(); for (int y = 0; y < height; y++) all.append(row(y)).append('\n'); return all.toString(); }
		int rowOf(String needle) { for (int y = 0; y < height; y++) if (row(y).contains(needle)) return y; return -1; }
		Position cursor() { return frame.cursorPosition().orElse(null); }
	}

	private static Drawn draw(InteractiveScreen screen, int width, int height) {
		var buffer = Buffer.empty(Rect.of(width, height));
		var frame = Frame.forTesting(buffer);
		screen.render(frame);
		return new Drawn(buffer, frame, width, height);
	}

	private static void press(InteractiveScreen screen, KeyCode code, KeyModifiers... modifiers) {
		screen.handle(modifiers.length == 0 ? KeyEvent.ofKey(code) : KeyEvent.ofKey(code, modifiers[0]));
	}

	private static void type(InteractiveScreen screen, String text) { screen.handle(new PasteEvent(text)); }

	private static void tab(InteractiveScreen screen, int times) { for (int i = 0; i < times; i++) press(screen, KeyCode.TAB); }

	@Test
	void panelIsFramedWithATitleAndFourColumnsNarrowerOnEachSide() {
		var screen = screen(true);
		screen.interaction(new SessionInteraction.Dialog(model("dev", "localhost", null), false, "dev"));
		var drawn = draw(screen, 120, 40);
		int top = drawn.rowOf("Edit Connection Profile");
		assertThat(top).isEqualTo(7);
		assertThat(drawn.row(top).substring(4, 5)).isEqualTo("╭");
		assertThat(drawn.row(top).substring(115, 116)).isEqualTo("╮");
		assertThat(drawn.row(top + 25).substring(4, 5)).isEqualTo("╰");
		assertThat(drawn.row(top + 25).substring(115, 116)).isEqualTo("╯");
		assertThat(drawn.row(top + 3).substring(4, 5)).isEqualTo("│");
		assertThat(drawn.row(top + 3).substring(115, 116)).isEqualTo("│");
	}

	@Test
	void panelIsOpaqueAndLeavesTheScreenOutsideTheFrameToTheTranscript() {
		var screen = screen(true);
		screen.interaction(new SessionInteraction.Dialog(model("dev", "localhost", null), false, "dev"));
		var drawn = draw(screen, 120, 40);
		for (int y = 8; y < 33; y++) assertThat(drawn.row(y).substring(5, 115)).doesNotContain("TRANSCRIPT");
		assertThat(drawn.row(5) + drawn.row(4) + drawn.row(3)).contains("TRANSCRIPT");
	}

	@Test
	void titleSaysWhatTheUserDoes() {
		var screen = screen(true);
		screen.interaction(new SessionInteraction.Dialog(model("dev", "localhost", null), false, "dev"));
		assertThat(draw(screen, 100, 30).text()).contains("Edit Connection Profile");
		screen.interaction(new SessionInteraction.Dialog(model(null, null, null), false, null));
		assertThat(draw(screen, 100, 30).text()).contains("New Connection Profile").doesNotContain("Edit Connection Profile");
		screen.interaction(new SessionInteraction.Dialog(model(null, "localhost", null), true, null));
		var drawn = draw(screen, 100, 30);
		assertThat(drawn.row(drawn.rowOf("─ Connect ─")).substring(4)).startsWith("╭─ Connect ─");
		assertThat(drawn.text()).doesNotContain("Connection Profile");
	}

	@Test
	void labelsShareOneColumnAndValuesShareOneLeftEdge() {
		var screen = screen(false);
		var given = new Profile("dev", null, "localhost", 5432, "db", "alice", null, null, "public", Map.of());
		screen.interaction(new SessionInteraction.Dialog(new ProfileDialogModel(given, null), false, "dev"));
		var drawn = draw(screen, 100, 30);
		int label = drawn.row(drawn.rowOf("Name")).indexOf("Name");
		for (String text : List.of("Host", "Port", "Database", "User", "Schema", "Password")) assertThat(drawn.row(drawn.rowOf(text)).indexOf(text)).isEqualTo(label);
		int value = drawn.row(drawn.rowOf("Name")).indexOf("dev");
		assertThat(drawn.row(drawn.rowOf("Host")).indexOf("localhost")).isEqualTo(value);
		assertThat(drawn.row(drawn.rowOf("Port")).indexOf("5432")).isEqualTo(value);
		assertThat(drawn.row(drawn.rowOf("Database")).indexOf("db")).isEqualTo(value);
		assertThat(drawn.row(drawn.rowOf("Schema")).indexOf("public")).isEqualTo(value);
		assertThat(value).isGreaterThan(label + "Properties".length());
	}

	@Test
	void groupsHaveHeadingsAndTheAlternativesHaveAHint() {
		var screen = screen(true);
		screen.interaction(new SessionInteraction.Dialog(model("dev", "localhost", null), false, "dev"));
		var drawn = draw(screen, 100, 30);
		var order = List.of("Profile ─", "Connection ─", "Credentials ─", "Options ─").stream().map(drawn::rowOf).toList();
		assertThat(order).isSorted().doesNotContain(-1);
		assertThat(drawn.text()).contains("Either a JDBC URL, or host, port and database", "key=value;key=value");
	}

	@Test
	void focusedFieldHasAMarkerAndTheCursorSitsAtTheEndOfItsText() {
		var screen = screen(false);
		screen.interaction(new SessionInteraction.Dialog(model("dev", "localhost", null), false, "dev"));
		var drawn = draw(screen, 100, 30);
		int row = drawn.rowOf("Name");
		assertThat(drawn.row(row)).contains("> Name");
		assertThat(drawn.row(drawn.rowOf("Host"))).doesNotContain("> Host");
		assertThat(drawn.cursor()).isEqualTo(new Position(drawn.row(row).indexOf("dev") + 3, row));
		tab(screen, 2);
		drawn = draw(screen, 100, 30);
		row = drawn.rowOf("Host");
		assertThat(drawn.row(row)).contains("> Host");
		assertThat(drawn.cursor()).isEqualTo(new Position(drawn.row(row).indexOf("localhost") + 9, row));
		press(screen, KeyCode.HOME);
		assertThat(draw(screen, 100, 30).cursor()).isEqualTo(new Position(drawn.row(row).indexOf("localhost"), row));
	}

	@Test
	void focusedFieldIsReversedInColorAndNoColor() {
		for (boolean color : List.of(true, false)) {
			var screen = screen(color);
			screen.interaction(new SessionInteraction.Dialog(model("dev", "localhost", null), false, "dev"));
			var drawn = draw(screen, 100, 30);
			int row = drawn.rowOf("Name"), column = drawn.row(row).indexOf("dev");
			var focused = drawn.buffer().get(column, row).style();
			var other = drawn.buffer().get(drawn.row(drawn.rowOf("Host")).indexOf("localhost"), drawn.rowOf("Host")).style();
			assertThat(focused).isNotEqualTo(other);
		}
	}

	@Test
	void longValuesScrollHorizontallyAndTheCursorStaysInsideTheField() {
		var screen = screen(false);
		String longName = "start-" + "x".repeat(100) + "-end";
		screen.interaction(new SessionInteraction.Dialog(model(longName, "localhost", null), false, "dev"));
		var drawn = draw(screen, 80, 24);
		int row = drawn.rowOf("> Name");
		assertThat(drawn.row(row)).contains("-end").doesNotContain("start-");
		assertThat(drawn.row(row).charAt(75)).isEqualTo('|');
		assertThat(drawn.cursor().y()).isEqualTo(row);
		assertThat(drawn.cursor().x()).isBetween(drawn.row(row).indexOf("x"), 74);
		press(screen, KeyCode.HOME);
		drawn = draw(screen, 80, 24);
		assertThat(drawn.row(row)).contains("start-").doesNotContain("-end");
		assertThat(drawn.cursor().x()).isEqualTo(drawn.row(row).indexOf("start-"));
	}

	@Test
	void typingInAFieldUpdatesTheModelAndTheScreen() {
		var screen = screen(false);
		var model = model("dev", "localhost", null);
		screen.interaction(new SessionInteraction.Dialog(model, false, "dev"));
		type(screen, "2");
		assertThat(model.value(NAME)).isEqualTo("dev2");
		assertThat(draw(screen, 100, 30).text()).contains("dev2");
	}

	@Test
	void validationErrorShowsInsideTheDialogAboveTheButtons() {
		var screen = screen(false);
		screen.interaction(new SessionInteraction.Dialog(model("dev", "localhost", null), true, "dev"));
		screen.update(List.of(MessageBlock.error("Port must be a whole number from 1 to 65535.")));
		var drawn = draw(screen, 100, 30);
		int error = drawn.rowOf("! Port must be a whole number");
		int buttons = drawn.rowOf("[ Save ]");
		assertThat(error).isPositive().isLessThan(buttons);
		assertThat(drawn.row(error).substring(4, 5)).isEqualTo("|");
		assertThat(drawn.row(error)).contains("! Port must");
		assertThat(drawn.row(buttons)).contains("[ Save ]", "[ Connect ]", "[ Cancel ]");
	}

	@Test
	void smallScreenStaysUsableAtTheMinimumSize() {
		var screen = screen(false);
		screen.interaction(new SessionInteraction.Dialog(model("dev", "localhost", null), true, "dev"));
		var drawn = draw(screen, 60, 20);
		assertThat(drawn.row(1).substring(4, 5)).isEqualTo("+");
		assertThat(drawn.row(1).substring(55, 56)).isEqualTo("+");
		assertThat(drawn.row(18).substring(4, 5)).isEqualTo("+");
		assertThat(drawn.text()).contains("[ Save ]", "[ Connect ]", "[ Cancel ]", "Ctrl-S Save", "Name", "Edit Connection Profile");
		tab(screen, 9);
		drawn = draw(screen, 60, 20);
		assertThat(drawn.text()).contains("> Properties", "key=value;key=value");
		assertThat(drawn.buffer().get(54, 2).symbol()).isEqualTo("^");
		assertThat(drawn.cursor()).isNotNull();
		screen.update(List.of(MessageBlock.error("Name is required to save a Profile.")));
		drawn = draw(screen, 60, 20);
		assertThat(drawn.text()).contains("Name is required to save a Profile.", "> Properties", "[ Save ]");
	}

	@Test
	void tooSmallScreenShowsANoteAndEscapeStillCancels() {
		var screen = screen(false);
		screen.interaction(new SessionInteraction.Dialog(model("dev", "localhost", null), true, "dev"));
		var drawn = draw(screen, 59, 20);
		assertThat(drawn.text()).contains("Terminal too small", "60x20", "Esc");
		assertThat(drawn.text()).doesNotContain("[ Save ]");
		drawn = draw(screen, 100, 19);
		assertThat(drawn.text()).contains("Terminal too small");
		press(screen, KeyCode.ESCAPE);
		assertThat(cancelled.get()).isEqualTo(1);
		draw(screen, 1, 1);
		draw(screen, 5, 3);
	}

	@Test
	void noColorUsesPlainBorderCharactersAndTextMarkers() {
		var screen = screen(false);
		screen.interaction(new SessionInteraction.Dialog(model("dev", "localhost", null), true, "dev"));
		screen.update(List.of(MessageBlock.error("Host or JDBC URL is required.")));
		var drawn = draw(screen, 100, 30);
		var panelRows = new StringBuilder();
		for (int y = 2; y < 28; y++) panelRows.append(drawn.row(y), 4, 96);
		assertThat(panelRows.toString()).doesNotContain("╭", "╮", "╰", "╯", "│", "─");
		assertThat(drawn.row(drawn.rowOf("+- Edit Connection Profile")).substring(4)).startsWith("+- Edit Connection Profile -");
		assertThat(drawn.text()).contains("Credentials -", "! Host or JDBC URL is required.", "> Name");
		assertThat(drawn.row(drawn.rowOf("  Schema"))).contains("____");
		assertThat(drawn.row(drawn.rowOf("  Read-only"))).doesNotContain("_");
		for (int y = 0; y < 30; y++) for (int x = 0; x < 100; x++) {
			var style = drawn.buffer().get(x, y).style();
			assertThat(style.fg()).isEmpty();
			assertThat(style.bg()).isEmpty();
		}
	}

	@Test
	void wideCharactersKeepTheColumnsAndTheCursorAligned() {
		var screen = screen(false);
		screen.interaction(new SessionInteraction.Dialog(model("日本語日本語", "localhost", null), false, "dev"));
		var drawn = draw(screen, 100, 30);
		int row = drawn.rowOf("> Name");
		assertThat(drawn.row(row)).contains("日本語日本語");
		assertThat(drawn.buffer().get(95, row).symbol()).isEqualTo("|");
		int start = 0;
		for (int x = 0; x < 100; x++) if (drawn.buffer().get(x, row).symbol().equals("日")) { start = x; break; }
		assertThat(drawn.cursor()).isEqualTo(new Position(start + 12, row));
		assertThat(start).isEqualTo(drawn.row(drawn.rowOf("Host")).indexOf("localhost"));
		type(screen, "x".repeat(120));
		drawn = draw(screen, 100, 30);
		assertThat(drawn.buffer().get(95, row).symbol()).isEqualTo("|");
		assertThat(drawn.cursor().x()).isLessThan(95);
	}

	@Test
	void passwordFieldShowsAFixedMaskAndNeverTheSecretOrItsLength() {
		var screen = screen(false);
		screen.interaction(new SessionInteraction.Dialog(model("dev", "localhost", "hunter2-is-long"), false, "dev"));
		var drawn = draw(screen, 100, 30);
		assertThat(drawn.row(drawn.rowOf("Password"))).contains("********").doesNotContain("*********").doesNotContain("hunter");
		tab(screen, 6);
		type(screen, "more");
		drawn = draw(screen, 100, 30);
		assertThat(drawn.text()).doesNotContain("hunter", "more");
		assertThat(drawn.row(drawn.rowOf("> Password"))).contains("********").doesNotContain("*********");
	}

	@Test
	void emptyPasswordShowsNoMask() {
		var screen = screen(false);
		screen.interaction(new SessionInteraction.Dialog(model("dev", "localhost", null), false, "dev"));
		assertThat(draw(screen, 100, 30).row(draw(screen, 100, 30).rowOf("Password"))).doesNotContain("*");
	}

	@Test
	void readOnlyIsAChoiceThatSpaceLeftAndRightChange() {
		var screen = screen(false);
		var model = model("dev", "localhost", null);
		screen.interaction(new SessionInteraction.Dialog(model, false, "dev"));
		assertThat(draw(screen, 100, 30).text()).contains("(•) default (global: off)   ( ) on   ( ) off");
		tab(screen, 7);
		press(screen, KeyCode.RIGHT);
		assertThat(model.value(READ_ONLY)).isEqualTo("true");
		assertThat(draw(screen, 100, 30).text()).contains("( ) default (global: off)   (•) on   ( ) off");
		screen.handle(KeyEvent.ofChar(' '));
		assertThat(model.value(READ_ONLY)).isEqualTo("false");
		press(screen, KeyCode.LEFT);
		assertThat(model.value(READ_ONLY)).isEqualTo("true");
		type(screen, "abc");
		screen.handle(KeyEvent.ofChar('x'));
		assertThat(model.value(READ_ONLY)).isEqualTo("true");
		assertThat(draw(screen, 60, 20).text()).contains("‹ on ›");
	}

	@Test
	void unusedAlternativeIsDimmed() {
		var screen = screen(false);
		screen.interaction(new SessionInteraction.Dialog(model("dev", "localhost", null), false, "dev"));
		var drawn = draw(screen, 100, 30);
		int urlRow = drawn.rowOf("JDBC URL"), hostRow = drawn.rowOf("Host");
		int column = drawn.row(hostRow).indexOf("localhost");
		assertThat(drawn.buffer().get(column, urlRow).style().effectiveModifiers()).contains(Modifier.DIM);
		assertThat(drawn.buffer().get(column, hostRow).style().effectiveModifiers()).doesNotContain(Modifier.DIM);
	}

	@Test
	void tabReachesTheButtonsAndEnterActivatesTheFocusedButton() {
		var screen = screen(false);
		screen.interaction(new SessionInteraction.Dialog(model("dev", "localhost", null), true, "dev"));
		tab(screen, 10);
		var drawn = draw(screen, 100, 30);
		assertThat(drawn.row(drawn.rowOf("[ Save ]"))).contains("> [ Save ]".substring(2));
		assertThat(drawn.cursor()).isNull();
		press(screen, KeyCode.ENTER);
		assertThat(saved).containsExactly(true);
		tab(screen, 1);
		press(screen, KeyCode.ENTER);
		assertThat(saved).containsExactly(true, false);
		assertThat(draw(screen, 100, 30).text()).contains("[ Save ]  > [ Connect ]    [ Cancel ]");
		tab(screen, 1);
		press(screen, KeyCode.ENTER);
		assertThat(cancelled.get()).isEqualTo(1);
	}

	@Test
	void enterOnAFieldMovesOnAndOnTheLastFieldReachesSave() {
		var screen = screen(false);
		screen.interaction(new SessionInteraction.Dialog(model("dev", "localhost", null), false, "dev"));
		for (int i = 0; i < 9; i++) press(screen, KeyCode.ENTER);
		assertThat(saved).isEmpty();
		assertThat(draw(screen, 100, 30).text()).contains("> Properties");
		press(screen, KeyCode.ENTER);
		assertThat(saved).isEmpty();
		press(screen, KeyCode.ENTER);
		assertThat(saved).containsExactly(true);
	}

	@Test
	void connectButtonIsAbsentWhenConnectingIsNotPossible() {
		var screen = screen(false);
		screen.interaction(new SessionInteraction.Dialog(model("dev", "localhost", null), false, "dev"));
		var drawn = draw(screen, 100, 30);
		assertThat(drawn.text()).contains("[ Save ]", "[ Cancel ]").doesNotContain("[ Connect ]", "Ctrl-G");
	}

	@Test
	void keyHintSitsAtTheBottomInsideTheFrame() {
		var screen = screen(false);
		screen.interaction(new SessionInteraction.Dialog(model("dev", "localhost", null), true, "dev"));
		var drawn = draw(screen, 100, 30);
		int hint = drawn.rowOf("Ctrl-S Save");
		assertThat(drawn.row(hint + 1)).contains("+-----");
		assertThat(drawn.row(hint)).contains("Esc cancel", "Ctrl-G Connect").startsWith("    |");
		assertThat(drawn.row(hint).substring(95, 96)).isEqualTo("|");
	}

	@Test
	void passwordPromptHasTheSameFrameAndAFixedMask() {
		var screen = screen(false);
		screen.interaction(new SessionInteraction.Password("alice@db"));
		type(screen, "super-secret");
		var drawn = draw(screen, 100, 30);
		int title = drawn.rowOf("Password for alice@db");
		assertThat(drawn.row(title)).contains("+-");
		assertThat(drawn.text()).contains("********").doesNotContain("*********", "super");
		assertThat(drawn.row(title).indexOf('+')).isEqualTo((100 - DialogLayout.PROMPT_MAX_WIDTH) / 2);
		assertThat(drawn.text()).contains("Enter connects", "Esc cancels");
		assertThat(drawn.cursor()).isNotNull();
		var narrow = draw(screen, 50, 12);
		assertThat(narrow.row(narrow.rowOf("Password for")).indexOf('+')).isEqualTo(4);
	}

	@Test
	void passwordPromptShowsTheErrorInsideThePanel() {
		var screen = screen(true);
		screen.interaction(new SessionInteraction.Password("alice@db"));
		screen.update(List.of(MessageBlock.info("earlier"), MessageBlock.error("Password authentication failed.")));
		var drawn = draw(screen, 90, 20);
		int error = drawn.rowOf("! Password authentication failed.");
		assertThat(drawn.row(error)).contains("│");
		assertThat(error).isBetween(drawn.rowOf("Password for"), drawn.rowOf("Enter connects"));
	}

	@Test
	void removalConfirmationHasTheSameFrameAndTitleStyle() {
		var screen = screen(true);
		screen.interaction(new SessionInteraction.Confirmation("dev"));
		var drawn = draw(screen, 100, 30);
		int title = drawn.rowOf("Remove Profile");
		assertThat(drawn.row(title)).contains("╭", "╮", "dev");
		assertThat(drawn.text()).contains("y confirms").contains("Esc");
		assertThat(drawn.row(title).indexOf('╭')).isEqualTo((100 - DialogLayout.PROMPT_MAX_WIDTH) / 2);
		screen.handle(KeyEvent.ofChar('y'));
		assertThat(passwords).containsExactly("y");
	}

	@Test
	void focusSurvivesTheSessionReopeningTheSameDialogAfterAValidationError() {
		var screen = screen(false);
		var model = model("dev", "localhost", null);
		screen.interaction(new SessionInteraction.Dialog(model, true, "dev"));
		tab(screen, 3);
		type(screen, "99999");
		screen.interaction(new SessionInteraction.Dialog(model, true, "dev"));
		screen.update(List.of(MessageBlock.error("Port must be a whole number from 1 to 65535.")));
		var drawn = draw(screen, 100, 30);
		assertThat(drawn.text()).contains("> Port", "99999", "! Port must be");
		screen.interaction(new SessionInteraction.Dialog(model("other", "h", null), true, "other"));
		assertThat(draw(screen, 100, 30).text()).contains("> Name");
	}

	@Test
	void readOnlyChoiceFallsBackToTheShortFormInsteadOfBeingCutOff() {
		var screen = screen(false);
		screen.interaction(new SessionInteraction.Dialog(model("dev", "localhost", null), false, "dev"));
		assertThat(draw(screen, 70, 24).text()).contains("‹ default (global: off) ›").doesNotContain("( ) of ");
		assertThat(draw(screen, 74, 24).text()).contains("(•) default (global: off)   ( ) on   ( ) off");
	}
}
