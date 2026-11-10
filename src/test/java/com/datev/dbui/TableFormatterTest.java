package com.datev.dbui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class TableFormatterTest {

	@Test
	void formatsHeaderSeparatorAndAlignedRows() {
		Result result = new Result(List.of("id", "name"),
				List.of(List.of(1, "alice"), List.of(22, "bob")));

		assertThat(new TableFormatter().format(result)).isEqualTo("""
				id | name
				---+------
				1  | alice
				22 | bob
				""");
	}

	@Test
	void showsNullAsNULL() {
		Result result = new Result(List.of("a"), List.<List<Object>>of(Arrays.asList((Object) null)));

		assertThat(new TableFormatter().format(result)).isEqualTo("""
				a
				----
				NULL
				""");
	}

	@Test
	void formatsResultWithoutRowsAsHeaderAndSeparator() {
		assertThat(new TableFormatter().format(new Result(List.of("x"), List.of()))).isEqualTo("""
				x
				-
				""");
	}
}
