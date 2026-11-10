package com.datev.dbui;

import java.util.Arrays;
import java.util.Locale;

/** How a Result is written. */
public enum Format {

	TABLE, MARKDOWN, CSV, TSV, JSON;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public ResultFormatter formatter(FormatOptions options) {
		return formatter(options, Orientation.HORIZONTAL);
	}

	/** {@code json} ignores the Orientation. */
	public ResultFormatter formatter(FormatOptions options, Orientation orientation) {
		return switch (this) {
			case TABLE -> new TableFormatter(options, orientation);
			case MARKDOWN -> new MarkdownFormatter(options, orientation);
			case CSV -> new CsvFormatter(options, orientation);
			case TSV -> new TsvFormatter(options, orientation);
			case JSON -> new JsonFormatter(options);
		};
	}

	public static Format parse(String text) {
		String wanted = text.trim().toLowerCase(Locale.ROOT);
		return Arrays.stream(values()).filter(f -> f.id().equals(wanted)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown format '" + text
						+ "'. Use one of: table, markdown, csv, tsv, json"));
	}
}
