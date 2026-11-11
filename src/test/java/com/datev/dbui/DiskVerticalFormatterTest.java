package com.datev.dbui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DiskVerticalFormatterTest {

	@TempDir Path directory;

	@ParameterizedTest
	@EnumSource(value = Format.class, names = { "TABLE", "MARKDOWN", "CSV", "TSV" })
	void diskTransposePreservesValuesAndCleansUpItsSpools(Format format) throws Exception {
		var result = new Result(List.of("id", "name", "flag"), List.of(
			Arrays.asList(7, "a|b\nc", null), List.of(22, "\"q\",x", true)));
		String expected = switch (format) {
			case TABLE -> "id   | 7     | 22\nname | a|b⏎c | \"q\",x\nflag | NULL  | true\n";
			case MARKDOWN -> "| Column | Row 1 | Row 2 |\n| --- | --- | --- |\n| id | 7 | 22 |\n| name | a\\|b<br>c | \"q\",x |\n| flag | NULL | true |\n";
			case CSV -> "id,7,22\nname,\"a|b\nc\",\"\"\"q\"\",x\"\nflag,,true\n";
			case TSV -> "id\t7\t22\nname\ta|b\\nc\t\"q\",x\nflag\t\ttrue\n";
			default -> throw new AssertionError(format);
		};
		assertThat(new DiskVerticalFormatter(format, FormatOptions.defaults(), directory).format(result)).isEqualTo(expected);
		try (var files = Files.list(directory)) { assertThat(files).isEmpty(); }
	}

	@Test
	void columnAndWidthSpoolFilesArePrivateWhileTheyExist() throws Exception {
		assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"));
		var source = new RowSource() {
			@Override public List<String> columns() { return List.of("id", "name"); }
			@Override public java.util.Iterator<List<Object>> rows() {
				try (var directories = Files.list(directory)) {
					var spools = directories.toList();
					assertThat(spools).hasSize(1);
					try (var files = Files.list(spools.getFirst())) {
						var paths = files.toList();
						assertThat(paths).hasSize(3);
						for (Path path : paths) assertThat(Files.getPosixFilePermissions(path))
							.as("spool %s", path).isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
					}
				}
				catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
				return List.<List<Object>>of(List.of(7, "A")).iterator();
			}
		};
		var output = new java.io.StringWriter();
		new DiskVerticalFormatter(Format.CSV, FormatOptions.defaults(), directory).write(source, output);
		assertThat(output.toString()).isEqualTo("id,7\nname,A\n");
		try (var files = Files.list(directory)) { assertThat(files).isEmpty(); }
	}

	@ParameterizedTest
	@EnumSource(value = Format.class, names = { "TABLE", "MARKDOWN", "CSV", "TSV" })
	void emptyDiskTransposeKeepsColumnNames(Format format) {
		var result = new Result(List.of("id", "name"), List.of());
		String expected = format == Format.MARKDOWN
			? "| Column |\n| --- |\n| id |\n| name |\n" : "id\nname\n";
		assertThat(new DiskVerticalFormatter(format, FormatOptions.defaults(), directory).format(result)).isEqualTo(expected);
	}
}
