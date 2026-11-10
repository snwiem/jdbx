package com.datev.dbui.tui;

import java.util.Locale;

import com.datev.dbui.session.ExecutionStatus;

/** Plain Status bar text; the terminal adapter only draws it. */
final class StatusBarText {

	private StatusBarText() { }

	static String output(com.datev.dbui.config.Settings settings) {
		return "Format: " + settings.format() + " · Orientation: " + settings.orientation();
	}

	static String of(ExecutionStatus status) {
		String text = "Row limit: " + status.rowLimit();
		if (status.running()) {
			text += " · running… " + status.elapsed().toSeconds() + " s";
		}
		var last = status.lastResult();
		if (last != null) {
			text += " · " + last.rows() + (last.rows() == 1 ? " row" : " rows")
				+ String.format(Locale.ROOT, " · %.3f s", last.duration().toNanos() / 1_000_000_000.0)
				+ (last.truncated() ? " · truncated" : "");
		}
		else if (!status.running()) {
			text += " · Enter sends · Ctrl-J newline · /quit exits";
		}
		return text;
	}
}
