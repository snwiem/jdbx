package com.datev.dbui;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/** Writes a Result in the {@code table} Format. */
public class TableFormatter implements ResultFormatter {

	private final ValueRenderer values;

	public TableFormatter() {
		this(FormatOptions.defaults());
	}

	public TableFormatter(FormatOptions options) {
		this.values = new ValueRenderer(options);
	}

	@Override
	public void write(Result result, Writer out) throws IOException {
		int count = result.columns().size();
		List<List<String>> cells = new ArrayList<>();
		for (List<Object> row : result.rows()) {
			cells.add(row.stream().map(values::forTable).toList());
		}
		int[] widths = new int[count];
		for (int i = 0; i < count; i++) {
			widths[i] = result.columns().get(i).length();
			for (List<String> row : cells) {
				widths[i] = Math.max(widths[i], row.get(i).length());
			}
		}
		line(out, result.columns(), widths);
		StringBuilder separator = new StringBuilder();
		for (int i = 0; i < count; i++) {
			separator.append(i > 0 ? "-+-" : "").append("-".repeat(widths[i]));
		}
		out.append(separator).append('\n');
		for (List<String> row : cells) {
			line(out, row, widths);
		}
	}

	private static void line(Writer out, List<String> cells, int[] widths) throws IOException {
		StringBuilder line = new StringBuilder();
		for (int i = 0; i < widths.length; i++) {
			if (i > 0) {
				line.append(" | ");
			}
			line.append(cells.get(i));
			if (i < widths.length - 1) {
				line.append(" ".repeat(widths[i] - cells.get(i).length()));
			}
		}
		out.append(line).append('\n');
	}
}
