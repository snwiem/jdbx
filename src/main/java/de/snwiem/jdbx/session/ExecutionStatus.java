package de.snwiem.jdbx.session;

import java.time.Duration;

/**
 * An immutable snapshot for the Status bar, independent of the terminal.
 *
 * @param readOnly whether the Session is read-only
 * @param connected {@code false} while the connection is down and has not been restored
 */
public record ExecutionStatus(int rowLimit, boolean running, Duration elapsed, ResultSummary lastResult,
		boolean readOnly, boolean connected) {

	public ExecutionStatus(int rowLimit, boolean running, Duration elapsed, ResultSummary lastResult) {
		this(rowLimit, running, elapsed, lastResult, false, true);
	}

	public record ResultSummary(int rows, Duration duration, boolean truncated) { }
}
