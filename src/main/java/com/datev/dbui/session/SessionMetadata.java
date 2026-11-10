package com.datev.dbui.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * The content of {@code session.yaml}: start time, connection without password, the settings in effect, the last
 * Statement (shortened) and the id of the Session this one was forked from.
 */
public record SessionMetadata(Instant started, ConnectionInfo connection, int rowLimit, Long timeoutSeconds,
		boolean readOnly, String lastStatement, String resumedFrom) {

	static final int LAST_STATEMENT_LENGTH = 80;

	public SessionMetadata withLastStatement(String statement) {
		return new SessionMetadata(started, connection, rowLimit, timeoutSeconds, readOnly, shorten(statement), resumedFrom);
	}

	public SessionMetadata withConnection(ConnectionInfo info) {
		return new SessionMetadata(started, info, rowLimit, timeoutSeconds, readOnly, lastStatement, resumedFrom);
	}

	public SessionMetadata forkedFrom(String id, Instant now) {
		return new SessionMetadata(now, connection, rowLimit, timeoutSeconds, readOnly, lastStatement, id);
	}

	static String shorten(String statement) {
		if (statement == null) {
			return null;
		}
		String line = statement.strip().replaceAll("\\s+", " ");
		return line.length() <= LAST_STATEMENT_LENGTH ? line : line.substring(0, LAST_STATEMENT_LENGTH - 1) + "…";
	}

	/** Writes the file through a temporary file, so a reader never sees half a file. */
	public void write(Path file) throws IOException {
		Map<String, Object> connectionMap = new LinkedHashMap<>();
		connectionMap.put("host", connection.host());
		connectionMap.put("port", connection.port());
		connectionMap.put("database", connection.database());
		connectionMap.put("user", connection.user());
		connectionMap.put("url", connection.url());
		connectionMap.put("profile", connection.profile());
		Map<String, Object> settings = new LinkedHashMap<>();
		settings.put("row-limit", rowLimit);
		settings.put("timeout", timeoutSeconds);
		settings.put("read-only", readOnly);
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("started", started.toString());
		root.put("connection", connectionMap);
		root.put("settings", settings);
		root.put("last-statement", lastStatement);
		root.put("resumed-from", resumedFrom);
		var options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(temporary, new Yaml(options).dump(root), StandardCharsets.UTF_8);
		Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	/** @throws IOException when the file is missing or not a valid {@code session.yaml} */
	public static SessionMetadata read(Path file) throws IOException {
		try {
			Map<?, ?> root = (Map<?, ?>) new Yaml(new LoaderOptions()).load(Files.readString(file, StandardCharsets.UTF_8));
			Map<?, ?> connection = (Map<?, ?>) root.get("connection");
			Map<?, ?> settings = (Map<?, ?>) root.get("settings");
			var info = new ConnectionInfo(text(connection.get("host")), connection.get("port") == null ? null
				: Integer.valueOf(connection.get("port").toString()), text(connection.get("database")),
				text(connection.get("user")), text(connection.get("url")), text(connection.get("profile")));
			Object timeout = settings.get("timeout");
			return new SessionMetadata(Instant.parse(root.get("started").toString()), info,
				Integer.parseInt(settings.get("row-limit").toString()), timeout == null ? null : Long.valueOf(timeout.toString()),
				Boolean.parseBoolean(settings.get("read-only").toString()), text(root.get("last-statement")),
				text(root.get("resumed-from")));
		}
		catch (RuntimeException e) {
			throw new IOException("Invalid session.yaml " + file + ": " + e.getMessage(), e);
		}
	}

	private static String text(Object value) {
		return value == null ? null : value.toString();
	}
}
