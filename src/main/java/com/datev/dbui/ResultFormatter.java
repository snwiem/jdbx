package com.datev.dbui;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;

/** Writes a Result in one Format. Pure: no JDBC, Spring or terminal types. */
public interface ResultFormatter {

	void write(Result result, Writer out) throws IOException;

	default String format(Result result) {
		StringWriter out = new StringWriter();
		try {
			write(result, out);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return out.toString();
	}
}
