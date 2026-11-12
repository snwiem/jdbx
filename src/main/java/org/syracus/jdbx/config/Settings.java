package org.syracus.jdbx.config;

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
 * @param historyEnabled whether the input history is saved ({@code history.enabled})
 * @param sessionsKeep how many Session directories to keep ({@code sessions.keep})
 * @param keyBindings editor keys from the current config file; not restored by Resume
 */
public record Settings(int rowLimit, Duration timeout, String format, String orientation, boolean readOnly,
		String nullMarker, String multilineMarker, int maxWidth, ZoneId timezone, boolean historyEnabled, int sessionsKeep,
		KeyBindings keyBindings) {

	public static final int DEFAULT_SESSIONS_KEEP = 50;

	public static final String DEFAULT_NULL_MARKER = "NULL";
	public static final String DEFAULT_MULTILINE_MARKER = "⏎";
	public static final int DEFAULT_MAX_WIDTH = 60;

	public Settings(int rowLimit, Duration timeout, String format, String orientation, boolean readOnly) {
		this(rowLimit, timeout, format, orientation, readOnly, DEFAULT_NULL_MARKER, DEFAULT_MULTILINE_MARKER,
				DEFAULT_MAX_WIDTH, null, true, DEFAULT_SESSIONS_KEEP);
	}

	public Settings(int rowLimit, Duration timeout, String format, String orientation, boolean readOnly,
			String nullMarker, String multilineMarker, int maxWidth, ZoneId timezone) {
		this(rowLimit, timeout, format, orientation, readOnly, nullMarker, multilineMarker, maxWidth, timezone, true,
				DEFAULT_SESSIONS_KEEP);
	}

	public Settings(int rowLimit, Duration timeout, String format, String orientation, boolean readOnly,
			String nullMarker, String multilineMarker, int maxWidth, ZoneId timezone, boolean historyEnabled, int sessionsKeep) {
		this(rowLimit, timeout, format, orientation, readOnly, nullMarker, multilineMarker, maxWidth, timezone,
			historyEnabled, sessionsKeep, KeyBindings.defaults());
	}

	/** The same Settings with other values for the settings that Resume restores. */
	public Settings withExecution(int rowLimit, Duration timeout, boolean readOnly) {
		return new Settings(rowLimit, timeout, format, orientation, readOnly, nullMarker, multilineMarker, maxWidth,
				timezone, historyEnabled, sessionsKeep, keyBindings);
	}

	/** The same Settings with another maximum cell width for the {@code table} Format; 0 means no cut. */
	public Settings withMaxWidth(int maxWidth) {
		return new Settings(rowLimit, timeout, format, orientation, readOnly, nullMarker, multilineMarker, maxWidth,
				timezone, historyEnabled, sessionsKeep, keyBindings);
	}

	/** The same Settings with another Format and Orientation for subsequent Results. */
	public Settings withOutput(String format, String orientation) {
		return new Settings(rowLimit, timeout, format, orientation, readOnly, nullMarker, multilineMarker, maxWidth,
				timezone, historyEnabled, sessionsKeep, keyBindings);
	}

	/** The same Settings with another editor key map. */
	public Settings withKeyBindings(KeyBindings keyBindings) {
		return new Settings(rowLimit, timeout, format, orientation, readOnly, nullMarker, multilineMarker, maxWidth,
			timezone, historyEnabled, sessionsKeep, keyBindings);
	}

	public static Settings defaults() {
		return new Settings(1000, null, "table", "horizontal", false);
	}

}
