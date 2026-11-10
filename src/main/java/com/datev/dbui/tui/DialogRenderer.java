package com.datev.dbui.tui;

import java.util.function.Function;

import com.datev.dbui.config.ProfileDialogModel;
import com.datev.dbui.config.ProfileDialogModel.Field;

import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import dev.tamboui.widgets.input.TextAreaState;

/** Thin TamboUI drawing of the dialogs. Layout and form decisions come from DialogLayout and DialogForm. */
final class DialogRenderer {
	static final String MASK = "********";
	private static final int LABEL_COLUMN = 13; // marker (2) + longest label (10) + 1 space
	private final Frame frame;
	private final DialogStyles styles;

	DialogRenderer(Frame frame, DialogStyles styles) {
		this.frame = frame;
		this.styles = styles;
	}

	void tooSmall(int minWidth, int minHeight) {
		frame.clearCursor();
		if (frame.width() < 1 || frame.height() < 1) return;
		int y = frame.height() / 2;
		frame.buffer().fill(new Rect(0, y, frame.width(), 1), new Cell(" ", styles.panel()));
		put(0, y, frame.width(), "Terminal too small (need " + minWidth + "x" + minHeight + "). Esc cancels.", styles.error());
	}

	void form(String title, DialogForm form, ProfileDialogModel model, Function<Field, TextAreaState> states, boolean globalReadOnly, String error) {
		var layout = DialogLayout.form(frame.width(), frame.height());
		if (layout.tooSmall()) { tooSmall(DialogLayout.FORM_MIN_WIDTH, DialogLayout.FORM_MIN_HEIGHT); return; }
		frame.clearCursor();
		Rect panel = layout.panel();
		panel(panel, title);
		boolean full = panel.height() >= DialogLayout.FORM_HEIGHT;
		boolean showError = error != null;
		int footer = full ? 4 : (showError ? 3 : 2);
		int top = panel.y() + 1 + (full ? 1 : 0);
		int bodyHeight = panel.height() - 2 - (full ? 1 : 0) - footer;
		int cx = panel.x() + 3, cw = panel.width() - 6;
		var rows = form.rows(!full);
		int offset = DialogForm.scrollOffset(rows.size(), bodyHeight, form.focusedRow(!full));
		for (int i = 0; i < bodyHeight && offset + i < rows.size(); i++) {
			var row = rows.get(offset + i);
			int y = top + i;
			switch (row.kind()) {
				case HEADING -> {
					put(cx, y, cw, row.text() + " ", styles.heading());
					int used = CharWidth.of(row.text()) + 1;
					put(cx + used, y, cw - used, styles.horizontal().repeat(Math.max(0, cw - used)), styles.border());
				}
				case HINT -> { boolean indent = CharWidth.of(row.text()) <= cw - 2; put(cx + (indent ? 2 : 0), y, cw, row.text(), styles.hint()); }
				case FIELD -> fieldRow(cx, y, cw, row.field(), form, model, states, globalReadOnly);
				case BLANK -> { }
			}
		}
		if (offset > 0) put(panel.x() + panel.width() - 2, top, 1, styles.up(), styles.hint());
		if (offset + bodyHeight < rows.size()) put(panel.x() + panel.width() - 2, top + bodyHeight - 1, 1, styles.down(), styles.hint());
		int y = top + bodyHeight;
		if (showError || full) { if (showError) put(cx, y, cw, "! " + oneLine(error), styles.error()); y++; }
		if (full) y++;
		buttons(cx, y, cw, form);
		put(cx, y + 1, cw, DialogForm.hint(form.buttons().contains(DialogForm.Button.CONNECT), cw), styles.hint());
	}

	private void fieldRow(int cx, int y, int cw, Field field, DialogForm form, ProfileDialogModel model, Function<Field, TextAreaState> states,
			boolean globalReadOnly) {
		boolean focused = form.focusedField() == field;
		put(cx, y, 2, focused ? "> " : "  ", focused ? styles.heading() : styles.panel());
		put(cx + 2, y, LABEL_COLUMN - 2, DialogForm.label(field), focused ? styles.heading() : styles.panel());
		int fx = cx + LABEL_COLUMN, fw = cw - LABEL_COLUMN;
		Style style = focused ? styles.focusedField() : DialogForm.dimmed(field, model) ? styles.dimmedField() : styles.field();
		if (field == Field.READ_ONLY) {
			paintField(fx, y, fw, DialogForm.readOnlyText(model.value(field), globalReadOnly, fw >= 43), style, true);
			return;
		}
		var state = states.apply(field);
		FieldWindow window = field == Field.PASSWORD
			? FieldWindow.of(state.text().isEmpty() ? "" : MASK, state.text().isEmpty() ? 0 : MASK.length(), fw)
			: FieldWindow.of(state.text(), state.cursorCol(), fw);
		paintField(fx, y, fw, window.visible(), style, focused);
		if (focused) frame.setCursorPosition(fx + window.cursorColumn(), y);
	}

