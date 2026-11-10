package com.datev.dbui.config;

import java.time.Duration;
import java.time.ZoneId;

/**
 * Settings read from {@code config.yaml}.
 *
 * @param timeout statement timeout, or {@code null} for none
 * @param nullMarker text for NULL in {@code table} and {@code markdown}
 * @param multilineMarker text for a line break inside a cell in {@code table}
 * @param maxWidth maximum text width of a cell in {@code table}; 0 means no cut
 * @param timezone time zone for {@code timestamptz} values, or {@code null} for the JVM default
 */
public record Settings(int rowLimit, Duration timeout, String format, String orientation, boolean readOnly,
		String nullMarker, String multilineMarker, int maxWidth, ZoneId timezone) {

	public static final String DEFAULT_NULL_MARKER = "NULL";
	public static final String DEFAULT_MULTILINE_MARKER = "⏎";
	public static final int DEFAULT_MAX_WIDTH = 60;

	public Settings(int rowLimit, Duration timeout, String format, String orientation, boolean readOnly) {
		this(rowLimit, timeout, format, orientation, readOnly, DEFAULT_NULL_MARKER, DEFAULT_MULTILINE_MARKER,
				DEFAULT_MAX_WIDTH, null);
	}

	public static Settings defaults() {
		return new Settings(1000, null, "table", "horizontal", false);
	}

}
