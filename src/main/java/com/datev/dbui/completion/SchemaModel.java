package com.datev.dbui.completion;

import java.util.List;

/** The schema data Completion reads. Implementations answer from a cache; they must not run per key press queries. */
public interface SchemaModel {

	/** No schema known: only keywords complete. */
	SchemaModel EMPTY = new SchemaModel() {
		@Override
		public List<String> schemas() {
			return List.of();
		}

		@Override
		public String defaultSchema() {
			return null;
		}

		@Override
		public List<Table> tables(String schema) {
			return List.of();
		}

		@Override
		public List<String> columns(String schema, String table) {
			return List.of();
		}
	};

	record Table(String name, boolean view) {
	}

	/** The schema cannot be read, for example because the connection failed. */
	final class Unavailable extends RuntimeException {

		public Unavailable(Throwable cause) {
			super(cause.getMessage(), cause);
		}
	}

	/** Schema names without system schemas. */
	List<String> schemas();

	/** The schema that unqualified table names refer to; null if unknown. */
	String defaultSchema();

	List<Table> tables(String schema);

	/** Column names in table order; empty when the table does not exist. */
	List<String> columns(String schema, String table);
}
