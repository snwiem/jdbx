package com.datev.dbui;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Rules for reading a Result in a stream. PostgreSQL streams rows with a cursor only when the fetch size is set and
 * autocommit is off. Autocommit stays on for other Statements, because some (for example {@code vacuum}) cannot run
 * in a transaction block.
 */
final class StreamingRead {

	/** Rows per round trip to the server. */
	static final int FETCH_SIZE = 1000;

	private static final Pattern QUERY = Pattern.compile("^\\s*(select|with|values|table|show)\\b",
			Pattern.CASE_INSENSITIVE);

	private StreamingRead() {
	}

	/** True when the Statement starts like a query, so reading it with autocommit off is safe. */
	static boolean isQuery(String statement) {
		return QUERY.matcher(statement.toLowerCase(Locale.ROOT)).find();
	}

	/** pgjdbc's query timer does not cover later cursor fetches; PostgreSQL must also time each fetch. */
	static void begin(Connection connection, Duration timeout) throws SQLException {
		connection.setAutoCommit(false);
		if (timeout != null && !timeout.isZero()) {
			// PostgreSQL accepts at most this many milliseconds. The setting ends with the read transaction.
			long millis = Math.min(timeout.toMillis(), Integer.MAX_VALUE);
			try (var statement = connection.createStatement()) {
				try (var current = statement.executeQuery("SELECT setting FROM pg_catalog.pg_settings WHERE name = 'statement_timeout'")) {
					if (current.next() && current.getLong(1) > 0) {
						millis = Math.min(millis, current.getLong(1));
					}
				}
				statement.execute("SET LOCAL statement_timeout = '" + millis + "ms'");
			}
		}
	}

	/** Ends the read transaction (no-op after a commit) and turns autocommit on again. */
	static void restoreAutoCommit(Connection connection) throws SQLException {
		try {
			connection.rollback();
		}
		catch (SQLException ignored) {
			// the connection may be broken; setAutoCommit below reports it if it matters
		}
		connection.setAutoCommit(true);
	}
}
