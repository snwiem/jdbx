package org.syracus.jdbx;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;

/** Writes a Result in the {@code tsv} Format. Tab, newline and carriage return become {@code \t}, {@code \n}, {@code \r}. */
public class TsvFormatter implements ResultFormatter {

	private final ValueRenderer values;

	private final Orientation orientation;

	public TsvFormatter(FormatOptions options) {
		this(options, Orientation.HORIZONTAL);
	}

	public TsvFormatter(FormatOptions options, Orientation orientation) {
		this.values = new ValueRenderer(options);
		this.orientation = orientation;
	}

	@Override
	public void write(RowSource source, Writer out) throws IOException {
		if (orientation == Orientation.VERTICAL) {
			List<List<String>> records = new java.util.ArrayList<>();
			for (String column : source.columns()) {
				records.add(new java.util.ArrayList<>(List.of(column)));
			}
			Iterator<List<Object>> rows = source.rows();
			while (rows.hasNext()) {
				List<Object> row = rows.next();
				for (int c = 0; c < records.size(); c++) {
					records.get(c).add(cell(row.get(c)));
				}
			}
			for (List<String> record : records) {
				line(out, record);
			}
			return;
		}
		line(out, source.columns());
		Iterator<List<Object>> rows = source.rows();
		while (rows.hasNext()) {
			line(out, rows.next().stream().map(this::cell).toList());
		}
	}

	private String cell(Object value) {
		return value == null ? "" : values.plain(value);
	}

	private static void line(Writer out, List<String> cells) throws IOException {
		out.append(String.join("\t", cells.stream().map(TsvFormatter::escape).toList())).append('\n');
	}

	static String escape(String cell) {
		return cell.replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
	}
}
