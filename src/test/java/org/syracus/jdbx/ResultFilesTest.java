package com.datev.dbui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.datev.dbui.config.Settings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ResultFilesTest {

	@TempDir Path directory;

	@ParameterizedTest(name = "{index}: accepted={1}: {0}")
	@MethodSource("queryInputs")
	void preflightUnderstandsQuotesAndCommentsAndRefusesWritesAndLocks(String input, boolean accepted, int count) {
		if (accepted) assertThat(ResultFiles.queries(input)).hasSize(count);
		else assertThatIllegalArgumentException().isThrownBy(() -> ResultFiles.queries(input));
	}

	static Stream<Arguments> queryInputs() {
		return Stream.of(
			Arguments.of("select 1", true, 1),
			Arguments.of("select 'delete; into' as v; select 2", true, 2),
			Arguments.of("select 'it''s update; merge'", true, 1),
			Arguments.of("select E'a\\\'b; delete'", true, 1),
			Arguments.of("select $$insert; update$$", true, 1),
			Arguments.of("select $tag$delete; into$tag$", true, 1),
			Arguments.of("/* outer /* delete */ nested */ select 1", true, 1),
			Arguments.of("select 1 -- into; delete\n; select 2", true, 2),
			Arguments.of("select \"insert\", \"a\"\"update\" from \"delete\"", true, 1),
			Arguments.of("with x as (select 1) select * from x", true, 1),
			Arguments.of("(select 1) union (select 2)", true, 1),
			Arguments.of("values (1); table x; show work_mem", true, 3),
			Arguments.of("select 1; -- trailing comment", true, 1),
			Arguments.of("select 1; insert into x values(1)", false, 0),
			Arguments.of("with x as (select 1) insert into x values(1) returning id", false, 0),
			Arguments.of("with x as (delete from t returning id) select * from x", false, 0),
			Arguments.of("select * from x for update", false, 0),
			Arguments.of("select * from x for no key update", false, 0),
			Arguments.of("select * from x for /* lock */ share", false, 0),
			Arguments.of("select * from x for key share", false, 0),
			Arguments.of("select * into x from t", false, 0),
			Arguments.of("explain analyze delete from x", false, 0),
			Arguments.of("-- only a comment", false, 0)
		);
	}

	@Test
	void preflightNamesTheFirstRefusedStatementWithAtMostSixtyCharacters() {
		assertThatIllegalArgumentException().isThrownBy(() -> ResultFiles.queries("select 1; delete " + "x".repeat(100)))
			.withMessageContaining("Refused Statement: delete " + "x".repeat(53) + "...")
			.withMessageContaining("Nothing was run").withMessageNotContaining("x".repeat(54));
		assertThatIllegalArgumentException().isThrownBy(() -> ResultFiles.queries("select 1; delete from t; update t set id=1"))
			.withMessageContaining("Refused Statement: delete from t.").withMessageNotContaining("update t set id=1");
	}

	@Test
	void nestedJsonWriterIndentsAcrossChunksAndLeavesTheFinalNewlineToTheDocument() throws Exception {
		var output = new StringWriter();
		try (var nested = new ResultFiles.NestedJsonWriter(output)) {
			nested.write("[\n");
			nested.write("  {\"v\":\"a\\nb\"}");
			nested.write("\n]\n");
			nested.flush();
			assertThat(output.toString()).isEqualTo("  [\n    {\"v\":\"a\\nb\"}\n  ]");
		}
		output.append(",\n");
		try (var nested = new ResultFiles.NestedJsonWriter(output)) { nested.write("[]\n"); }
		assertThat(output.toString()).endsWith("],\n  []");
	}

	@Test
	void fileErrorsNameTheRequestedTargetNotJustATemporaryFile() {
		Path target = directory.resolve("missing").resolve("wanted.csv");
		var result = new Result(List.of("id"), List.of(List.of(7)));
		assertThatThrownBy(() -> ResultFiles.save(result, Settings.defaults(), target, () -> false))
			.isInstanceOf(IOException.class).hasMessageContaining(target.toString());
	}
}
