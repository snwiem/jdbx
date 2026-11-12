package de.snwiem.jdbx;

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
	void anInMemoryResultUsesAllRowsForColumnWidthsBeyondTheStreamingSample() {
		var rows = new java.util.ArrayList<List<Object>>();
		for (int i = 0; i < 1000; i++) {
			rows.add(List.of("x", "value"));
		}
		rows.add(List.of("widest last row", "value"));
		String formatted = new TableFormatter().format(new Result(List.of("a", "b"), rows));
		assertThat(formatted.lines().findFirst().orElseThrow()).isEqualTo("a               | b");
		assertThat(formatted.lines().skip(2)).allSatisfy(line -> assertThat(line.indexOf('|')).isEqualTo(16));
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
