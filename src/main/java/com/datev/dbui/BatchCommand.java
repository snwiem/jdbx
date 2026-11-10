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
					spec.commandLine().getOut().print(new TableFormatter().format(read(resultSet)));
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
		for (int i = 1; i <= count; i++) {
			columns.add(metaData.getColumnLabel(i));
		}
		List<List<Object>> rows = new ArrayList<>();
		while (resultSet.next()) {
			List<Object> row = new ArrayList<>();
			for (int i = 1; i <= count; i++) {
				row.add(resultSet.getObject(i));
			}
			rows.add(row);
		}
		return new Result(columns, rows);
	}

}
