package com.datev.dbui.schema;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Schema metadata read only through {@link DatabaseMetaData} (ADR 0005) and cached until {@link #refresh()}.
 * Not thread-safe: use it from one thread at a time, like the connection it wraps. It knows neither Spring
 * nor the terminal so that Completion can reuse the cache.
 */
public final class SchemaProvider {

	private static final Set<String> SYSTEM_SCHEMAS = Set.of("pg_catalog", "information_schema", "pg_toast");

	public record TableInfo(String name, String type, String remarks) {
	}

	public record ColumnInfo(String name, String type, boolean nullable, String defaultValue, int position, String remarks) {
	}

	public record KeyInfo(String name, List<String> columns) {
	}

	/** A foreign key. {@code columns} are on the described table, {@code other*} on the table at the other end. */
	public record ForeignKeyInfo(String name, List<String> columns, String otherSchema, String otherTable,
			List<String> otherColumns) {
	}

	public record IndexInfo(String name, List<String> columns, boolean unique) {
	}

	public record TableDescription(String schema, String name, String type, String remarks, List<ColumnInfo> columns,
			KeyInfo primaryKey, List<ForeignKeyInfo> references, List<ForeignKeyInfo> referencedBy,
			List<IndexInfo> indexes) {
	}

	private final Connection connection;
	private List<String> schemas;
	private final Map<String, List<TableInfo>> tables = new HashMap<>();
	private final Map<String, Optional<TableDescription>> descriptions = new HashMap<>();

	public SchemaProvider(Connection connection) {
		this.connection = connection;
	}

	/** Drops all cached metadata. */
	public void refresh() {
		schemas = null;
		tables.clear();
		descriptions.clear();
	}

	public String currentSchema() throws SQLException {
		return connection.getSchema();
	}

	public List<String> schemas(boolean includeSystem) throws SQLException {
		if (schemas == null) {
			List<String> loaded = new ArrayList<>();
			try (ResultSet rs = connection.getMetaData().getSchemas()) {
				while (rs.next()) {
					loaded.add(rs.getString("TABLE_SCHEM"));
				}
			}
			schemas = loaded;
		}
		return schemas.stream().filter(s -> includeSystem || !isSystem(s)).toList();
	}

	private static boolean isSystem(String schema) {
		return SYSTEM_SCHEMAS.contains(schema);
	}

	public List<TableInfo> tables(String schema) throws SQLException {
		var cached = tables.get(schema);
		if (cached != null) {
			return cached;
		}
		List<TableInfo> loaded = new ArrayList<>();
		try (ResultSet rs = connection.getMetaData().getTables(null, schema, "%", new String[] { "TABLE", "VIEW" })) {
			while (rs.next()) {
				loaded.add(new TableInfo(rs.getString("TABLE_NAME"), rs.getString("TABLE_TYPE"), nullToEmpty(rs.getString("REMARKS"))));
			}
		}
		loaded.sort(java.util.Comparator.comparing(TableInfo::name));
		tables.put(schema, loaded);
		return loaded;
	}

	/** Empty when the schema has no table or view of exactly this name. */
	public Optional<TableDescription> describe(String schema, String table) throws SQLException {
		String key = schema + "\0" + table;
		var cached = descriptions.get(key);
		if (cached != null) {
			return cached;
		}
		Optional<TableDescription> loaded = load(schema, table);
		descriptions.put(key, loaded);
		return loaded;
	}

	private Optional<TableDescription> load(String schema, String table) throws SQLException {
		var info = tables(schema).stream().filter(t -> t.name().equals(table)).findFirst();
		if (info.isEmpty()) {
			return Optional.empty();
		}
		DatabaseMetaData meta = connection.getMetaData();
		return Optional.of(new TableDescription(schema, table, info.get().type(), info.get().remarks(),
			columns(meta, schema, table), primaryKey(meta, schema, table),
			foreignKeys(meta.getImportedKeys(null, schema, table), true),
			foreignKeys(meta.getExportedKeys(null, schema, table), false), indexes(meta, schema, table)));
	}

	private static List<ColumnInfo> columns(DatabaseMetaData meta, String schema, String table) throws SQLException {
		List<ColumnInfo> columns = new ArrayList<>();
		try (ResultSet rs = meta.getColumns(null, schema, table, "%")) {
			while (rs.next()) {
				columns.add(new ColumnInfo(rs.getString("COLUMN_NAME"), rs.getString("TYPE_NAME"),
					rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls, rs.getString("COLUMN_DEF"),
					rs.getInt("ORDINAL_POSITION"), nullToEmpty(rs.getString("REMARKS"))));
			}
		}
		columns.sort(java.util.Comparator.comparingInt(ColumnInfo::position));
		return columns;
	}

	private static KeyInfo primaryKey(DatabaseMetaData meta, String schema, String table) throws SQLException {
		TreeMap<Integer, String> columns = new TreeMap<>();
		String name = null;
		try (ResultSet rs = meta.getPrimaryKeys(null, schema, table)) {
			while (rs.next()) {
				columns.put(rs.getInt("KEY_SEQ"), rs.getString("COLUMN_NAME"));
				name = rs.getString("PK_NAME");
			}
		}
		return columns.isEmpty() ? null : new KeyInfo(name, List.copyOf(columns.values()));
	}

	/** Reads imported keys ({@code imported}: other = PK side) or exported keys (other = FK side). */
	private static List<ForeignKeyInfo> foreignKeys(ResultSet rs, boolean imported) throws SQLException {
		record Group(String name, String otherSchema, String otherTable, TreeMap<Integer, String[]> columns) {
		}
		Map<String, Group> groups = new LinkedHashMap<>();
		try (rs) {
			while (rs.next()) {
				String name = rs.getString("FK_NAME");
				String otherSchema = rs.getString(imported ? "PKTABLE_SCHEM" : "FKTABLE_SCHEM");
				String otherTable = rs.getString(imported ? "PKTABLE_NAME" : "FKTABLE_NAME");
				var group = groups.computeIfAbsent(otherSchema + "\0" + otherTable + "\0" + name,
					k -> new Group(name, otherSchema, otherTable, new TreeMap<>()));
				String own = rs.getString(imported ? "FKCOLUMN_NAME" : "PKCOLUMN_NAME");
				String other = rs.getString(imported ? "PKCOLUMN_NAME" : "FKCOLUMN_NAME");
				group.columns().put(rs.getInt("KEY_SEQ"), new String[] { own, other });
			}
		}
		return groups.values().stream()
			.map(g -> new ForeignKeyInfo(g.name(), g.columns().values().stream().map(c -> c[0]).toList(), g.otherSchema(),
				g.otherTable(), g.columns().values().stream().map(c -> c[1]).toList()))
			.sorted(java.util.Comparator.comparing(ForeignKeyInfo::otherSchema).thenComparing(ForeignKeyInfo::otherTable)
				.thenComparing(ForeignKeyInfo::name))
			.toList();
	}

	private static List<IndexInfo> indexes(DatabaseMetaData meta, String schema, String table) throws SQLException {
		record Group(boolean unique, TreeMap<Integer, String> columns) {
		}
		Map<String, Group> groups = new TreeMap<>();
		try (ResultSet rs = meta.getIndexInfo(null, schema, table, false, false)) {
			while (rs.next()) {
				String name = rs.getString("INDEX_NAME");
				String column = rs.getString("COLUMN_NAME");
				if (name == null || column == null) {
					continue;
				}
				boolean unique = !rs.getBoolean("NON_UNIQUE");
				groups.computeIfAbsent(name, k -> new Group(unique, new TreeMap<>())).columns()
					.put((int) rs.getShort("ORDINAL_POSITION"), column);
			}
		}
		return groups.entrySet().stream()
			.map(e -> new IndexInfo(e.getKey(), List.copyOf(e.getValue().columns().values()), e.getValue().unique())).toList();
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
