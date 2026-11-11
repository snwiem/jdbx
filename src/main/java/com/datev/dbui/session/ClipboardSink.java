package com.datev.dbui.session;

import java.io.IOException;

/** Terminal boundary: writes and flushes one complete OSC 52 sequence. */
@FunctionalInterface
public interface ClipboardSink {
	void write(String sequence) throws IOException;
}
