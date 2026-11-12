package org.syracus.jdbx.session;

import org.syracus.jdbx.Result;

/** A terminal-independent entry in a Session's Transcript. */
public record MessageBlock(Kind kind, String text, Result result) {

	public enum Kind { INPUT, RESULT, INFO, ERROR }

	public static MessageBlock input(String text) {
		return new MessageBlock(Kind.INPUT, text, null);
	}

	/** An unformatted Result for adapters; a null text is distinct from a legitimately empty formatted Result. */
	public static MessageBlock result(Result result) {
		return new MessageBlock(Kind.RESULT, null, result);
	}

	/** Freeze the rendered text when the Result enters the Transcript; later setting changes do not rewrite it. */
	public static MessageBlock result(Result result, org.syracus.jdbx.config.Settings settings) {
		String text = org.syracus.jdbx.Format.parse(settings.format())
			.formatter(org.syracus.jdbx.FormatOptions.from(settings), org.syracus.jdbx.Orientation.parse(settings.orientation()))
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
