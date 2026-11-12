package de.snwiem.jdbx.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CommandTagTest {

	@Test
	void insertShowsOidZeroAndCount() {
		assertThat(CommandTag.of("insert into t values (1),(2),(3)", 3)).isEqualTo("INSERT 0 3");
	}

	@Test
	void updateAndDeleteShowTheCount() {
		assertThat(CommandTag.of("update t set i = 1", 2)).isEqualTo("UPDATE 2");
		assertThat(CommandTag.of("DELETE from t", 0)).isEqualTo("DELETE 0");
	}

	@Test
	void ddlShowsTheFirstTwoKeywordsWithoutCount() {
		assertThat(CommandTag.of("create table t(i int)", 0)).isEqualTo("CREATE TABLE");
		assertThat(CommandTag.of("  drop   index  i;", 0)).isEqualTo("DROP INDEX");
	}

	@Test
	void ddlSkipsModifiersLikeTemporaryAndUnique() {
		assertThat(CommandTag.of("create temporary table t(i int)", 0)).isEqualTo("CREATE TABLE");
		assertThat(CommandTag.of("create or replace view v as select 1", 0)).isEqualTo("CREATE VIEW");
		assertThat(CommandTag.of("create unique index i on t(i)", 0)).isEqualTo("CREATE INDEX");
	}

	@Test
	void otherStatementsShowTheFirstKeyword() {
		assertThat(CommandTag.of("truncate t", 0)).isEqualTo("TRUNCATE");
		assertThat(CommandTag.of("/* c */ -- x\nset search_path = a;", 0)).isEqualTo("SET");
	}
}
