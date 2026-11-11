package com.datev.dbui;

import java.util.Arrays;
import java.util.Locale;

/** How a Result is written. */
public enum Format {

	TABLE, MARKDOWN, CSV, TSV, JSON, JSON_PRETTY;

	public String id() {
		return name().toLowerCase(Locale.ROOT).replace('_', '-');
	}

	/** True if Batch Results follow each other without a blank line: every Result is a complete JSON document. */
	public boolean isDocumentStream() {
		return this == JSON || this == JSON_PRETTY;
	}

	public ResultFormatter formatter(FormatOptions options) {
		return formatter(options, Orientation.HORIZONTAL);
	}

	/** {@code json} and {@code json-pretty} ignore the Orientation. */
	public ResultFormatter formatter(FormatOptions options, Orientation orientation) {
		return switch (this) {
			case TABLE -> new TableFormatter(options, orientation);
			case MARKDOWN -> new MarkdownFormatter(options, orientation);
			case CSV -> new CsvFormatter(options, orientation);
			case TSV -> new TsvFormatter(options, orientation);
			case JSON -> new JsonFormatter(options, false);
			case JSON_PRETTY -> new JsonFormatter(options, true);
		};
	}

	public static Format parse(String text) {
		String wanted = text.trim().toLowerCase(Locale.ROOT);
		return Arrays.stream(values()).filter(f -> f.id().equals(wanted)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown format '" + text
						+ "'. Use one of: table, markdown, csv, tsv, json, json-pretty"));
	}
}
