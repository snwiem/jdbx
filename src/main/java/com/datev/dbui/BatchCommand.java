package com.datev.dbui;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import com.datev.dbui.config.ConfigException;
import com.datev.dbui.config.ConfigLoader;
import com.datev.dbui.config.ConnectionOptions;
import com.datev.dbui.config.ConnectionParameters;
import com.datev.dbui.config.Profile;
import com.datev.dbui.config.Settings;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** Batch mode: connect, run one Statement, write the Result to stdout. Never prompts. */
@Command(name = "dbui", mixinStandardHelpOptions = true, description = "Runs a SQL Statement in Batch mode.")
class BatchCommand implements Callable<Integer> {

	@Spec
	CommandSpec spec;

	@Mixin
	ConnectionOptions connectionOptions = new ConnectionOptions();

	@Option(names = { "-c", "--command" }, required = true, description = "Statement to run")
	String statement;

	@Override
	public Integer call() {
		ConnectionParameters parameters;
		Settings settings;
		try {
			Map<String, String> environment = System.getenv();
			ConfigLoader loader = new ConfigLoader(ConfigLoader.directory(connectionOptions.configDir, environment,
					Path.of(System.getProperty("user.home"))), environment);
			settings = loader.settings();
			Profile profile = connectionOptions.profile == null ? null : loader.profile(connectionOptions.profile);
			parameters = ConnectionParameters.resolve(connectionOptions, profile, settings);
		}
		catch (ConfigException e) {
			spec.commandLine().getErr().println("Error: " + e.getMessage());
			return 1;
		}
		try (Connection connection = parameters.open(); Statement jdbcStatement = connection.createStatement()) {
			if (settings.timeout() != null) {
				jdbcStatement.setQueryTimeout((int) settings.timeout().toSeconds());
			}
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
