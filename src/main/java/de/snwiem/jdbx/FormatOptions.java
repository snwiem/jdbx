package de.snwiem.jdbx;

import java.time.ZoneId;

/**
 * Settings that change how values are rendered. The defaults follow the PRD. Reading them from settings is done by
 * the settings loader.
 */
public record FormatOptions(String nullMarker, String multiLineMarker, int maxWidth, ZoneId timeZone) {

	public static final String DEFAULT_NULL_MARKER = "NULL";
	public static final String DEFAULT_MULTI_LINE_MARKER = "⏎";
	public static final int DEFAULT_MAX_WIDTH = 60;

	public static FormatOptions defaults() {
		return new FormatOptions(DEFAULT_NULL_MARKER, DEFAULT_MULTI_LINE_MARKER, DEFAULT_MAX_WIDTH, ZoneId.systemDefault());
	}

	public static FormatOptions from(de.snwiem.jdbx.config.Settings settings) {
		FormatOptions options = defaults().withNullMarker(settings.nullMarker())
			.withMultiLineMarker(settings.multilineMarker()).withMaxWidth(settings.maxWidth());
		return settings.timezone() == null ? options : options.withTimeZone(settings.timezone());
	}

	public FormatOptions withNullMarker(String value) {
		return new FormatOptions(value, multiLineMarker, maxWidth, timeZone);
	}

	public FormatOptions withMultiLineMarker(String value) {
		return new FormatOptions(nullMarker, value, maxWidth, timeZone);
	}

	public FormatOptions withMaxWidth(int value) {
		return new FormatOptions(nullMarker, multiLineMarker, value, timeZone);
	}

	public FormatOptions withTimeZone(ZoneId value) {
		return new FormatOptions(nullMarker, multiLineMarker, maxWidth, value);
	}
}
