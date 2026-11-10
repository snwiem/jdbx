package com.datev.dbui;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/** Writes a Result in the {@code csv} Format (RFC 4180 quoting, {@code \n} line ends, NULL as empty). Never cuts data. */
public class CsvFormatter implements ResultFormatter {

	private final ValueRenderer values;

	public CsvFormatter(FormatOptions options) {
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
		StringBuilder line = new StringBuilder();
		for (int i = 0; i < cells.size(); i++) {
			if (i > 0) {
				line.append(',');
			}
			line.append(quote(cells.get(i)));
		}
		out.append(line).append('\n');
	}

	private static String quote(String cell) {
		if (cell.contains(",") || cell.contains("\"") || cell.contains("\n") || cell.contains("\r")) {
			return "\"" + cell.replace("\"", "\"\"") + "\"";
		}
		return cell;
	}
}
