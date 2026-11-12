package org.syracus.jdbx;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Result formatter seam: vertical Orientation (one line per column, column name first). */
class VerticalFormatterTest {

	private static final FormatOptions UTC = FormatOptions.defaults().withTimeZone(ZoneOffset.UTC);

	private static String vertical(Format format, Result result) {
		return format.formatter(UTC, Orientation.VERTICAL).format(result);
	}

	private static Result mixed() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(Arrays.asList(1, "alice", null));
		rows.add(Arrays.asList(22, "bob", true));
		return new Result(List.of("id", "name", "flag"), rows);
	}

	private static Result oneRow() {
		return new Result(List.of("id", "name"), List.<List<Object>>of(Arrays.asList(1, "alice")));
	}

	private static Result empty() {
		return new Result(List.of("id", "name"), List.of());
	}

	private static Result one(Object value) {
		return new Result(List.of("v"), List.<List<Object>>of(Arrays.asList(value)));
	}

	// table

	@Test
	void tableVerticalAlignsNamesLeftAndValuesPerRow() {
		assertThat(vertical(Format.TABLE, mixed())).isEqualTo("""
				id   | 1     | 22
				name | alice | bob
				flag | NULL  | true
				""");
	}

	@Test
	void tableVerticalOneRow() {
		assertThat(vertical(Format.TABLE, oneRow())).isEqualTo("""
				id   | 1
				name | alice
				""");
	}

	@Test
	void tableVerticalEmptyResultShowsOnlyColumnNames() {
		assertThat(vertical(Format.TABLE, empty())).isEqualTo("id\nname\n");
	}

	@Test
	void tableVerticalCutsLongTextAndMarksNewlines() {
		assertThat(vertical(Format.TABLE, one("x".repeat(70)))).isEqualTo("v | " + "x".repeat(59) + "…\n");
		assertThat(vertical(Format.TABLE, one("a\nb"))).isEqualTo("v | a⏎b\n");
	}

	// markdown

	@Test
	void markdownVerticalHasColumnAndRowHeader() {
		assertThat(vertical(Format.MARKDOWN, mixed())).isEqualTo("""
				| Column | Row 1 | Row 2 |
				| --- | --- | --- |
				| id | 1 | 22 |
				| name | alice | bob |
				| flag | NULL | true |
				""");
	}

	@Test
	void markdownVerticalOneRow() {
		assertThat(vertical(Format.MARKDOWN, oneRow())).isEqualTo("""
				| Column | Row 1 |
				| --- | --- |
				| id | 1 |
				| name | alice |
				""");
	}

	@Test
	void markdownVerticalEmptyResultHasOnlyColumnHeader() {
		assertThat(vertical(Format.MARKDOWN, empty())).isEqualTo("""
				| Column |
				| --- |
				| id |
				| name |
				""");
	}

	@Test
	void markdownVerticalEscapesPipesAndNewlines() {
		assertThat(vertical(Format.MARKDOWN, one("a|b\nc"))).isEqualTo("""
				| Column | Row 1 |
				| --- | --- |
				| v | a\\|b<br>c |
				""");
	}

	// csv

	@Test
	void csvVerticalWritesOneRecordPerColumn() {
		assertThat(vertical(Format.CSV, mixed())).isEqualTo("id,1,22\nname,alice,bob\nflag,,true\n");
	}

	@Test
	void csvVerticalOneRowAndEmpty() {
		assertThat(vertical(Format.CSV, oneRow())).isEqualTo("id,1\nname,alice\n");
		assertThat(vertical(Format.CSV, empty())).isEqualTo("id\nname\n");
	}

	@Test
	void csvVerticalQuotesAndKeepsLongMultiLineText() {
		String text = "x,\"y\"\nz" + "w".repeat(100);

		assertThat(vertical(Format.CSV, one(text))).isEqualTo("v,\"x,\"\"y\"\"\nz" + "w".repeat(100) + "\"\n");
	}

	// tsv

	@Test
	void tsvVerticalWritesOneRecordPerColumn() {
		assertThat(vertical(Format.TSV, mixed())).isEqualTo("id\t1\t22\nname\talice\tbob\nflag\t\ttrue\n");
	}

	@Test
	void tsvVerticalOneRowEmptyAndEscapes() {
		assertThat(vertical(Format.TSV, oneRow())).isEqualTo("id\t1\nname\talice\n");
		assertThat(vertical(Format.TSV, empty())).isEqualTo("id\nname\n");
		assertThat(vertical(Format.TSV, one("a\tb\nc"))).isEqualTo("v\ta\\tb\\nc\n");
	}

	// json

	@Test
	void jsonIsIdenticalForBothOrientations() {
		for (Result result : List.of(mixed(), oneRow(), empty())) {
			assertThat(vertical(Format.JSON, result))
					.isEqualTo(Format.JSON.formatter(UTC, Orientation.HORIZONTAL).format(result))
					.isEqualTo(Format.JSON.formatter(UTC).format(result));
		}
	}

	// parsing

	@Test
	void parsesOrientationCaseInsensitively() {
		assertThat(Orientation.parse(" Vertical ")).isEqualTo(Orientation.VERTICAL);
		assertThat(Orientation.parse("HORIZONTAL")).isEqualTo(Orientation.HORIZONTAL);
	}

	@org.junit.jupiter.api.Test
	void rejectsUnknownOrientation() {
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> Orientation.parse("diagonal"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("diagonal").hasMessageContaining("horizontal, vertical");
	}
}
