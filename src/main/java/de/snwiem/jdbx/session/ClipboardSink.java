package org.syracus.jdbx.session;

import java.io.IOException;

/** Terminal boundary: writes and flushes one complete OSC 52 sequence. */
@FunctionalInterface
public interface ClipboardSink {
	void write(String sequence) throws IOException;
}
