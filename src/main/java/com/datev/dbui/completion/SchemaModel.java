package com.datev.dbui.completion;

import java.util.List;

/** The schema data Completion reads. Implementations answer from a cache; they must not run per key press queries. */
public interface SchemaModel {

	record Table(String name, boolean view) {
	}

	/** Schema names without system schemas. */
	List<String> schemas();

	/** The schema that unqualified table names refer to; null if unknown. */
	String defaultSchema();

	List<Table> tables(String schema);

	/** Column names in table order; empty when the table does not exist. */
	List<String> columns(String schema, String table);
}
