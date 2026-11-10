package com.datev.dbui;

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
		var metaData = resultSet.getMetaData();
		int count = metaData.getColumnCount();
		List<String> columns = new ArrayList<>();
		boolean[] jsonColumns = new boolean[count + 1];
		Class<?>[] temporal = new Class<?>[count + 1];
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
		List<List<Object>> rows = new ArrayList<>();
		boolean truncated = false;
		while (resultSet.next()) {
			if (maxRows != null && rows.size() >= maxRows) {
				truncated = true;
				break;
			}
			List<Object> row = new ArrayList<>();
			for (int i = 1; i <= count; i++) {
				Object value = temporal[i] == null ? resultSet.getObject(i) : resultSet.getObject(i, temporal[i]);
				row.add(value != null && jsonColumns[i] ? new JsonValue(value.toString()) : value);
			}
			rows.add(row);
		}
		return new Read(new Result(columns, rows), truncated);
	}
}
