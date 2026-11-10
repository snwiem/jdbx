package com.datev.dbui;

import java.util.ArrayList;
import java.util.List;

/** Writes a Result in the horizontal {@code table} Format. */
public class TableFormatter {

	public String format(Result result) {
		int count = result.columns().size();
		List<List<String>> cells = new ArrayList<>();
		for (List<Object> row : result.rows()) {
			cells.add(row.stream().map(TableFormatter::render).toList());
		}
		int[] widths = new int[count];
		for (int i = 0; i < count; i++) {
			widths[i] = result.columns().get(i).length();
			for (List<String> row : cells) {
				widths[i] = Math.max(widths[i], row.get(i).length());
			}
		}
		StringBuilder out = new StringBuilder();
		line(out, result.columns(), widths);
		for (int i = 0; i < count; i++) {
			out.append(i > 0 ? "-+-" : "").append("-".repeat(widths[i]));
		}
		out.append('\n');
		cells.forEach(row -> line(out, row, widths));
		return out.toString();
	}

	private static String render(Object value) {
		return value == null ? "NULL" : value.toString();
	}

	private static void line(StringBuilder out, List<String> cells, int[] widths) {
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
