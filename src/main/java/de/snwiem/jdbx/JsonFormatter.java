package de.snwiem.jdbx;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;

/**
 * Writes a Result in the {@code json} Format (an array with one object per row, one object per line) or, when
 * {@code pretty}, in the {@code json-pretty} Format (two-space indent, one key per line). Never cuts data.
 * A json or jsonb value is inserted as the database sent it, without re-indentation.
 */
public class JsonFormatter implements ResultFormatter {

	private final ValueRenderer values;

	private final boolean pretty;

	public JsonFormatter(FormatOptions options) {
		this(options, false);
	}

	public JsonFormatter(FormatOptions options, boolean pretty) {
		this.pretty = pretty;
		this.values = new ValueRenderer(options);
	}

	@Override
	public void write(RowSource source, Writer out) throws IOException {
		Iterator<List<Object>> rows = source.rows();
		if (!rows.hasNext()) {
			out.append("[]\n");
			return;
		}
		out.append("[\n");
		while (rows.hasNext()) {
			List<Object> row = rows.next();
			StringBuilder line = new StringBuilder(pretty ? "  {\n" : "{");
			for (int i = 0; i < source.columns().size(); i++) {
				if (i > 0) {
					line.append(pretty ? ",\n" : ",");
				}
				if (pretty) {
					line.append("    ");
				}
				string(line, source.columns().get(i));
				line.append(pretty ? ": " : ":");
				value(line, row.get(i));
			}
			out.append(line).append(pretty ? "\n  }" : "}").append(rows.hasNext() ? ",\n" : "\n");
		}
		out.append("]\n");
	}

	private void value(StringBuilder out, Object value) {
		switch (value) {
			case null -> out.append("null");
			case Boolean b -> out.append(b);
			case JsonValue json -> out.append(json.text());
			case BigDecimal number -> out.append(number.toPlainString());
			case Double d when d.isNaN() || d.isInfinite() -> string(out, d.toString());
			case Float f when f.isNaN() || f.isInfinite() -> string(out, f.toString());
			case Number number -> out.append(number);
			default -> string(out, values.plain(value));
		}
	}

	private static void string(StringBuilder out, String text) {
		out.append('"');
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			switch (c) {
				case '"' -> out.append("\\\"");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '\t' -> out.append("\\t");
				case '\b' -> out.append("\\b");
				case '\f' -> out.append("\\f");
				default -> {
					if (c < 0x20) {
						out.append(String.format("\\u%04x", (int) c));
					}
					else {
						out.append(c);
					}
				}
			}
		}
		out.append('"');
	}
}
