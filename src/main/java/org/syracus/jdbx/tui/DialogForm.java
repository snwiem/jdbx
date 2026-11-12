package org.syracus.jdbx.tui;

import java.util.ArrayList;
import java.util.List;

import org.syracus.jdbx.config.ProfileDialogModel;
import org.syracus.jdbx.config.ProfileDialogModel.Field;

/** Rows, groups and focus order of the Profile dialog: first the fields, then the buttons. No terminal. */
final class DialogForm {
	static final int FULL_ROWS = 19;
	static final String ALTERNATIVE_HINT = "Either a JDBC URL, or host, port and database";
	static final String PROPERTIES_HINT = "Driver properties as key=value;key=value";

	enum Kind { HEADING, HINT, BLANK, FIELD }
	record Row(Kind kind, String text, Field field) { }
	enum Button {
		SAVE("Save"), CONNECT("Connect"), CANCEL("Cancel");
		private final String label;
		Button(String label) { this.label = label; }
		String label() { return label; }
	}

	private final List<Button> buttons;
	private int focus;

	DialogForm(boolean canConnect) {
		buttons = canConnect ? List.of(Button.SAVE, Button.CONNECT, Button.CANCEL) : List.of(Button.SAVE, Button.CANCEL);
	}

	List<Button> buttons() { return buttons; }
	Field focusedField() { return focus < Field.values().length ? Field.values()[focus] : null; }
	Button focusedButton() { return focus < Field.values().length ? null : buttons.get(focus - Field.values().length); }
	void next() { focus = Math.floorMod(focus + 1, Field.values().length + buttons.size()); }
	void previous() { focus = Math.floorMod(focus - 1, Field.values().length + buttons.size()); }

	static String label(Field field) {
		return switch (field) {
			case NAME -> "Name";
			case URL -> "JDBC URL";
			case HOST -> "Host";
			case PORT -> "Port";
			case DATABASE -> "Database";
			case USER -> "User";
			case PASSWORD -> "Password";
			case READ_ONLY -> "Read-only";
			case SCHEMA -> "Schema";
			case PROPERTIES -> "Properties";
		};
	}

	List<Row> rows(boolean compact) {
		var rows = new ArrayList<Row>();
		heading(rows, "Profile", false);
		field(rows, Field.NAME);
		heading(rows, "Connection", !compact);
		rows.add(new Row(Kind.HINT, ALTERNATIVE_HINT, null));
		for (Field field : List.of(Field.URL, Field.HOST, Field.PORT, Field.DATABASE)) field(rows, field);
		heading(rows, "Credentials", !compact);
		field(rows, Field.USER);
		field(rows, Field.PASSWORD);
		heading(rows, "Options", !compact);
		for (Field field : List.of(Field.READ_ONLY, Field.SCHEMA, Field.PROPERTIES)) field(rows, field);
		rows.add(new Row(Kind.HINT, PROPERTIES_HINT, null));
		return rows;
	}

	private static void heading(List<Row> rows, String text, boolean blankBefore) {
		if (blankBefore) rows.add(new Row(Kind.BLANK, "", null));
		rows.add(new Row(Kind.HEADING, text, null));
	}

	private static void field(List<Row> rows, Field field) { rows.add(new Row(Kind.FIELD, label(field), field)); }

	/** The index of the focused field in rows(compact), or -1 when a button has the focus. */
	int focusedRow(boolean compact) {
		Field field = focusedField();
		if (field == null) return -1;
		var rows = rows(compact);
		for (int i = 0; i < rows.size(); i++) if (rows.get(i).field() == field) return i;
		return -1;
	}

	/** JDBC URL and Host/Port/Database are alternatives: the one without text is dimmed while the other one has text. */
	static boolean dimmed(Field field, ProfileDialogModel model) {
		boolean urlText = !model.value(Field.URL).isBlank();
		boolean partsText = !model.value(Field.HOST).isBlank() || !model.value(Field.PORT).isBlank() || !model.value(Field.DATABASE).isBlank();
		return switch (field) {
			case URL -> partsText && !urlText;
			case HOST, PORT, DATABASE -> urlText && !partsText;
			default -> false;
		};
	}

	/** Read-only is "" (default), "true" or "false" in the model. Moves one step through default, on, off. */
	static String cycleReadOnly(String current, int step) {
		var values = List.of("", "true", "false");
		int index = Math.max(0, values.indexOf(current));
		return values.get(Math.floorMod(index + step, values.size()));
	}

	static String readOnlyText(String value, boolean globalReadOnly, boolean wide) {
		String global = globalReadOnly ? "on" : "off";
		if (!wide) return "‹ " + (value.isEmpty() ? "default (global: " + global + ")" : value.equals("true") ? "on" : "off") + " ›";
		return radio(value.isEmpty()) + " default (global: " + global + ")   " + radio(value.equals("true")) + " on   " + radio(value.equals("false")) + " off";
	}

	private static String radio(boolean selected) { return selected ? "(•)" : "( )"; }

	static String hint(boolean canConnect, int width) {
		var variants = canConnect
			? List.of("Tab/Shift-Tab move · Enter next · Ctrl-S Save · Ctrl-G Connect · Esc cancel", "Tab move · Ctrl-S Save · Ctrl-G Connect · Esc cancel",
				"Ctrl-S Save · Ctrl-G Connect · Esc cancel", "Ctrl-S Save · Esc cancel")
			: List.of("Tab/Shift-Tab move · Enter next · Ctrl-S Save · Esc cancel", "Tab move · Ctrl-S Save · Esc cancel", "Ctrl-S Save · Esc cancel");
		for (String variant : variants) if (variant.length() <= width) return variant;
		return variants.getLast();
	}

	/** First visible row so that the focused row and the row below it are in a viewport of the given height. */
	static int scrollOffset(int rows, int viewport, int focusedRow) {
		if (focusedRow < 0) return 0;
		return Math.max(0, Math.min(rows - viewport, focusedRow - viewport + 2));
	}
}
