package com.datev.dbui.config;

import java.time.Duration;

/**
 * Settings read from {@code config.yaml}.
 *
 * @param timeout statement timeout, or {@code null} for none
 */
public record Settings(int rowLimit, Duration timeout, String format, String orientation, boolean readOnly) {

	public static Settings defaults() {
		return new Settings(1000, null, "table", "horizontal", false);
	}

}
