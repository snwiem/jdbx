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

	public static MessageBlock error(String text) {
		return new MessageBlock(Kind.ERROR, text, null);
	}
}
