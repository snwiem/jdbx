package org.syracus.jdbx.tui;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

import org.syracus.jdbx.config.ProfileDialogModel;
import org.syracus.jdbx.config.ProfileDialogModel.Field;
import org.syracus.jdbx.session.SessionInteraction;

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
	private final DialogForm form;
	private String error;
	void error(String error) { this.error = error; }

	ConnectionModal(SessionInteraction interaction, Consumer<Boolean> save, Consumer<String> password, Runnable cancel) {
		this.interaction = interaction; this.save = save; this.password = password; this.cancel = cancel;
		this.form = interaction instanceof SessionInteraction.Dialog dialog ? new DialogForm(dialog.connect()) : null;
		if (interaction instanceof SessionInteraction.Dialog dialog) {
			for (Field field : Field.values()) {
				var state = new TextAreaState(); state.setText(dialog.model().value(field)); state.moveCursorToEnd(); fields.put(field, state);
			}
		}
	}

	/** The Session reopens the same dialog after a validation error: keep focus and cursor. */
	boolean reopenedBy(SessionInteraction next) {
		return interaction instanceof SessionInteraction.Dialog current && next instanceof SessionInteraction.Dialog other && current.model() == other.model();
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
				if (key.isKey(KeyCode.TAB)) { if (key.hasShift()) form.previous(); else form.next(); return true; }
				if (key.isKey(KeyCode.ENTER)) {
					var button = form.focusedButton();
					if (key.hasCtrl() && dialog.connect()) save.accept(false);
					else if (button == DialogForm.Button.SAVE) save.accept(true);
					else if (button == DialogForm.Button.CONNECT) save.accept(false);
					else if (button == DialogForm.Button.CANCEL) cancel.run();
					else form.next();
					return true;
				}
				if (form.focusedField() == Field.READ_ONLY) {
					int step = key.isKey(KeyCode.RIGHT) || key.code() == KeyCode.CHAR && key.character() == ' ' && !key.hasCtrl() ? 1 : key.isKey(KeyCode.LEFT) ? -1 : 0;
					if (step != 0) dialog.model().set(Field.READ_ONLY, DialogForm.cycleReadOnly(dialog.model().value(Field.READ_ONLY), step));
					return true;
				}
			}
		}
		if (interaction instanceof SessionInteraction.Dialog && event instanceof PasteEvent && form.focusedField() == Field.READ_ONLY) return true;
		if (interaction instanceof SessionInteraction.Password) TextEditing.handle(passwordInput, singleLine(event), Overflow.CLIP);
		if (interaction instanceof SessionInteraction.Dialog dialog && form.focusedField() != null && form.focusedField() != Field.READ_ONLY) {
			Field field = form.focusedField();
			String before = fields.get(field).text();
			TextEditing.handle(fields.get(field), singleLine(event), Overflow.CLIP);
			if (!before.equals(fields.get(field).text())) dialog.model().set(field, fields.get(field).text());
		}
		return true;
	}

	private Event singleLine(Event event) {
		return event instanceof PasteEvent paste ? new PasteEvent(paste.text().replace('\r', ' ').replace('\n', ' ')) : event;
	}

	void render(Frame frame, boolean color, boolean globalReadOnly) {
		var renderer = new DialogRenderer(frame, DialogStyles.of(color));
		if (interaction instanceof SessionInteraction.Dialog dialog) {
			renderer.form(title(dialog), form, dialog.model(), fields::get, globalReadOnly, error);
		}
		else if (interaction instanceof SessionInteraction.Password prompt) renderer.password(prompt.label(), passwordInput, error);
		else if (interaction instanceof SessionInteraction.Confirmation confirmation) renderer.confirmation(confirmation.profileName());
	}

	private static String title(SessionInteraction.Dialog dialog) {
		return dialog.editingName() != null ? "Edit Connection Profile" : dialog.connect() ? "Connect" : "New Connection Profile";
	}
}
