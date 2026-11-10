package com.datev.dbui;

import java.io.IOException;
import java.io.Writer;
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
	public void write(Result result, Writer out) throws IOException {
		if (orientation == Orientation.VERTICAL) {
			List<String> header = new java.util.ArrayList<>(List.of("Column"));
			for (int r = 1; r <= result.rows().size(); r++) {
				header.add("Row " + r);
			}
			line(out, header);
			line(out, header.stream().map(c -> "---").toList());
			for (int c = 0; c < result.columns().size(); c++) {
				List<String> record = new java.util.ArrayList<>(List.of(result.columns().get(c).replace("|", "\\|")));
				for (List<Object> row : result.rows()) {
					record.add(values.forMarkdown(row.get(c)));
				}
				line(out, record);
			}
			return;
		}
		line(out, result.columns().stream().map(c -> c.replace("|", "\\|")).toList());
		line(out, result.columns().stream().map(c -> "---").toList());
		for (List<Object> row : result.rows()) {
			line(out, row.stream().map(values::forMarkdown).toList());
		}
	}

	private static void line(Writer out, List<String> cells) throws IOException {
		out.append("| ").append(String.join(" | ", cells)).append(" |\n");
	}
}
