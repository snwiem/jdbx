package com.datev.dbui;

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
 * value. {@code json} and {@code jsonb} become {@link JsonValue}. JDBC types stay in this class.
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

	private final boolean[] jsonColumns;

	private final Class<?>[] temporal;

	private boolean truncated;

	/** @param maxRows at most this many rows are yielded ({@code null}: all) */
	JdbcRowSource(ResultSet resultSet, Integer maxRows) throws SQLException {
		this.resultSet = resultSet;
		this.maxRows = maxRows;
		ResultSetMetaData metaData = resultSet.getMetaData();
		count = metaData.getColumnCount();
		jsonColumns = new boolean[count + 1];
		temporal = new Class<?>[count + 1];
		for (int i = 1; i <= count; i++) {
			columns.add(metaData.getColumnLabel(i));
			String type = metaData.getColumnTypeName(i);
			jsonColumns[i] = "json".equals(type) || "jsonb".equals(type);
			temporal[i] = switch (type) {
				case "timestamp" -> java.time.LocalDateTime.class;
				case "timestamptz" -> java.time.OffsetDateTime.class;
				case "date" -> java.time.LocalDate.class;
				case "time" -> java.time.LocalTime.class;
				default -> null;
			};
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
						Object value = temporal[i] == null ? resultSet.getObject(i) : resultSet.getObject(i, temporal[i]);
						row.add(value != null && jsonColumns[i] ? new JsonValue(value.toString()) : value);
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
