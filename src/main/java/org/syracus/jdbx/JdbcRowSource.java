package org.syracus.jdbx;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * A {@link RowSource} over a JDBC {@link ResultSet}, read one row at a time. Plain {@code timestamp}, {@code date} and
 * {@code time} are read as local values so that no time zone shifts them. {@code timestamptz} is read as an offset
 * value. {@code json} and {@code jsonb} become {@link JsonValue}. {@link JdbcResultReader} shares the value conversion.
 */
final class JdbcRowSource implements RowSource {

	/** Wraps the {@link SQLException} that a row read throws inside an {@link Iterator}. */
	static final class RowReadException extends RuntimeException {
		RowReadException(SQLException cause) {
			super(cause.getMessage(), cause);
		}
	}

	private final ResultSet resultSet;

	private final Integer maxRows;

	private final int count;

	private final List<String> columns = new ArrayList<>();

	private final String[] types;

	private boolean truncated;

	/** @param maxRows at most this many rows are yielded ({@code null}: all) */
	JdbcRowSource(ResultSet resultSet, Integer maxRows) throws SQLException {
		this.resultSet = resultSet;
		this.maxRows = maxRows;
		ResultSetMetaData metaData = resultSet.getMetaData();
		count = metaData.getColumnCount();
		types = new String[count + 1];
		for (int i = 1; i <= count; i++) {
			columns.add(metaData.getColumnLabel(i));
			types[i] = metaData.getColumnTypeName(i);
		}
	}

	@Override
	public List<String> columns() {
		return columns;
	}

	/** True when the rows ended because of {@code maxRows} while the database had more. Valid after reading all rows. */
	boolean truncated() {
		return truncated;
	}

	@Override
	public Iterator<List<Object>> rows() {
		return new Iterator<>() {
			private int read;

			private boolean ready;

			@Override
			public boolean hasNext() {
				if (ready) {
					return true;
				}
				try {
					if (!resultSet.next()) {
						return false;
					}
					if (maxRows != null && read >= maxRows) {
						truncated = true;
						return false;
					}
				}
				catch (SQLException e) {
					throw new RowReadException(e);
				}
				ready = true;
				return true;
			}

			@Override
			public List<Object> next() {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}
				ready = false;
				read++;
				try {
					List<Object> row = new ArrayList<>(count);
					for (int i = 1; i <= count; i++) {
						row.add(JdbcResultReader.readValue(resultSet, i, types[i]));
					}
					return row;
				}
				catch (SQLException e) {
					throw new RowReadException(e);
				}
			}
		};
	}
}
