package org.syracus.jdbx;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.syracus.jdbx.config.Settings;

/** Writes Results to a same-directory temporary file, publishing only a complete file. */
public final class ResultFiles {

	private ResultFiles() { }

	public static void save(Result result, Settings settings, Path target, BooleanSupplier cancelled) throws IOException, SQLException {
		atomic(target, cancelled, out -> Format.parse(settings.format())
			.formatter(FormatOptions.from(settings), Orientation.parse(settings.orientation())).write(result, out));
	}

	/** Validates the whole input before opening a file or executing SQL. */
	public static List<String> queries(String input) {
		var statements = new StatementSplitter().split(input);
		if (statements.isEmpty()) throw new IllegalArgumentException("No SQL input to dump. Supply SQL or submit an input first.");
		for (String sql : statements) {
			if (!queryOnly(sql)) {
				String preview = sql.length() > 60 ? sql.substring(0, 60) + "..." : sql;
				throw new IllegalArgumentException("Dump requires a query for every Statement. Refused Statement: " + preview + ". Nothing was run. "
					+ "Writes, INTO and locking queries are not accepted; quote identifiers named insert, update, delete or merge.");
			}
		}
		return statements;
	}

	private static boolean queryOnly(String sql) {
		var tokens = SqlTokens.scan(sql).stream().filter(token -> token.type() != SqlTokens.Type.COMMENT).toList();
		int first = 0;
		while (first < tokens.size() && tokens.get(first).text(sql).equals("(")) first++;
		if (first == tokens.size() || tokens.get(first).type() != SqlTokens.Type.WORD
				|| !java.util.Set.of("select", "with", "values", "table", "show")
					.contains(tokens.get(first).text(sql).toLowerCase(java.util.Locale.ROOT))) return false;
		var words = tokens.stream().map(token -> token.type() == SqlTokens.Type.WORD
			? token.text(sql).toLowerCase(java.util.Locale.ROOT) : "").toList();
		for (int i = 0; i < words.size(); i++) {
			if (java.util.Set.of("into", "insert", "update", "delete", "merge").contains(words.get(i))) return false;
			if (words.get(i).equals("for") && i + 1 < words.size()
					&& (words.get(i + 1).equals("share") || (words.get(i + 1).equals("key")
						&& i + 2 < words.size() && words.get(i + 2).equals("share")))) return false;
		}
		return true;
	}

	/** Streams all rows. The caller validates the whole input before opening a file or executing SQL. */
	public static long dump(Connection connection, Statement statement, List<String> statements, Settings settings,
			Path target, BooleanSupplier cancelled) throws IOException, SQLException {
		var pg = connection.unwrap(org.postgresql.core.BaseConnection.class);
		if (!pg.getStandardConformingStrings()) {
			throw new SQLException("Dump requires standard_conforming_strings = on. Nothing was run.");
		}
		// SQL BEGIN does not change JDBC's autocommit flag; ask pgjdbc before owning a read transaction.
		if (!connection.getAutoCommit() || pg.getTransactionState()
				!= org.postgresql.core.TransactionState.IDLE) {
			throw new SQLException("Finish the open transaction with COMMIT or ROLLBACK before /dump.");
		}
		long[] count = { 0 };
		Format format = Format.parse(settings.format());
		var options = FormatOptions.from(settings);
		var orientation = Orientation.parse(settings.orientation());
		ResultFormatter formatter = orientation == Orientation.VERTICAL && !format.isDocumentStream()
			? new DiskVerticalFormatter(format, options, target.toAbsolutePath().getParent()) : format.formatter(options, orientation);
		statement.setFetchSize(StreamingRead.FETCH_SIZE);
		atomic(target, cancelled, out -> {
			if (format.isDocumentStream()) out.append("[\n");
			boolean first = true;
			for (String sql : statements) {
				checkCancelled(cancelled);
				try {
					StreamingRead.begin(connection, settings.timeout());
					try (var guard = connection.createStatement()) { guard.execute("SET TRANSACTION READ ONLY"); }
					if (!statement.execute(sql)) throw new SQLException("Dump requires a query for every Statement.");
					try (var result = statement.getResultSet()) {
						if (!first) out.append(format.isDocumentStream() ? ",\n" : "\n");
						var source = new JdbcRowSource(result, null);
						var rows = source.rows();
						var counted = RowSource.of(source.columns(), new java.util.Iterator<>() {
							@Override public boolean hasNext() {
								if (cancelled.getAsBoolean()) throw new JdbcRowSource.RowReadException(new SQLException("Dump cancelled", "57014"));
								return rows.hasNext();
							}
							@Override public List<Object> next() { var row = rows.next(); count[0]++; return row; }
						});
						formatter.write(counted, format.isDocumentStream() ? new NestedJsonWriter(out) : out);
					}
					checkCancelled(cancelled);
					connection.rollback();
					first = false;
				}
				catch (JdbcRowSource.RowReadException e) { throw (SQLException) e.getCause(); }
				finally { StreamingRead.restoreAutoCommit(connection); }
			}
			if (format.isDocumentStream()) out.append("\n]\n");
		});
		return count[0];
	}

	/** Indents a Result array by two spaces and leaves its final newline to the outer document. */
	static final class NestedJsonWriter extends Writer {
		private final Writer out;
		private boolean start = true;
		private boolean newline;
		NestedJsonWriter(Writer out) { this.out = out; }
		@Override public void write(char[] chars, int offset, int length) throws IOException {
			for (int i = offset; i < offset + length; i++) {
				char c = chars[i];
				if (newline) { out.write('\n'); newline = false; start = true; }
				if (c == '\n') newline = true;
				else {
					if (start) { out.write("  "); start = false; }
					out.write(c);
				}
			}
		}
		@Override public void flush() throws IOException { out.flush(); }
		@Override public void close() { }
	}

	@FunctionalInterface
	private interface Write {
		void write(Writer out) throws IOException, SQLException;
	}

	private static void atomic(Path target, BooleanSupplier cancelled, Write write) throws IOException, SQLException {
		Path absolute = target.toAbsolutePath();
		try {
			if (Files.isDirectory(absolute, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
				throw new IOException("Target is a directory: " + absolute);
			}
			Path temporary = Files.createTempFile(absolute.getParent(), ".dbui-", ".tmp");
			try {
				try (Writer out = new java.io.FilterWriter(Files.newBufferedWriter(temporary)) {
					@Override public void write(int c) throws IOException { checkCancelled(cancelled); super.write(c); }
					@Override public void write(char[] chars, int offset, int length) throws IOException { checkCancelled(cancelled); super.write(chars, offset, length); }
					@Override public void write(String text, int offset, int length) throws IOException { checkCancelled(cancelled); super.write(text, offset, length); }
				}) {
					write.write(out);
				}
				try (var file = java.nio.channels.FileChannel.open(temporary, java.nio.file.StandardOpenOption.WRITE)) {
					file.force(true);
				}
				checkCancelled(cancelled);
				Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			}
			finally {
				Files.deleteIfExists(temporary);
			}
		}
		catch (IOException e) {
			throw new IOException("Cannot write target " + absolute + ": " + e.getMessage(), e);
		}
	}

	private static void checkCancelled(BooleanSupplier cancelled) throws IOException {
		if (cancelled.getAsBoolean()) throw new IOException("File writing cancelled.");
	}
}
