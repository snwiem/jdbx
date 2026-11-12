package org.syracus.jdbx.tui;

import dev.tamboui.text.CharWidth;

/** The visible part of a single-line input field: scrolls horizontally so that the cursor stays inside. */
record FieldWindow(String visible, int cursorColumn) {
	static FieldWindow of(String text, int cursor, int width) {
		if (width < 1) return new FieldWindow("", 0);
		int[] points = text.replaceAll("[\\p{Cntrl}]", "?").codePoints().toArray();
		int cursorIndex = text.codePointCount(0, Math.max(0, Math.min(cursor, text.length())));
		int[] widths = new int[points.length];
		int total = 0, beforeCursor = 0;
		for (int i = 0; i < points.length; i++) {
			widths[i] = Math.max(0, CharWidth.of(points[i]));
			total += widths[i];
			if (i < cursorIndex) beforeCursor += widths[i];
		}
		int start = beforeCursor <= width - 1 ? 0 : beforeCursor - (width - 1) + Math.min(width / 4, total - beforeCursor);
		int first = 0, skipped = 0;
		while (first < points.length && skipped < start) skipped += widths[first++];
		var visible = new StringBuilder();
		int used = 0;
		for (int i = first; i < points.length && used + widths[i] <= width; i++) { visible.appendCodePoint(points[i]); used += widths[i]; }
		return new FieldWindow(visible.toString(), Math.max(0, beforeCursor - skipped));
	}
}
