package com.datev.dbui;

import java.io.Console;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

/** Batch mode: connect, run Statements, write the Results to stdout. Never prompts. */
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

	@Option(names = { "-c", "--command" }, description = "Statements to run (repeatable)")
	List<String> commands = new ArrayList<>();

	@Option(names = { "-f", "--file" }, description = "File with Statements to run ('-' reads stdin)")
	List<String> files = new ArrayList<>();

	@Override
	public Integer call() {
		Properties properties = new Properties();
		properties.setProperty("user", user);
		properties.setProperty("password", password);
		properties.setProperty("ApplicationName", "dbui");
		String url = "jdbc:postgresql://" + host + ":" + port + "/" + db;
		try (Connection connection = DriverManager.getConnection(url, properties);
				Statement jdbcStatement = connection.createStatement()) {
			boolean first = true;
			for (String statement : statements()) {
				if (jdbcStatement.execute(statement)) {
					try (ResultSet resultSet = jdbcStatement.getResultSet()) {
						PrintWriter out = spec.commandLine().getOut();
						if (!first) {
							out.print('\n');
						}
						out.print(new TableFormatter().format(read(resultSet)));
						out.flush();
						first = false;
					}
				}
			}
			return 0;
		}
		catch (IOException e) {
			spec.commandLine().getErr().println("Error: " + e.getMessage());
			return 1;
		}
		catch (SQLException e) {
			spec.commandLine().getErr().println("Error: " + e.getMessage());
			return 1;
		}
	}

	/** Statements of all inputs: {@code -c} values, then {@code -f} files; stdin if none is given and it is no terminal. */
	private List<String> statements() throws IOException {
		StatementSplitter splitter = new StatementSplitter();
		List<String> statements = new ArrayList<>();
		for (String command : commands) {
			statements.addAll(splitter.split(command));
		}
		for (String file : files) {
			String text = file.equals("-") ? readStdin() : Files.readString(Path.of(file));
			statements.addAll(splitter.split(text));
		}
		if (commands.isEmpty() && files.isEmpty() && !stdinIsTerminal()) {
			statements.addAll(splitter.split(readStdin()));
		}
		return statements;
	}

	private static boolean stdinIsTerminal() {
		Console console = System.console();
		return console != null && console.isTerminal();
	}

	private static String readStdin() throws IOException {
		return new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
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
