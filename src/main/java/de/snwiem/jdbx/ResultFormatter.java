package de.snwiem.jdbx;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;

/** Writes a Result in one Format. Pure: no JDBC, Spring or terminal types. */
public interface ResultFormatter {

	/** Writes the rows while it reads them. Horizontal {@code table} and the vertical layouts buffer some rows. */
	void write(RowSource source, Writer out) throws IOException;

	default void write(Result result, Writer out) throws IOException {
		write(RowSource.of(result), out);
	}

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
