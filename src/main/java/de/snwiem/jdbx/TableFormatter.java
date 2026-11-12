package de.snwiem.jdbx;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Writes a Result in the {@code table} Format. */
public class TableFormatter implements ResultFormatter {

	private final ValueRenderer values;

	public TableFormatter() {
		this(FormatOptions.defaults());
	}

	private final Orientation orientation;

	public TableFormatter(FormatOptions options) {
		this(options, Orientation.HORIZONTAL);
	}

	public TableFormatter(FormatOptions options, Orientation orientation) {
		this.values = new ValueRenderer(options);
		this.orientation = orientation;
	}

	/** Rows read before the column widths of horizontal output are fixed. */
	static final int SAMPLE_ROWS = 1000;

	/**
	 * Horizontal: buffers up to {@link #SAMPLE_ROWS} rows, takes the column widths from them, writes them, then
	 * streams the rest with the same widths. A later value that is wider overflows its column (it is cut only by
	 * {@code max-width}). Vertical: buffers the whole Result, because every line needs one value of every row.
	 */
	@Override
	public void write(RowSource source, Writer out) throws IOException {
		write(source, out, SAMPLE_ROWS);
	}

	/** In-memory Results (including the Transcript) use all their rows for aligned column widths. */
	@Override
	public void write(Result result, Writer out) throws IOException {
		write(RowSource.of(result), out, result.rows().size());
	}

	private void write(RowSource source, Writer out, int sampleRows) throws IOException {
		if (orientation == Orientation.VERTICAL) {
			writeVertical(source, out);
			return;
		}
		List<String> columns = source.columns();
		int count = columns.size();
		Iterator<List<Object>> rows = source.rows();
		List<List<String>> sample = new ArrayList<>();
		while (sample.size() < sampleRows && rows.hasNext()) {
			sample.add(rows.next().stream().map(values::forTable).toList());
		}
		int[] widths = new int[count];
		for (int i = 0; i < count; i++) {
			widths[i] = columns.get(i).length();
			for (List<String> row : sample) {
				widths[i] = Math.max(widths[i], row.get(i).length());
			}
		}
		line(out, columns, widths);
		StringBuilder separator = new StringBuilder();
		for (int i = 0; i < count; i++) {
			separator.append(i > 0 ? "-+-" : "").append("-".repeat(widths[i]));
		}
		out.append(separator).append('\n');
		for (List<String> row : sample) {
			line(out, row, widths);
		}
		while (rows.hasNext()) {
			line(out, rows.next().stream().map(values::forTable).toList(), widths);
		}
	}

	/** One line per column: the name, then one value per Result row. No header, no separator. */
	private void writeVertical(RowSource source, Writer out) throws IOException {
		List<List<String>> lines = new ArrayList<>();
		for (String column : source.columns()) {
			lines.add(new ArrayList<>(List.of(column)));
		}
		Iterator<List<Object>> rows = source.rows();
		while (rows.hasNext()) {
			List<Object> row = rows.next();
			for (int c = 0; c < lines.size(); c++) {
				lines.get(c).add(values.forTable(row.get(c)));
			}
		}
		int[] widths = new int[lines.isEmpty() ? 0 : lines.get(0).size()];
		for (List<String> cells : lines) {
			for (int i = 0; i < cells.size(); i++) {
				widths[i] = Math.max(widths[i], cells.get(i).length());
			}
		}
		for (List<String> cells : lines) {
			line(out, cells, widths);
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
				line.append(" ".repeat(Math.max(0, widths[i] - cells.get(i).length())));
			}
		}
		out.append(line).append('\n');
	}
}
