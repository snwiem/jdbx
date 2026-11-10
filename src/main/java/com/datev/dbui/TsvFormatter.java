package com.datev.dbui;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/** Writes a Result in the {@code tsv} Format. Tab, newline and carriage return become {@code \t}, {@code \n}, {@code \r}. */
public class TsvFormatter implements ResultFormatter {

	private final ValueRenderer values;

	public TsvFormatter(FormatOptions options) {
		this.values = new ValueRenderer(options);
	}

	@Override
	public void write(Result result, Writer out) throws IOException {
		line(out, result.columns());
		for (List<Object> row : result.rows()) {
			line(out, row.stream().map(v -> v == null ? "" : values.plain(v)).toList());
		}
	}

	private static void line(Writer out, List<String> cells) throws IOException {
		out.append(String.join("\t", cells.stream().map(TsvFormatter::escape).toList())).append('\n');
	}

	private static String escape(String cell) {
		return cell.replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
	}
}
