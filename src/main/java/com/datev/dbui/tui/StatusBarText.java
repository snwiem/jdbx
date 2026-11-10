package com.datev.dbui.tui;

import java.util.Locale;

import com.datev.dbui.session.ExecutionStatus;

/** Plain Status bar text; the terminal adapter only draws it. */
final class StatusBarText {

	private StatusBarText() { }

	static String output(com.datev.dbui.config.Settings settings) {
		return "Format: " + settings.format() + " · Orientation: " + settings.orientation();
	}

	/** The mode segment at the start of the Status bar: {@code read-only}, {@code read-write} or {@code not connected}. */
	static String mode(ExecutionStatus status) {
		return !status.connected() ? "not connected" : status.readOnly() ? "read-only" : "read-write";
	}

	/** A one-line terminal keeps the mode first, even during Completion loading. */
	static java.util.List<String> lines(ExecutionStatus status, com.datev.dbui.config.Settings settings,
			String connection, String note, int height) {
		String mode = mode(status);
		String detail = of(status).substring(mode.length() + " · ".length());
		String prefix = mode + (note == null ? "" : " · " + note);
		if (height == 1) {
			return java.util.List.of(prefix + " · " + output(settings) + " · " + detail + " · " + connection);
		}
		return java.util.List.of(connection + " · " + output(settings), prefix + " · " + detail);
	}

	static String of(ExecutionStatus status) {
		String text = mode(status) + " · Row limit: " + status.rowLimit();
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
