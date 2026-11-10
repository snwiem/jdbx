package com.datev.dbui.session;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.datev.dbui.JdbcResultReader;

/** Owns one JDBC connection. Statement execution never runs on the input thread. */
public final class Session implements AutoCloseable {

	public record Update(List<MessageBlock> messageBlocks, boolean ended) {
		public Update {
			messageBlocks = List.copyOf(messageBlocks);
		}
	}

	private final Connection connection;
	private final SessionDirectory directory;
	private final ExecutorService executor = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("dbui-statement").factory());
	private final List<MessageBlock> messageBlocks = new ArrayList<>();
	private boolean ended;
	private boolean running;

	public Session(Connection connection, SessionDirectory directory) throws SQLException {
		this.connection = connection;
		this.directory = directory;
		connection.setAutoCommit(true);
	}

	/** The Session log file. Only this Session writes to it. */
	public Path logFile() {
		return directory.logFile();
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
					add(MessageBlock.result(JdbcResultReader.read(rows, null).result()));
				}
			}
			else {
				add(MessageBlock.info(CommandTag.of(input, statement.getLargeUpdateCount())));
			}
			return new Update(messageBlocks(), false);
		}
		catch (SQLException e) {
			add(MessageBlock.error(SqlErrorText.of(e)));
			return new Update(messageBlocks(), false);
		}
		catch (RuntimeException | Error e) {
			// Not a SQL error: the stack trace goes to the Session log, never to the terminal.
			add(MessageBlock.error("Unexpected error: " + e.getMessage() + ". Details: " + logFile()));
			writeLog(e);
			return new Update(messageBlocks(), false);
		}
	}

	private void writeLog(Throwable error) {
		var trace = new StringWriter();
		error.printStackTrace(new PrintWriter(trace));
		try {
			Files.writeString(logFile(), Instant.now() + " Unexpected error\n" + trace, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		}
		catch (IOException ignored) {
			// The log is best effort; the red band already tells the user.
		}
	}

	private synchronized void add(MessageBlock block) {
		messageBlocks.add(block);
	}

	@Override
	public void close() throws SQLException {
		executor.close();
		connection.close();
	}
}
