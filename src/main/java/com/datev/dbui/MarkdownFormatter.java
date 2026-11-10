package com.datev.dbui;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/** Writes a Result in the {@code markdown} Format (pipe table). */
public class MarkdownFormatter implements ResultFormatter {

	private final ValueRenderer values;

	public MarkdownFormatter(FormatOptions options) {
		this.values = new ValueRenderer(options);
	}

	@Override
	public void write(Result result, Writer out) throws IOException {
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
