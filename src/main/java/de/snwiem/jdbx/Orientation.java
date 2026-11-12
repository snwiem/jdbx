package de.snwiem.jdbx;

import java.util.Arrays;
import java.util.Locale;

/** Whether a Result is laid out horizontally (one row per line) or vertically (one column per line). */
public enum Orientation {

	HORIZONTAL, VERTICAL;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Orientation parse(String text) {
		String wanted = text.trim().toLowerCase(Locale.ROOT);
		return Arrays.stream(values()).filter(o -> o.id().equals(wanted)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"Unknown orientation '" + text + "'. Use one of: horizontal, vertical"));
	}
}
