package de.snwiem.jdbx.session;

import de.snwiem.jdbx.Result;

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
	public static MessageBlock result(Result result, de.snwiem.jdbx.config.Settings settings) {
		String text = de.snwiem.jdbx.Format.parse(settings.format())
			.formatter(de.snwiem.jdbx.FormatOptions.from(settings), de.snwiem.jdbx.Orientation.parse(settings.orientation()))
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
