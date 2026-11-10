package com.datev.dbui.tui;

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;

/** Colors and frame characters of the dialogs. Without color (NO_COLOR) the frame is plain ASCII and styles use no color codes. */
record DialogStyles(Style panel, Style border, Style title, Style heading, Style hint, Style field, Style focusedField, Style dimmedField,
		Style error, Style button, Style focusedButton, boolean color) {
	static DialogStyles of(boolean color) {
		if (!color) {
			return new DialogStyles(Style.EMPTY, Style.EMPTY, Style.EMPTY.bold(), Style.EMPTY.bold(), Style.EMPTY.dim(), Style.EMPTY.underlined(),
				Style.EMPTY.reversed(), Style.EMPTY.dim().underlined(), Style.EMPTY.bold(), Style.EMPTY, Style.EMPTY.reversed().bold(), false);
		}
		Style panel = Style.EMPTY.fg(Color.indexed(252)).bg(Color.indexed(235));
		return new DialogStyles(panel, panel.fg(Color.indexed(73)), panel.fg(Color.indexed(255)).bold(), panel.fg(Color.indexed(73)).bold(),
			panel.fg(Color.indexed(245)), Style.EMPTY.fg(Color.indexed(255)).bg(Color.indexed(238)),
			Style.EMPTY.fg(Color.indexed(255)).bg(Color.indexed(25)), Style.EMPTY.fg(Color.indexed(244)).bg(Color.indexed(237)).dim(),
			panel.fg(Color.indexed(203)).bold(), Style.EMPTY.fg(Color.indexed(252)).bg(Color.indexed(238)),
			Style.EMPTY.fg(Color.indexed(255)).bg(Color.indexed(25)).bold(), true);
	}

	String topLeft() { return color ? "╭" : "+"; }
	String topRight() { return color ? "╮" : "+"; }
	String bottomLeft() { return color ? "╰" : "+"; }
	String bottomRight() { return color ? "╯" : "+"; }
	String horizontal() { return color ? "─" : "-"; }
	String vertical() { return color ? "│" : "|"; }
	String fieldFill() { return color ? " " : "_"; }
	String up() { return color ? "▲" : "^"; }
	String down() { return color ? "▼" : "v"; }
}
