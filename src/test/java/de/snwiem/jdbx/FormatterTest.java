package de.snwiem.jdbx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Result formatter seam: every Format and the value rendering rules. */
class FormatterTest {

	private static final FormatOptions UTC = FormatOptions.defaults().withTimeZone(ZoneOffset.UTC);

	private static Result one(Object value) {
		return new Result(List.of("v"), List.<List<Object>>of(Arrays.asList(value)));
	}

	private static String render(Format format, Result result) {
		return format.formatter(UTC).format(result);
	}

	private static Result mixed() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(Arrays.asList(1, "alice", null));
		rows.add(Arrays.asList(22, "bob", true));
		return new Result(List.of("id", "name", "flag"), rows);
	}

	// table

	@Test
	void tableCutsLongTextWithEllipsisAtMaxWidth() {
		String text = "x".repeat(70);

		assertThat(render(Format.TABLE, one(text))).contains("\n" + "x".repeat(59) + "…\n");
	}

	@Test
	void tableDoesNotCutTextOfExactlyMaxWidth() {
		String text = "x".repeat(60);

		assertThat(render(Format.TABLE, one(text))).contains("\n" + text + "\n");
	}

	@Test
	void tableShowsNewlineMarker() {
		assertThat(render(Format.TABLE, one("a\nb\r\nc"))).contains("a⏎b⏎c");
	}

	@Test
	void tableShowsBinaryAsHexWithLength() {
		assertThat(render(Format.TABLE, one(new byte[] { 0x0a, (byte) 0xff }))).contains("\\x0aff (2 bytes)");
	}

	@Test
	void tableCutsLongBinaryButKeepsLength() {
		String table = render(Format.TABLE, one(new byte[100]));

		assertThat(table).contains("\\x0000").contains("… (100 bytes)");
		assertThat(table).doesNotContain("0".repeat(100));
	}

	@Test
	void optionsChangeMarkersAndWidth() {
		FormatOptions options = UTC.withNullMarker("<null>").withMultiLineMarker("~").withMaxWidth(5);
		Result result = new Result(List.of("a", "b"), List.<List<Object>>of(Arrays.asList(null, "l1\nl2 and more")));

		assertThat(Format.TABLE.formatter(options).format(result)).isEqualTo("""
				a      | b
				-------+------
				<null> | l1~l…
				""");
	}

	// markdown

	@Test
	void markdownWritesPipeTable() {
		assertThat(render(Format.MARKDOWN, mixed())).isEqualTo("""
				| id | name | flag |
				| --- | --- | --- |
				| 1 | alice | NULL |
				| 22 | bob | true |
				""");
	}

	@Test
	void markdownEscapesPipesAndKeepsRowsOnOneLine() {
		assertThat(render(Format.MARKDOWN, one("a|b\nc"))).isEqualTo("""
				| v |
				| --- |
				| a\\|b<br>c |
				""");
	}

	@Test
	void markdownEscapesPipesInColumnNamesAndDoesNotCutText() {
		Result result = new Result(List.of("a|b"), List.<List<Object>>of(List.of("y".repeat(80))));

		assertThat(render(Format.MARKDOWN, result)).contains("| a\\|b |").contains("y".repeat(80));
	}

	// csv

	@Test
	void csvWritesHeaderAndRowsWithEmptyNull() {
		assertThat(render(Format.CSV, mixed())).isEqualTo("""
				id,name,flag
				1,alice,
				22,bob,true
				""");
	}

	@Test
	void csvQuotesCommasQuotesAndNewlines() {
		Result result = new Result(List.of("a,b"), List.<List<Object>>of(List.of("x,y"), List.of("say \"hi\""),
				List.of("l1\nl2")));

		assertThat(render(Format.CSV, result)).isEqualTo("\"a,b\"\n\"x,y\"\n\"say \"\"hi\"\"\"\n\"l1\nl2\"\n");
	}

	@Test
	void csvNeverCutsDataAndWritesFullHex() {
		String text = "x".repeat(100);

		assertThat(render(Format.CSV, one(text))).isEqualTo("v\n" + text + "\n");
		assertThat(render(Format.CSV, one(new byte[] { 1, 2, 3 }))).isEqualTo("v\n\\x010203\n");
		assertThat(render(Format.CSV, one(new byte[200]))).contains("0".repeat(400));
	}

	// tsv

	@Test
	void tsvWritesTabSeparatedWithEmptyNull() {
		assertThat(render(Format.TSV, mixed())).isEqualTo("id\tname\tflag\n1\talice\t\n22\tbob\ttrue\n");
	}

	@Test
	void tsvEscapesTabsAndNewlines() {
		assertThat(render(Format.TSV, one("a\tb\nc\r\nd"))).isEqualTo("v\na\\tb\\nc\\r\\nd\n");
	}

	@Test
	void tsvNeverCutsData() {
		assertThat(render(Format.TSV, one("x".repeat(100)))).contains("x".repeat(100));
	}

	// json

	@Test
	void jsonWritesOneObjectPerRowWithTypedValues() {
		assertThat(render(Format.JSON, mixed())).isEqualTo("""
				[
				{"id":1,"name":"alice","flag":null},
				{"id":22,"name":"bob","flag":true}
				]
				""");
	}

	@Test
	void jsonWritesEmptyResultAsEmptyArray() {
		assertThat(render(Format.JSON, new Result(List.of("x"), List.of()))).isEqualTo("[]\n");
	}

	@Test
	void jsonEscapesStrings() {
		assertThat(render(Format.JSON, one("q\"b\\n\nt\tc\u0001é"))).contains("{\"v\":\"q\\\"b\\\\n\\nt\\tc\\u0001é\"}");
	}

	@Test
	void jsonWritesNumbersPlainAndNeverCuts() {
		Result result = new Result(List.of("a", "b", "c"), List.<List<Object>>of(
				List.of(new java.math.BigDecimal("1E+3"), 1.5d, "x".repeat(100))));

		assertThat(render(Format.JSON, result)).contains("{\"a\":1000,\"b\":1.5,\"c\":\"" + "x".repeat(100) + "\"}");
	}

	@Test
	void jsonWritesNonFiniteNumbersAsStrings() {
		assertThat(render(Format.JSON, one(Double.NaN))).contains("{\"v\":\"NaN\"}");
	}

	@Test
	void jsonWritesBinaryAsFullHexString() {
		assertThat(render(Format.JSON, one(new byte[] { 0x0a, (byte) 0xff }))).contains("{\"v\":\"\\\\x0aff\"}");
	}

	// json-pretty

	@Test
	void jsonPrettyWritesEachKeyOnItsOwnLine() {
		assertThat(render(Format.JSON_PRETTY, mixed())).isEqualTo("""
				[
				  {
				    "id": 1,
				    "name": "alice",
				    "flag": null
				  },
				  {
				    "id": 22,
				    "name": "bob",
				    "flag": true
				  }
				]
				""");
	}

	@Test
	void jsonPrettyParsesAsTheSameDataAsJson() throws Exception {
		var pretty = new org.json.JSONArray(render(Format.JSON_PRETTY, mixed()));
		var compact = new org.json.JSONArray(render(Format.JSON, mixed()));
		assertThat(pretty.toString()).isEqualTo(compact.toString());
	}

	@Test
	void jsonPrettyWritesEmptyResultAsEmptyArray() {
		assertThat(render(Format.JSON_PRETTY, new Result(List.of("x"), List.of()))).isEqualTo("[]\n");
	}

	@Test
	void jsonPrettyRendersValuesLikeJson() {
		Result result = new Result(List.of("s", "n", "b", "z", "d", "j"), List.<List<Object>>of(
				Arrays.asList("q\"b\\n\n\u0001", new java.math.BigDecimal("1E+3"), true, null, Double.NaN,
						new JsonValue("{\"k\": [1, 2]}"))));

		assertThat(render(Format.JSON_PRETTY, result)).isEqualTo("""
				[
				  {
				    "s": "q\\"b\\\\n\\n\\u0001",
				    "n": 1000,
				    "b": true,
				    "z": null,
				    "d": "NaN",
				    "j": {"k": [1, 2]}
				  }
				]
				""");
	}

	@Test
	void jsonPrettyIdUsesAHyphenAndOnlyTheHyphenFormIsParsed() {
		assertThat(Format.JSON_PRETTY.id()).isEqualTo("json-pretty");
		assertThat(Format.parse("json-pretty")).isEqualTo(Format.JSON_PRETTY);
		assertThat(Format.parse(" JSON-Pretty ")).isEqualTo(Format.JSON_PRETTY);
		assertThatThrownBy(() -> Format.parse("json_pretty")).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("table, markdown, csv, tsv, json, json-pretty");
	}

	@Test
	void jsonEmbedsJsonColumnsAsRealJson() {
		assertThat(render(Format.JSON, one(new JsonValue("{\"k\": [1, 2]}")))).contains("{\"v\":{\"k\": [1, 2]}}");
	}

	// dates (all Formats share the rule)

	@Test
	void datesAreIso8601InConfiguredTimeZone() {
		Instant instant = Instant.parse("2024-03-05T10:15:30Z");
		FormatOptions berlin = UTC.withTimeZone(ZoneId.of("Europe/Berlin"));

		assertThat(Format.CSV.formatter(berlin).format(one(Timestamp.from(instant)))).isEqualTo("v\n2024-03-05T11:15:30+01:00\n");
		assertThat(Format.CSV.formatter(berlin).format(one(OffsetDateTime.parse("2024-03-05T10:15:30Z"))))
				.isEqualTo("v\n2024-03-05T11:15:30+01:00\n");
		assertThat(render(Format.JSON, one(instant))).contains("{\"v\":\"2024-03-05T10:15:30Z\"}");
		assertThat(render(Format.TABLE, one(LocalDate.of(2024, 3, 5)))).contains("2024-03-05");
	}

	@Test
	void dateTimeAndPlainTimestampAreShownAsStoredWhateverTheTimeZone() {
		FormatOptions tokyo = UTC.withTimeZone(ZoneId.of("Asia/Tokyo"));

		assertThat(Format.CSV.formatter(tokyo).format(one(LocalDate.of(2024, 3, 5)))).isEqualTo("v\n2024-03-05\n");
		assertThat(Format.CSV.formatter(tokyo).format(one(java.time.LocalTime.of(10, 15, 30))))
				.isEqualTo("v\n10:15:30\n");
		assertThat(Format.CSV.formatter(tokyo).format(one(java.time.LocalDateTime.of(2024, 3, 5, 23, 15, 30))))
				.isEqualTo("v\n2024-03-05T23:15:30\n");
	}

	@Test
	void timestampWithTimeZoneIsConvertedToConfiguredTimeZone() {
		FormatOptions tokyo = UTC.withTimeZone(ZoneId.of("Asia/Tokyo"));

		assertThat(Format.CSV.formatter(tokyo).format(one(OffsetDateTime.parse("2024-03-05T23:15:30Z"))))
				.isEqualTo("v\n2024-03-06T08:15:30+09:00\n");
	}

	@Test
	void datesUseJvmDefaultTimeZoneByDefault() {
		Instant instant = Instant.parse("2024-03-05T10:15:30Z");
		String expected = instant.atZone(ZoneId.systemDefault()).toOffsetDateTime()
				.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);

		assertThat(Format.CSV.formatter(FormatOptions.defaults()).format(one(instant))).isEqualTo("v\n" + expected + "\n");
	}

	@Test
	void parsesFormatNamesCaseInsensitively() {
		assertThat(Format.parse("CSV")).isEqualTo(Format.CSV);
		assertThat(Format.parse("markdown")).isEqualTo(Format.MARKDOWN);
	}
}
