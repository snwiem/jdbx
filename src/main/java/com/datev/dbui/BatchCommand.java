package com.datev.dbui;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** Batch mode: connect, run one Statement, write the Result to stdout. Never prompts. */
@Command(name = "dbui", mixinStandardHelpOptions = true, description = "Runs a SQL Statement in Batch mode.")
class BatchCommand implements Callable<Integer> {

	@Spec
	CommandSpec spec;

	@Option(names = "--host", required = true, description = "Database host")
	String host;

	@Option(names = "--port", defaultValue = "5432", description = "Database port (default: ${DEFAULT-VALUE})")
	int port;

	@Option(names = "--db", required = true, description = "Database name")
	String db;

	@Option(names = "--user", required = true, description = "Database user")
	String user;

	@Option(names = "--password", required = true, description = "Database password")
	String password;

	@Option(names = { "-c", "--command" }, required = true, description = "Statement to run")
	String statement;

	@Option(names = "--format", defaultValue = "table", converter = FormatConverter.class,
			description = "Output Format: table, markdown, csv, tsv or json (default: ${DEFAULT-VALUE})")
	Format format;

	static class FormatConverter implements ITypeConverter<Format> {
		@Override
		public Format convert(String value) {
			return Format.parse(value);
		}
	}

	@Override
	public Integer call() {
		Properties properties = new Properties();
		properties.setProperty("user", user);
		properties.setProperty("password", password);
		properties.setProperty("ApplicationName", "dbui");
		String url = "jdbc:postgresql://" + host + ":" + port + "/" + db;
		try (Connection connection = DriverManager.getConnection(url, properties);
				Statement jdbcStatement = connection.createStatement()) {
			if (jdbcStatement.execute(statement)) {
				try (ResultSet resultSet = jdbcStatement.getResultSet()) {
					spec.commandLine().getOut().print(format.formatter(FormatOptions.defaults()).format(read(resultSet)));
					spec.commandLine().getOut().flush();
				}
			}
			return 0;
		}
		catch (SQLException e) {
			spec.commandLine().getErr().println("Error: " + e.getMessage());
			return 1;
		}
	}

	private static Result read(ResultSet resultSet) throws SQLException {
		ResultSetMetaData metaData = resultSet.getMetaData();
		int count = metaData.getColumnCount();
		List<String> columns = new ArrayList<>();
		boolean[] jsonColumns = new boolean[count + 1];
		for (int i = 1; i <= count; i++) {
			columns.add(metaData.getColumnLabel(i));
			String type = metaData.getColumnTypeName(i);
			jsonColumns[i] = "json".equals(type) || "jsonb".equals(type);
		}
		List<List<Object>> rows = new ArrayList<>();
		while (resultSet.next()) {
			List<Object> row = new ArrayList<>();
			for (int i = 1; i <= count; i++) {
				Object value = resultSet.getObject(i);
				row.add(value != null && jsonColumns[i] ? new JsonValue(value.toString()) : value);
			}
			rows.add(row);
		}
		return new Result(columns, rows);
	}

}
