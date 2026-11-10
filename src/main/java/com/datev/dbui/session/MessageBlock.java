package com.datev.dbui.session;

import com.datev.dbui.Result;

/** A terminal-independent entry in a Session's Transcript. */
public record MessageBlock(Kind kind, String text, Result result) {

	public enum Kind { INPUT, RESULT, INFO, ERROR }

	public static MessageBlock input(String text) {
		return new MessageBlock(Kind.INPUT, text, null);
	}

	public static MessageBlock result(Result result) {
		return new MessageBlock(Kind.RESULT, "", result);
	}

	/** Freeze the rendered text when the Result enters the Transcript; later setting changes do not rewrite it. */
	public static MessageBlock result(Result result, com.datev.dbui.config.Settings settings) {
		String text = com.datev.dbui.Format.parse(settings.format())
			.formatter(com.datev.dbui.FormatOptions.from(settings), com.datev.dbui.Orientation.parse(settings.orientation()))
			.format(result);
		return new MessageBlock(Kind.RESULT, text, result);
	}

	public static MessageBlock info(String text) {
		return new MessageBlock(Kind.INFO, text, null);
	}

	public static MessageBlock error(String text) {
		return new MessageBlock(Kind.ERROR, text, null);
	}
}
