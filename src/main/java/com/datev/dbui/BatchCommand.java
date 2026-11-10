package com.datev.dbui;

import java.io.Console;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import com.datev.dbui.config.ConfigException;
import com.datev.dbui.config.ConfigLoader;
import com.datev.dbui.config.ConnectionOptions;
import com.datev.dbui.config.ConnectionParameters;
import com.datev.dbui.config.Profile;
import com.datev.dbui.config.Settings;
import com.datev.dbui.session.SessionContext;
import com.datev.dbui.session.SessionDirectory;
import com.datev.dbui.tui.InteractiveMode;

import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Connects, then selects Interactive mode (no -c, no -f, stdin is a terminal) or Batch mode (Statements to stdout,
 * never prompts).
 */
@Command(name = "dbui", mixinStandardHelpOptions = true,
		description = "Runs SQL in Interactive mode, or in Batch mode with -c, -f or stdin.")
class BatchCommand implements Callable<Integer> {

	@Spec
	CommandSpec spec;

	@Mixin
	ConnectionOptions connectionOptions = new ConnectionOptions();

	@Option(names = { "-c", "--command" }, description = "Statements to run (repeatable)")
	List<String> commands = new ArrayList<>();

	@Option(names = { "-f", "--file" }, description = "File with Statements to run ('-' reads stdin)")
	List<String> files = new ArrayList<>();

	@Option(names = "--format", converter = FormatConverter.class,
			description = "Output Format: table, markdown, csv, tsv or json (default: 'format' in config.yaml, else table)")
	Format format;

	@Option(names = "--orientation", converter = OrientationConverter.class,
			description = "Layout: horizontal or vertical (default: from settings, else horizontal)")
	Orientation orientation;

	static class OrientationConverter implements ITypeConverter<Orientation> {
		@Override
		public Orientation convert(String value) {
			return Orientation.parse(value);
		}
	}

	@Option(names = "--limit", paramLabel = "N",
			description = "Batch mode: write at most N rows per Result (default: no limit)")
	Integer limit;

	static class FormatConverter implements ITypeConverter<Format> {
		@Override
		public Format convert(String value) {
			return Format.parse(value);
		}
	}

	@Override
	public Integer call() {
		if (limit != null && limit < 1) {
			spec.commandLine().getErr().println("Error: --limit must be 1 or more, but is " + limit + ".");
			return 2;
		}
		ConnectionParameters parameters;
		Settings settings;
		ConfigLoader loader;
		try {
			Map<String, String> environment = System.getenv();
			loader = new ConfigLoader(ConfigLoader.directory(connectionOptions.configDir, environment,
					Path.of(System.getProperty("user.home"))), environment);
			settings = loader.settings();
			Profile profile = connectionOptions.profile == null ? null : loader.profile(connectionOptions.profile);
			parameters = ConnectionParameters.resolve(connectionOptions, profile, settings);
		}
		catch (ConfigException e) {
			spec.commandLine().getErr().println("Error: " + e.getMessage());
			return 1;
		}
		try (Connection connection = parameters.open()) {
			if (commands.isEmpty() && files.isEmpty() && stdinIsTerminal()) {
				Settings interactiveSettings = settings.withExecution(settings.rowLimit(), settings.timeout(), parameters.readOnly())
					.withOutput((format == null ? Format.parse(settings.format()) : format).id(), orientation(settings).id());
				return InteractiveMode.run(connection, label(parameters), new SessionContext(SessionDirectory.defaultStateBase(),
						interactiveSettings, parameters,
						connectionOptions.profile, loader::profile));
			}
			return runBatch(connection, settings);
		}
		catch (IOException e) {
			spec.commandLine().getErr().println("Error: " + e.getMessage());
			return 1;
		}
		catch (SQLException e) {
			spec.commandLine().getErr().println("Error: " + e.getMessage());
			return 1;
		}
		catch (Exception e) {
			spec.commandLine().getErr().println("Error: " + e.getMessage());
			return 1;
		}
	}

	private Orientation orientation(Settings settings) {
		return orientation != null ? orientation : Orientation.parse(settings.orientation());
	}

	private static String label(ConnectionParameters parameters) {
		String target = parameters.url().replaceFirst("^jdbc:postgresql://", "").replaceFirst("\\?.*$", "");
		String user = parameters.properties().get("user");
		return user == null ? target : user + "@" + target;
	}

	private Integer runBatch(Connection connection, Settings settings) throws IOException, SQLException {
		try (Statement jdbcStatement = connection.createStatement()) {
			if (settings.timeout() != null) {
				jdbcStatement.setQueryTimeout((int) settings.timeout().toSeconds());
			}
			jdbcStatement.setFetchSize(StreamingRead.FETCH_SIZE);
			if (limit != null) {
				jdbcStatement.setMaxRows(limit == Integer.MAX_VALUE ? limit : limit + 1);
			}
			List<String> statements = statements();
			if (statements.isEmpty()) {
				spec.commandLine().getErr()
						.println("Error: No input. Use -c or -f, pipe Statements to stdin, or run in a terminal for Interactive mode.");
				return 2;
			}
			Format effectiveFormat = format != null ? format : Format.parse(settings.format());
			FormatOptions options = formatOptions(settings);
			boolean first = true;
			for (String statement : statements) {
				boolean streaming = StreamingRead.isQuery(statement);
				try {
					if (streaming) {
						StreamingRead.begin(connection, settings.timeout());
					}
					if (jdbcStatement.execute(statement)) {
						try (ResultSet resultSet = jdbcStatement.getResultSet()) {
							PrintWriter out = spec.commandLine().getOut();
							if (!first) {
								out.print('\n');
							}
							JdbcRowSource source = new JdbcRowSource(resultSet, limit);
							effectiveFormat.formatter(options, orientation(settings)).write(source, out);
							out.flush();
							if (source.truncated()) {
								spec.commandLine().getErr().println("truncated at " + limit + " rows");
								spec.commandLine().getErr().flush();
							}
							first = false;
						}
					}
					if (streaming) {
						connection.commit();
					}
				}
				catch (JdbcRowSource.RowReadException e) {
					throw (SQLException) e.getCause();
				}
				finally {
					if (streaming) {
						StreamingRead.restoreAutoCommit(connection);
					}
				}
			}
			return 0;
		}
	}

	private static FormatOptions formatOptions(Settings settings) {
		return FormatOptions.from(settings);
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

}
