package org.syracus.jdbx.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Resolved connection values: URL, driver properties and read-only state. Precedence per field: command-line
 * option, then Profile, then Settings, then default. Used by Batch mode and later by Interactive mode.
 * {@code toString} hides the properties because they hold the password.
 */
public record ConnectionParameters(String url, Map<String, String> properties, boolean readOnly) {

	public static final String APPLICATION_NAME = "jdbx";

	/**
	 * @param profile the selected Profile, or {@code null}
	 * @throws ConfigException when required values are missing; Batch mode never prompts
	 */
	public static ConnectionParameters resolve(ConnectionOptions options, Profile profile, Settings settings) {
		String host = first(options.host, profile == null ? null : profile.host());
		Integer port = first(options.port, profile == null ? null : profile.port());
		String database = first(options.database, profile == null ? null : profile.database());
		String user = first(options.user, profile == null ? null : profile.user());
		String password = first(options.password, profile == null ? null : profile.password());
		String schema = first(options.schema, profile == null ? null : profile.schema());

		boolean hostOptionGiven = options.host != null || options.port != null || options.database != null;
		String url = options.url;
		if (url == null && !hostOptionGiven && profile != null) {
			url = profile.url();
		}
		boolean urlBased = url != null;

		List<String> missing = new ArrayList<>();
		if (!urlBased) {
			if (host == null) {
				throw new ConfigException("No connection target: give --url, --host or a Profile (-p).");
			}
			if (database == null) {
				missing.add("--db");
			}
			if (user == null) {
				missing.add("--user");
			}
			url = "jdbc:postgresql://" + host + ":" + (port == null ? 5432 : port) + "/" + database;
		}
		boolean passwordInUrl = urlBased && hasPasswordParameter(url);
		if (password == null && !passwordInUrl) {
			missing.add("--password");
		}
		if (!missing.isEmpty()) {
			throw new ConfigException("Missing connection values: " + String.join(", ", missing)
					+ ". Give them as options or in the Profile. Batch mode never prompts for a password.");
		}

		Map<String, String> properties = new LinkedHashMap<>();
		if (profile != null) {
			properties.putAll(profile.properties());
		}
		properties.put("ApplicationName", APPLICATION_NAME);
		if (user != null) {
			properties.put("user", user);
		}
		if (password != null && !passwordInUrl) {
			properties.put("password", password);
		}
		if (schema != null) {
			properties.put("currentSchema", schema);
		}
		boolean readOnly = options.readOnly != null ? options.readOnly
				: profile != null && profile.readOnly() != null ? profile.readOnly() : settings.readOnly();
		return new ConnectionParameters(url, properties, readOnly);
	}

	/** Opens one plain JDBC connection (autocommit on). */
	public Connection open() throws SQLException {
		Properties driverProperties = new Properties();
		driverProperties.putAll(properties);
		// Without this the driver sends no read-only flag to the server while autocommit is on (finding of issue 0006).
		driverProperties.setProperty("readOnlyMode", "always");
		Connection connection = DriverManager.getConnection(url, driverProperties);
		if (readOnly) {
			connection.setReadOnly(true);
		}
		return connection;
	}

	private static boolean hasPasswordParameter(String url) {
		int query = url.indexOf('?');
		if (query < 0) {
			return false;
		}
		for (String pair : url.substring(query + 1).split("&")) {
			if (pair.startsWith("password=")) {
				return true;
			}
		}
		return false;
	}

	private static <T> T first(T preferred, T fallback) {
		return preferred != null ? preferred : fallback;
	}

	@Override
	public String toString() {
		return "ConnectionParameters[" + url + "]";
	}

}