	/** A text field shows its empty cells as underscores without color. A choice or a focused field shows plain spaces. */
	private void paintField(int x, int y, int width, String text, Style style, boolean plainFill) {
		String fill = plainFill ? " " : styles.fieldFill();
		put(x, y, width, fill.repeat(width), style);
		put(x, y, width, text, style);
	}

	private void buttons(int cx, int y, int cw, DialogForm form) {
		int total = 0;
		for (var button : form.buttons()) total += 4 + label(button).length();
		int x = cx + Math.max(0, (cw - total) / 2);
		for (var button : form.buttons()) {
			boolean focused = form.focusedButton() == button;
			put(x + 2, y, 2, focused ? "> " : "  ", styles.heading());
			put(x + 4, y, label(button).length(), label(button), focused ? styles.focusedButton() : styles.button());
			x += 4 + label(button).length();
		}
	}

	private static String label(DialogForm.Button button) { return "[ " + button.label() + " ]"; }

	void password(String label, TextAreaState input, String error) {
		var layout = DialogLayout.prompt(frame.width(), frame.height(), 5);
		if (layout.tooSmall()) { tooSmall(DialogLayout.PROMPT_MIN_WIDTH, DialogLayout.PROMPT_MIN_HEIGHT); return; }
		Rect panel = layout.panel();
		panel(panel, "Password for " + label);
		int cx = panel.x() + 3, cw = panel.width() - 6, y = panel.y() + 2;
		put(cx, y, 2, "> ", styles.heading());
		put(cx + 2, y, LABEL_COLUMN - 2, "Password", styles.heading());
		int fx = cx + LABEL_COLUMN, fw = cw - LABEL_COLUMN;
		boolean empty = input.text().isEmpty();
		FieldWindow window = FieldWindow.of(empty ? "" : MASK, empty ? 0 : MASK.length(), fw);
		paintField(fx, y, fw, window.visible(), styles.focusedField(), true);
		frame.setCursorPosition(fx + window.cursorColumn(), y);
		if (error != null) put(cx, y + 2, cw, "! " + oneLine(error), styles.error());
		put(cx, y + 3, cw, "Enter connects · Esc cancels · the password is never saved", styles.hint());
	}

	void confirmation(String name) {
		var layout = DialogLayout.prompt(frame.width(), frame.height(), 4);
		if (layout.tooSmall()) { tooSmall(DialogLayout.PROMPT_MIN_WIDTH, DialogLayout.PROMPT_MIN_HEIGHT); return; }
		frame.clearCursor();
		Rect panel = layout.panel();
		panel(panel, "Remove Profile '" + oneLine(name) + "'?");
		int cx = panel.x() + 3, cw = panel.width() - 6, y = panel.y() + 2;
		put(cx, y, cw, "The Profile is deleted from profiles.yaml.", styles.panel());
		put(cx, y + 2, cw, "y confirms · any other letter or Esc cancels", styles.hint());
	}

	/** Opaque panel: every cell is overwritten, so no Transcript text shows through. */
	private void panel(Rect panel, String title) {
		var buffer = frame.buffer();
		buffer.fill(panel, new Cell(" ", styles.panel()));
		int right = panel.x() + panel.width() - 1, bottom = panel.y() + panel.height() - 1;
		put(panel.x(), panel.y(), panel.width(), styles.topLeft() + styles.horizontal().repeat(panel.width() - 2) + styles.topRight(), styles.border());
		put(panel.x(), bottom, panel.width(), styles.bottomLeft() + styles.horizontal().repeat(panel.width() - 2) + styles.bottomRight(), styles.border());
		for (int y = panel.y() + 1; y < bottom; y++) {
			put(panel.x(), y, 1, styles.vertical(), styles.border());
			put(right, y, 1, styles.vertical(), styles.border());
		}
		put(panel.x() + 2, panel.y(), panel.width() - 4, " " + title + " ", styles.title());
	}

	private static String oneLine(String text) { return text.replaceAll("[\\p{Cntrl}]", " "); }

	private void put(int x, int y, int width, String text, Style style) {
		if (y < 0 || y >= frame.height() || x < 0 || width < 1) return;
		frame.buffer().setString(x, y, CharWidth.substringByWidth(text.replaceAll("[\\p{Cntrl}]", "?"), Math.min(width, frame.width() - x)), style);
	}
}
