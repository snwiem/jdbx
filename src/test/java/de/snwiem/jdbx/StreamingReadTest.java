package de.snwiem.jdbx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StreamingReadTest {

	@ParameterizedTest
	@ValueSource(strings = { "select 1", "  \n\tWITH x as (select 1) select * from x", "values (1)", "table t", "show all",
			"-- export\nselect 1", "-- a\n-- b\n\n  select 1", "/* c */ with x as (select 1) select 1",
			"/* a */ -- b\n /* c */\n\tvalues (1)", "/* outer /* nested */ still comment */ table t",
			"--x\r\nshow all", "/**/select 1", "-- select\n/* ; */ select 1" })
	void isQueryForReadStatementsWithOptionalLeadingComments(String statement) {
		assertThat(StreamingRead.isQuery(statement)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "-- only a comment", "/* only */", "/* unterminated select 1", "-- c\nvacuum",
			"/* c */ vacuum", "/* /* nested */ select 1", "selection 1", "/* c */selection", "-- select 1",
			"'/* x */' select 1", "insert into t values (1)", "-- c\nwith_x" })
	void isNotQueryOtherwise(String statement) {
		assertThat(StreamingRead.isQuery(statement)).isFalse();
	}
}
