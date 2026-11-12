package com.datev.dbui.schema;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.datev.dbui.Result;
import com.datev.dbui.session.MessageBlock;

/** Runs the schema Slash commands and turns their output into Message blocks. */
public final class SchemaCommands {

	private final SchemaProvider provider;

	public SchemaCommands(SchemaProvider provider) {
		this.provider = provider;
	}

	/** Whether the input is a schema Slash command. */
	public static boolean handles(String input) {
		return SchemaCommandLine.parse(input).isPresent();
	}

	/** Blocks for one command; call only when {@link #handles} is true. May query the database. */
	public List<MessageBlock> run(String input) throws SQLException {
		var line = SchemaCommandLine.parse(input).orElseThrow();
		return run(line.command(), line.argument());
	}

	/** Executes arguments already parsed by the Session's Slash command registry. */
	public List<MessageBlock> run(String command, String argument) throws SQLException {
		try {
			return switch (command) {
				case "schema" -> schemas(argument);
				case "tables" -> tables(argument);
				case "describe" -> describe(argument);
				default -> refresh();
			};
		}
		catch (IllegalArgumentException e) {
			return List.of(MessageBlock.error(e.getMessage()));
		}
	}

	private List<MessageBlock> refresh() {
		provider.refresh();
		return List.of(MessageBlock.info("Schema cache cleared. It reloads on next use."));
	}

	private List<MessageBlock> schemas(String argument) throws SQLException {
		if (!argument.isEmpty() && !argument.equals("all")) {
			return List.of(MessageBlock.error("Usage: /schema [all]"));
		}
		List<List<Object>> rows = new ArrayList<>();
		for (String schema : provider.schemas(argument.equals("all"))) {
			rows.add(row(schema));
		}
		return List.of(MessageBlock.result(new Result(List.of("schema"), rows)));
	}

	private List<MessageBlock> tables(String argument) throws SQLException {
		String schema = provider.currentSchema();
		if (!argument.isEmpty()) {
			var name = QualifiedName.parse(argument);
			if (name.schema() != null) {
				return List.of(MessageBlock.error("Usage: /tables [schema]"));
			}
			schema = name.name();
			if (!provider.schemas(true).contains(schema)) {
				return List.of(MessageBlock.error("Unknown schema: " + argument));
			}
		}
		List<List<Object>> rows = new ArrayList<>();
		for (var table : provider.tables(schema)) {
			rows.add(row(table.name(), table.type(), table.remarks()));
		}
		return List.of(MessageBlock.result(new Result(List.of("name", "type", "remarks"), rows)));
	}

	/** The schemas, then the tables and views of the current schema, one row each with its kind. */
	private List<MessageBlock> overview() throws SQLException {
		List<List<Object>> rows = new ArrayList<>();
		for (String schema : provider.schemas(false)) {
			rows.add(row(schema, "schema"));
		}
		for (var table : provider.tables(provider.currentSchema())) {
			rows.add(row(table.name(), table.type().toLowerCase(java.util.Locale.ROOT)));
		}
		return List.of(MessageBlock.result(new Result(List.of("name", "type"), rows)));
	}

	private List<MessageBlock> schemaTables(String schema) throws SQLException {
		if (!provider.schemas(true).contains(schema)) {
			return List.of(MessageBlock.error("Unknown schema: " + schema));
		}
		List<List<Object>> rows = new ArrayList<>();
		for (var table : provider.tables(schema)) {
			rows.add(row(table.name(), table.type(), table.remarks()));
		}
		return List.of(MessageBlock.result(new Result(List.of("name", "type", "remarks"), rows)));
	}

	private List<MessageBlock> describe(String argument) throws SQLException {
		if (argument.isEmpty()) {
			return overview();
		}
		if (argument.endsWith(".")) {
			return schemaTables(QualifiedName.parse(argument.substring(0, argument.length() - 1)).name());
		}
		var name = QualifiedName.parse(argument);
		String schema = name.schema() != null ? name.schema() : provider.currentSchema();
		Optional<SchemaProvider.TableDescription> found = provider.describe(schema, name.name());
		if (found.isEmpty() && name.schema() == null && provider.schemas(true).contains(name.name())) {
			return schemaTables(name.name());
		}
		if (found.isEmpty()) {
			return List.of(MessageBlock.error("Unknown table: " + argument));
		}
		var table = found.get();
		List<MessageBlock> blocks = new ArrayList<>();
		blocks.add(MessageBlock.info(table.schema() + "." + table.name() + " (" + table.type() + ")"
			+ (table.remarks().isEmpty() ? "" : ": " + table.remarks())));
		List<List<Object>> columns = new ArrayList<>();
		for (var c : table.columns()) {
			columns.add(row(c.name(), c.type(), c.nullable() ? "YES" : "NO", c.defaultValue(), c.position(), c.remarks()));
		}
		blocks.add(MessageBlock.result(new Result(List.of("name", "type", "nullable", "default", "position", "remarks"), columns)));
		if (table.primaryKey() != null) {
			blocks.add(MessageBlock.info("Primary key"));
			blocks.add(MessageBlock.result(new Result(List.of("name", "columns"),
				List.of(row(table.primaryKey().name(), String.join(", ", table.primaryKey().columns()))))));
		}
		foreignKeys(blocks, "References", table.references());
		foreignKeys(blocks, "Referenced by", table.referencedBy());
		if (!table.indexes().isEmpty()) {
			blocks.add(MessageBlock.info("Indexes"));
			List<List<Object>> rows = new ArrayList<>();
			for (var i : table.indexes()) {
				rows.add(row(i.name(), String.join(", ", i.columns()), i.unique() ? "YES" : "NO"));
			}
			blocks.add(MessageBlock.result(new Result(List.of("name", "columns", "unique"), rows)));
		}
		return blocks;
	}

	private static void foreignKeys(List<MessageBlock> blocks, String label, List<SchemaProvider.ForeignKeyInfo> keys) {
		if (keys.isEmpty()) {
			return;
		}
		blocks.add(MessageBlock.info("Foreign keys: " + label.toLowerCase()));
		List<List<Object>> rows = new ArrayList<>();
		for (var k : keys) {
			rows.add(row(k.name(), String.join(", ", k.columns()), k.otherSchema() + "." + k.otherTable(),
				String.join(", ", k.otherColumns())));
		}
		blocks.add(MessageBlock.result(new Result(List.of("name", "columns", "table", "table columns"), rows)));
	}

	private static List<Object> row(Object... values) {
		return new ArrayList<>(Arrays.asList(values));
	}
}
