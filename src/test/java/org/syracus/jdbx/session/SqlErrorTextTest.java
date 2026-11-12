package com.datev.dbui.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

class SqlErrorTextTest {

	@Test
	void serverErrorShowsMessageSqlStateAndPosition() {
		var server = new ServerErrorMessage("SERROR\0C42703\0Mcolumn \"nme\" does not exist\0P8\0\0");
		assertThat(SqlErrorText.of(new PSQLException(server)))
			.isEqualTo("ERROR: column \"nme\" does not exist (42703), position 8");
	}

	@Test
	void serverErrorWithoutPositionOmitsIt() {
		var server = new ServerErrorMessage("SERROR\0C42P01\0Mrelation \"nope\" does not exist\0\0");
		assertThat(SqlErrorText.of(new PSQLException(server)))
			.isEqualTo("ERROR: relation \"nope\" does not exist (42P01)");
	}

	@Test
	void otherSqlExceptionsShowMessageAndSqlStateWhenKnown() {
		assertThat(SqlErrorText.of(new SQLException("connection lost", "08006"))).isEqualTo("connection lost (08006)");
		assertThat(SqlErrorText.of(new SQLException("boom"))).isEqualTo("boom");
	}
}
