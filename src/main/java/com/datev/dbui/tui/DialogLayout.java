package com.datev.dbui.tui;

import dev.tamboui.layout.Rect;

/** Where the dialog panel goes on the screen. Pure arithmetic, no terminal. */
record DialogLayout(Rect panel, boolean tooSmall) {
	static final int MARGIN = 4;
	static final int FORM_MIN_WIDTH = 60;
	static final int FORM_MIN_HEIGHT = 20;
	static final int FORM_HEIGHT = 26;
	static final int PROMPT_MIN_WIDTH = 40;
	static final int PROMPT_MIN_HEIGHT = 8;
	static final int PROMPT_MAX_WIDTH = 72;

	/** The Profile form: screen width minus 2 * MARGIN, one blank row above and below where possible. */
	static DialogLayout form(int screenWidth, int screenHeight) {
		if (screenWidth < FORM_MIN_WIDTH || screenHeight < FORM_MIN_HEIGHT) return new DialogLayout(new Rect(0, 0, 0, 0), true);
		return centered(screenWidth, screenHeight, screenWidth - 2 * MARGIN, Math.min(FORM_HEIGHT, screenHeight - 2));
	}

	/** The password prompt and the removal confirmation: content rows plus the two border rows. */
	static DialogLayout prompt(int screenWidth, int screenHeight, int contentRows) {
		int height = contentRows + 2;
		if (screenWidth < PROMPT_MIN_WIDTH || screenHeight < PROMPT_MIN_HEIGHT) {
			return new DialogLayout(new Rect(0, 0, 0, 0), true);
		}
		return centered(screenWidth, screenHeight, Math.min(PROMPT_MAX_WIDTH, screenWidth - 2 * MARGIN), Math.min(height, screenHeight));
	}

	private static DialogLayout centered(int screenWidth, int screenHeight, int width, int height) {
		return new DialogLayout(new Rect((screenWidth - width) / 2, (screenHeight - height) / 2, width, height), false);
	}
}
