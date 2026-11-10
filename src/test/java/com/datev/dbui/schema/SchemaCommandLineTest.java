package com.datev.dbui.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SchemaCommandLineTest {

	@Test
	void recognisesTheSchemaCommandsWithTheirArgument() {
		assertThat(SchemaCommandLine.parse(" /tables  sales ")).contains(new SchemaCommandLine("tables", "sales"));
		assertThat(SchemaCommandLine.parse("/describe \"My Table\"")).contains(new SchemaCommandLine("describe", "\"My Table\""));
		assertThat(SchemaCommandLine.parse("/schema")).contains(new SchemaCommandLine("schema", ""));
		assertThat(SchemaCommandLine.parse("/refresh")).contains(new SchemaCommandLine("refresh", ""));
	}

	@Test
	void ignoresOtherInput() {
		assertThat(SchemaCommandLine.parse("/quit")).isEmpty();
		assertThat(SchemaCommandLine.parse("/tablesx")).isEmpty();
		assertThat(SchemaCommandLine.parse("select 1")).isEmpty();
		assertThat(SchemaCommandLine.parse("/* c */ select 1")).isEmpty();
	}
}
