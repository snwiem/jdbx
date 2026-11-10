package com.datev.dbui.tui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.datev.dbui.session.ExecutionStatus;

class StatusBarTextTest {

	@Test
	void statusShowsRowLimitRunningTimerAndTheLastResultSummary() {
		var last = new ExecutionStatus.ResultSummary(5, Duration.ofMillis(123), true);
		assertThat(StatusBarText.of(new ExecutionStatus(1000, true, Duration.ofMillis(12900), last)))
			.isEqualTo("Row limit: 1000 · running… 12 s · 5 rows · 0.123 s · truncated");
		assertThat(StatusBarText.of(new ExecutionStatus(5, false, Duration.ZERO, last)))
			.isEqualTo("Row limit: 5 · 5 rows · 0.123 s · truncated");
		assertThat(StatusBarText.of(new ExecutionStatus(1000, false, Duration.ZERO, null)))
			.isEqualTo("Row limit: 1000 · Enter sends · Ctrl-J newline · /quit exits");
		assertThat(StatusBarText.of(new ExecutionStatus(5, false, Duration.ZERO,
			new ExecutionStatus.ResultSummary(1, Duration.ofSeconds(2), false))))
			.isEqualTo("Row limit: 5 · 1 row · 2.000 s");
	}
}
