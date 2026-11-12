package com.datev.dbui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Row source seam: formatters write rows while they read them. */
class RowSourceFormatterTest {

	private static final FormatOptions UTC = FormatOptions.defaults().withTimeZone(ZoneOffset.UTC);

	private static List<List<Object>> smallRows() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(Arrays.asList(1, "alice", null));
		rows.add(Arrays.asList(22, "bo|b", true));
		return rows;
	}

	private static String stream(Format format, Orientation orientation, List<String> columns, List<List<Object>> rows)
			throws IOException {
		StringWriter out = new StringWriter();
		format.formatter(UTC, orientation).write(RowSource.of(columns, rows.iterator()), out);
		return out.toString();
	}

	@Test
	void horizontalFormatsWriteTheSameLinesFromARowSource() throws IOException {
		List<String> columns = List.of("id", "name", "flag");

		assertThat(stream(Format.TABLE, Orientation.HORIZONTAL, columns, smallRows())).isEqualTo("""
				id | name  | flag
				---+-------+-----
				1  | alice | NULL
				22 | bo|b  | true
				""");
		assertThat(stream(Format.MARKDOWN, Orientation.HORIZONTAL, columns, smallRows())).isEqualTo("""
				| id | name | flag |
				| --- | --- | --- |
				| 1 | alice | NULL |
				| 22 | bo\\|b | true |
				""");
		assertThat(stream(Format.CSV, Orientation.HORIZONTAL, columns, smallRows())).isEqualTo("""
				id,name,flag
				1,alice,
				22,bo|b,true
				""");
		assertThat(stream(Format.TSV, Orientation.HORIZONTAL, columns, smallRows())).isEqualTo("""
				id\tname\tflag
				1\talice\t
				22\tbo|b\ttrue
				""");
		assertThat(stream(Format.JSON, Orientation.HORIZONTAL, columns, smallRows())).isEqualTo("""
				[
				{"id":1,"name":"alice","flag":null},
				{"id":22,"name":"bo|b","flag":true}
				]
				""");
	}

	@Test
	void verticalFormatsWriteTheSameLinesFromARowSource() throws IOException {
		List<String> columns = List.of("id", "name", "flag");

		assertThat(stream(Format.TABLE, Orientation.VERTICAL, columns, smallRows())).isEqualTo("""
				id   | 1     | 22
				name | alice | bo|b
				flag | NULL  | true
				""");
		assertThat(stream(Format.CSV, Orientation.VERTICAL, columns, smallRows())).isEqualTo("""
				id,1,22
				name,alice,bo|b
				flag,,true
				""");
	}

	@Test
	void emptyRowSourceWritesOnlyTheHeader() throws IOException {
		assertThat(stream(Format.CSV, Orientation.HORIZONTAL, List.of("a"), List.of())).isEqualTo("a\n");
		assertThat(stream(Format.JSON, Orientation.HORIZONTAL, List.of("a"), List.of())).isEqualTo("[]\n");
	}

	/** Pulls rows one by one and records how many were pulled when output arrives. */
	private static final class Probe implements Iterator<List<Object>> {
		int pulled;
		final int total;

		Probe(int total) {
			this.total = total;
		}

		@Override
		public boolean hasNext() {
			return pulled < total;
		}

		@Override
		public List<Object> next() {
			pulled++;
			return Arrays.asList((Object) pulled);
		}
	}

	private static int pulledAtFirstDataLine(Format format, int total) throws IOException {
		Probe probe = new Probe(total);
		int[] pulledAtFirstRow = { -1 };
		Writer out = new Writer() {
			int lines;

			@Override
			public void write(char[] buffer, int offset, int length) {
				for (int i = offset; i < offset + length; i++) {
					if (buffer[i] == '\n' && ++lines == 2 && pulledAtFirstRow[0] < 0) {
						pulledAtFirstRow[0] = probe.pulled;
					}
				}
			}

			@Override
			public void flush() {
			}

			@Override
			public void close() {
			}
		};
		format.formatter(UTC).write(RowSource.of(List.of("n"), probe), out);
		return pulledAtFirstRow[0];
	}

	@Test
	void csvTsvJsonAndMarkdownWriteRowsBeforeTheSourceIsRead() throws IOException {
		for (Format format : List.of(Format.CSV, Format.TSV, Format.JSON, Format.MARKDOWN)) {
			// the second line is the separator for markdown and the first row for the others
			assertThat(pulledAtFirstDataLine(format, 100_000)).as(format.id()).isLessThanOrEqualTo(1);
		}
	}

	@Test
	void tableTakesWidthsFromTheFirst1000RowsAndLetsLaterValuesOverflow() throws IOException {
		List<List<Object>> rows = new ArrayList<>();
		for (int i = 0; i < TableFormatter.SAMPLE_ROWS; i++) {
			rows.add(Arrays.asList("ab", "x"));
		}
		rows.add(Arrays.asList("abcdef", "y"));

		String[] lines = stream(Format.TABLE, Orientation.HORIZONTAL, List.of("c1", "c2"), rows).split("\n");

		assertThat(lines[0]).isEqualTo("c1 | c2");
		assertThat(lines[1]).isEqualTo("---+---");
		assertThat(lines[2]).isEqualTo("ab | x");
		assertThat(lines[lines.length - 1]).isEqualTo("abcdef | y");
	}

	@Test
	void tableWithFewerRowsThanTheSampleStillFitsAllWidths() throws IOException {
		List<List<Object>> rows = List.of(Arrays.asList("ab"), Arrays.asList("abcdef"));

		assertThat(stream(Format.TABLE, Orientation.HORIZONTAL, List.of("c"), rows)).isEqualTo("""
				c
				------
				ab
				abcdef
				""");
	}
}
