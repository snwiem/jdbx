package com.datev.dbui.session;

import java.time.Duration;

/** An immutable snapshot for the Status bar, independent of the terminal. */
public record ExecutionStatus(int rowLimit, boolean running, Duration elapsed, ResultSummary lastResult) {

	public record ResultSummary(int rows, Duration duration, boolean truncated) { }
}
