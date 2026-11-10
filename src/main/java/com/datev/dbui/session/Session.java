package com.datev.dbui.session;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.datev.dbui.Result;

/** Owns one JDBC connection. Statement execution never runs on the input thread. */
public final class Session implements AutoCloseable {

	public record Update(List<MessageBlock> messageBlocks, boolean ended) {
		public Update {
			messageBlocks = List.copyOf(messageBlocks);
		}
	}

	private final Connection connection;
	private final ExecutorService executor = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("dbui-statement").factory());
	private final List<MessageBlock> messageBlocks = new ArrayList<>();
	private boolean ended;
	private boolean running;

	public Session(Connection connection) throws SQLException {
		this.connection = connection;
		connection.setAutoCommit(true);
	}

	public synchronized List<MessageBlock> messageBlocks() {
		return List.copyOf(messageBlocks);
	}

	/** Accepts the next input after the previous submission completes; type-ahead is not supported. */
	public synchronized CompletableFuture<Update> submit(String input) {
		if (ended) {
			return CompletableFuture.completedFuture(new Update(messageBlocks(), true));
		}
		if (running) {
			return CompletableFuture.failedFuture(new IllegalStateException("A Statement is already running"));
		}
		if (input.isBlank()) {
			return CompletableFuture.completedFuture(new Update(messageBlocks(), false));
		}
		messageBlocks.add(MessageBlock.input(input));
		if (input.strip().equals("/quit")) {
			ended = true;
			return CompletableFuture.completedFuture(new Update(messageBlocks(), true));
		}
		String stripped = input.stripLeading();
		if (stripped.startsWith("/") && !stripped.startsWith("/*")) {
			messageBlocks.add(MessageBlock.error("Unknown Slash command: " + input.strip()));
			return CompletableFuture.completedFuture(new Update(messageBlocks(), false));
		}
		running = true;
		return CompletableFuture.supplyAsync(() -> {
			try {
				return execute(input);
			}
			finally {
				finished();
			}
		}, executor);
	}

	private synchronized void finished() {
		running = false;
	}

	private Update execute(String input) {
		try (var statement = connection.createStatement()) {
			if (statement.execute(input)) {
				try (var rows = statement.getResultSet()) {
					add(MessageBlock.result(read(rows)));
				}
			}
			return new Update(messageBlocks(), false);
		}
		catch (SQLException e) {
			add(MessageBlock.error(e.getMessage()));
			return new Update(messageBlocks(), false);
		}
	}

	private synchronized void add(MessageBlock block) {
		messageBlocks.add(block);
	}

	private static Result read(ResultSet rows) throws SQLException {
		var metadata = rows.getMetaData();
		int count = metadata.getColumnCount();
		List<String> columns = new ArrayList<>();
		for (int column = 1; column <= count; column++) {
			columns.add(metadata.getColumnLabel(column));
		}
		List<List<Object>> values = new ArrayList<>();
		while (rows.next()) {
			List<Object> row = new ArrayList<>();
			for (int column = 1; column <= count; column++) {
				row.add(rows.getObject(column));
			}
			values.add(row);
		}
		return new Result(columns, values);
	}

	@Override
	public void close() throws SQLException {
		executor.close();
		connection.close();
	}
}
