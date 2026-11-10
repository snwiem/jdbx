package com.datev.dbui;

import java.util.Iterator;
import java.util.List;

/**
 * Column names and a one-pass iterator of rows. Formatters read rows from it one by one and write them at once, so a
 * Result never has to be held in memory. Pure: no JDBC types. Interactive mode and {@code /dump} can reuse it.
 */
public interface RowSource {

	List<String> columns();

	/** The rows, once. Each row has one value per column. */
	Iterator<List<Object>> rows();

	static RowSource of(List<String> columns, Iterator<List<Object>> rows) {
		return new RowSource() {
			@Override
			public List<String> columns() {
				return columns;
			}

			@Override
			public Iterator<List<Object>> rows() {
				return rows;
			}
		};
	}

	static RowSource of(Result result) {
		return of(result.columns(), result.rows().iterator());
	}
}
