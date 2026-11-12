package com.datev.dbui.schema;

import java.sql.SQLException;
import java.util.List;

import com.datev.dbui.completion.SchemaModel;

/**
 * Feeds Completion from the {@link SchemaProvider} cache. A cache miss queries the database once; the next
 * request is answered from the cache. Not thread-safe, like the provider.
 */
public final class SchemaProviderModel implements SchemaModel {

	private final SchemaProvider provider;

	public SchemaProviderModel(SchemaProvider provider) {
		this.provider = provider;
	}

	@Override
	public List<String> schemas() {
		try {
			return provider.schemas(false);
		}
		catch (SQLException e) {
			throw new SchemaModel.Unavailable(e);
		}
	}

	@Override
	public String defaultSchema() {
		try {
			return provider.currentSchemaCached();
		}
		catch (SQLException e) {
			throw new SchemaModel.Unavailable(e);
		}
	}

	@Override
	public List<Table> tables(String schema) {
		try {
			return provider.tables(schema).stream().map(t -> new Table(t.name(), "VIEW".equals(t.type()))).toList();
		}
		catch (SQLException e) {
			throw new SchemaModel.Unavailable(e);
		}
	}

	@Override
	public List<String> columns(String schema, String table) {
		try {
			return provider.describe(schema, table).map(d -> d.columns().stream().map(SchemaProvider.ColumnInfo::name).toList())
				.orElse(List.of());
		}
		catch (SQLException e) {
			throw new SchemaModel.Unavailable(e);
		}
	}
}
