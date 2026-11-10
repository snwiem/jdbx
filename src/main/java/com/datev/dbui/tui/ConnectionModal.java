package com.datev.dbui.tui;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

import com.datev.dbui.config.ProfileDialogModel;
import com.datev.dbui.config.ProfileDialogModel.Field;
import com.datev.dbui.session.SessionInteraction;

import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.text.CharWidth;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.*;
import dev.tamboui.widgets.input.TextAreaState;

/** Thin modal adapter. Validation and Profile mapping stay in ProfileDialogModel. */
final class ConnectionModal {
	private final SessionInteraction interaction;
	private final Consumer<Boolean> save;
	private final Consumer<String> password;
	private final Runnable cancel;
	private final Map<Field, TextAreaState> fields = new EnumMap<>(Field.class);
	private final TextAreaState passwordInput = new TextAreaState();
	private int focus;
	private String error;
	void error(String error) { this.error = error; }

	ConnectionModal(SessionInteraction interaction, Consumer<Boolean> save, Consumer<String> password, Runnable cancel) {
		this.interaction = interaction; this.save = save; this.password = password; this.cancel = cancel;
		if (interaction instanceof SessionInteraction.Dialog dialog) {
			for (Field field : Field.values()) {
				var state = new TextAreaState(); state.setText(dialog.model().value(field)); state.moveCursorToEnd(); fields.put(field, state);
			}
		}
	}

	boolean handle(Event event) {
		if (event instanceof KeyEvent key) {
			if (key.isRelease()) return true;
			if (key.isKey(KeyCode.ESCAPE) || key.hasCtrl() && key.isCharIgnoreCase('c')) { cancel.run(); return true; }
			if (interaction instanceof SessionInteraction.Password && key.isKey(KeyCode.ENTER)) {
				String secret = passwordInput.text(); passwordInput.clear(); password.accept(secret); return true;
			}
			if (interaction instanceof SessionInteraction.Confirmation && key.code() == KeyCode.CHAR) {
				password.accept(key.character() == 'y' || key.character() == 'Y' ? "y" : "n"); return true;
			}
			if (interaction instanceof SessionInteraction.Dialog dialog) {
				if (key.hasCtrl() && key.isCharIgnoreCase('s')) { save.accept(true); return true; }
				if (key.hasCtrl() && key.isCharIgnoreCase('g') && dialog.connect()) { save.accept(false); return true; }
				if (key.isKey(KeyCode.TAB)) { focus = Math.floorMod(focus + (key.hasShift() ? -1 : 1), Field.values().length + 1); return true; }
				if (key.isKey(KeyCode.ENTER)) {
					if (key.hasCtrl() && dialog.connect()) save.accept(false);
					else if (focus >= Field.values().length - 1) save.accept(true);
					else focus++;
					return true;
				}
			}
		}
		if (interaction instanceof SessionInteraction.Password) TextEditing.handle(passwordInput, singleLine(event), Overflow.CLIP);
		if (interaction instanceof SessionInteraction.Dialog dialog && focus < Field.values().length) {
			Field field = Field.values()[focus];
			String before = fields.get(field).text();
			TextEditing.handle(fields.get(field), singleLine(event), Overflow.CLIP);
			if (!before.equals(fields.get(field).text())) dialog.model().set(field, fields.get(field).text());
		}
		return true;
	}

	private Event singleLine(Event event) {
		return event instanceof PasteEvent paste ? new PasteEvent(paste.text().replace('\r', ' ').replace('\n', ' ')) : event;
	}

	void render(Frame frame, Style style) {
		int width = Math.min(110, frame.width());
		int wanted = interaction instanceof SessionInteraction.Dialog ? Field.values().length + 6 : 6;
		int height = Math.min(wanted, frame.height());
		int x = Math.max(0, (frame.width() - width) / 2), y = Math.max(0, (frame.height() - height) / 2);
		frame.buffer().fill(new Rect(x, y, width, height), new Cell(" ", style));
		if (interaction instanceof SessionInteraction.Dialog dialog) {
			line(frame, x, y, width, " Edit Connection Profile", style.bold());
			int visible = Math.max(0, height - 5);
			int first = Math.max(0, Math.min(focus, Field.values().length - 1) - visible + 1);
			for (int row = 0; row < visible && first + row < Field.values().length; row++) {
				Field field = Field.values()[first + row];
				String value = field == Field.PASSWORD ? "*".repeat(fields.get(field).text().length()) : fields.get(field).text();
				String prefix = (focus == first + row ? "> " : "  ") + field.label() + ": ";
				line(frame, x, y + row + 1, width, prefix + value, focus == first + row ? style.reversed() : style);
			}
			if (error != null) line(frame, x, y + height - 4, width, " " + error, style.bold());
			line(frame, x, y + height - 3, width, (focus == Field.values().length ? "> " : "  ") + "[ Save ]", style.bold());
			line(frame, x, y + height - 2, width, " Tab/Shift-Tab fields · Enter next/Save · Ctrl-S Save · Esc cancel", style);
			line(frame, x, y + height - 1, width, dialog.connect() ? " Ctrl-G / Ctrl-Enter Connect without saving" : " Profile edits are saved to profiles.yaml", style);
		}
		else if (interaction instanceof SessionInteraction.Password prompt) {
			line(frame, x, y, width, " Password for " + prompt.label(), style.bold());
			line(frame, x, y + 2, width, " > " + "*".repeat(passwordInput.text().length()), style.reversed());
			if (error != null) line(frame, x, y + height - 2, width, " " + error, style.bold());
			line(frame, x, y + height - 1, width, " Enter connects · Esc cancels · password is never saved", style);
		}
		else if (interaction instanceof SessionInteraction.Confirmation confirmation) {
			line(frame, x, y, width, " Remove Profile '" + confirmation.profileName() + "'?", style.bold());
			line(frame, x, y + height - 1, width, " y confirms · any other letter or Esc cancels", style);
		}
	}

	private void line(Frame frame, int x, int y, int width, String text, Style style) {
		if (y < 0 || y >= frame.height() || width < 1) return;
		String safe = text.replaceAll("[\\p{Cntrl}]", "?");
		frame.buffer().setString(x, y, CharWidth.substringByWidth(safe, width), style);
	}
}
