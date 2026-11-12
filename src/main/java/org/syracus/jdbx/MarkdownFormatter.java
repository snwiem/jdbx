package org.syracus.jdbx;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;

/** Writes a Result in the {@code markdown} Format (pipe table). */
public class MarkdownFormatter implements ResultFormatter {

	private final ValueRenderer values;

	private final Orientation orientation;

	public MarkdownFormatter(FormatOptions options) {
		this(options, Orientation.HORIZONTAL);
	}

	public MarkdownFormatter(FormatOptions options, Orientation orientation) {
		this.values = new ValueRenderer(options);
		this.orientation = orientation;
	}

	@Override
	public void write(RowSource source, Writer out) throws IOException {
		if (orientation == Orientation.VERTICAL) {
			List<List<String>> records = new java.util.ArrayList<>();
			for (String column : source.columns()) {
				records.add(new java.util.ArrayList<>(List.of(column.replace("|", "\\|"))));
			}
			List<String> header = new java.util.ArrayList<>(List.of("Column"));
			Iterator<List<Object>> rows = source.rows();
			while (rows.hasNext()) {
				List<Object> row = rows.next();
				header.add("Row " + header.size());
				for (int c = 0; c < records.size(); c++) {
					records.get(c).add(values.forMarkdown(row.get(c)));
				}
			}
			line(out, header);
			line(out, header.stream().map(c -> "---").toList());
			for (List<String> record : records) {
				line(out, record);
			}
			return;
		}
		line(out, source.columns().stream().map(c -> c.replace("|", "\\|")).toList());
		line(out, source.columns().stream().map(c -> "---").toList());
		Iterator<List<Object>> rows = source.rows();
		while (rows.hasNext()) {
			line(out, rows.next().stream().map(values::forMarkdown).toList());
		}
	}

	private static void line(Writer out, List<String> cells) throws IOException {
		out.append("| ").append(String.join(" | ", cells)).append(" |\n");
	}
}
