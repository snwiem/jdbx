package org.syracus.jdbx;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Reads JDBC values identically in Interactive mode and Batch mode, without shifting local temporal values. */
public final class JdbcResultReader {

	private JdbcResultReader() { }

	public record Read(Result result, boolean truncated) { }

	/** Reads at most {@code maxRows} rows (null: all), probing one extra row to detect truncation. */
	public static Read read(ResultSet resultSet, Integer maxRows) throws SQLException {
		var source = new JdbcRowSource(resultSet, maxRows);
		List<List<Object>> rows = new ArrayList<>();
		try {
			source.rows().forEachRemaining(rows::add);
		}
		catch (JdbcRowSource.RowReadException e) {
			throw (SQLException) e.getCause();
		}
		return new Read(new Result(source.columns(), rows), source.truncated());
	}

	/** Shared conversion for the streaming reader and the list-based Result. JDBC column indexes start at 1. */
	static Object readValue(ResultSet resultSet, int column, String type) throws SQLException {
		Object value = switch (type) {
			case "timestamp" -> resultSet.getObject(column, java.time.LocalDateTime.class);
			case "timestamptz" -> resultSet.getObject(column, java.time.OffsetDateTime.class);
			case "date" -> resultSet.getObject(column, java.time.LocalDate.class);
			case "time" -> resultSet.getObject(column, java.time.LocalTime.class);
			default -> resultSet.getObject(column);
		};
		return value != null && ("json".equals(type) || "jsonb".equals(type)) ? new JsonValue(value.toString()) : value;
	}
}
