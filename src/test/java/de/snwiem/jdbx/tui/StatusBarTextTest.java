package de.snwiem.jdbx.tui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import de.snwiem.jdbx.session.ExecutionStatus;

class StatusBarTextTest {

	@Test
	void statusShowsRowLimitRunningTimerAndTheLastResultSummary() {
		var last = new ExecutionStatus.ResultSummary(5, Duration.ofMillis(123), true);
		assertThat(StatusBarText.of(new ExecutionStatus(1000, true, Duration.ofMillis(12900), last, false, true)))
			.isEqualTo("read-write · Row limit: 1000 · running… 12 s · 5 rows · 0.123 s · truncated");
		assertThat(StatusBarText.of(new ExecutionStatus(5, false, Duration.ZERO, last, false, true)))
			.isEqualTo("read-write · Row limit: 5 · 5 rows · 0.123 s · truncated");
		assertThat(StatusBarText.of(new ExecutionStatus(1000, false, Duration.ZERO, null, false, true)))
			.isEqualTo("read-write · Row limit: 1000 · Enter sends · Ctrl-J newline · /quit exits");
		assertThat(StatusBarText.of(new ExecutionStatus(5, false, Duration.ZERO,
			new ExecutionStatus.ResultSummary(1, Duration.ofSeconds(2), false), false, true)))
			.isEqualTo("read-write · Row limit: 5 · 1 row · 2.000 s");
	}

	@Test
	void combinedStatusKeepsSettingsSummaryAndLoadingNoteWithoutHidingMode() {
		var status = new ExecutionStatus(2, true, Duration.ofSeconds(3),
			new ExecutionStatus.ResultSummary(2, Duration.ofMillis(15), true), true, true);
		var settings = de.snwiem.jdbx.config.Settings.defaults().withOutput("csv", "vertical");
		assertThat(StatusBarText.lines(status, settings, "alice@host:5432/db", "loading schema…", 2))
			.containsExactly("alice@host:5432/db · Format: csv · Orientation: vertical",
				"read-only · loading schema… · Row limit: 2 · running… 3 s · 2 rows · 0.015 s · truncated");
		assertThat(StatusBarText.lines(status, settings, "alice@host:5432/db", "loading schema…", 1).getFirst())
			.startsWith("read-only · loading schema… · Format: csv · Orientation: vertical · Row limit: 2")
			.contains("running… 3 s", "2 rows", "0.015 s", "truncated", "alice@host:5432/db");
		assertThat(StatusBarText.lines(new ExecutionStatus(5, false, Duration.ZERO, null, false, false),
			settings, "old target", null, 1).getFirst()).startsWith("not connected · Format:");
	}

	@Test
	void statusShowsReadOnlyReadWriteOrNotConnected() {
		assertThat(StatusBarText.of(new ExecutionStatus(5, false, Duration.ZERO, null, true, true)))
			.startsWith("read-only · Row limit: 5");
		assertThat(StatusBarText.of(new ExecutionStatus(5, false, Duration.ZERO, null, false, true)))
			.startsWith("read-write · Row limit: 5");
		assertThat(StatusBarText.of(new ExecutionStatus(5, false, Duration.ZERO, null, true, false)))
			.startsWith("not connected · Row limit: 5");
		assertThat(StatusBarText.mode(new ExecutionStatus(5, false, Duration.ZERO, null, false, false)))
			.isEqualTo("not connected");
	}
}
