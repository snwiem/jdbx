package de.snwiem.jdbx.session;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import de.snwiem.jdbx.config.ConnectionParameters;

/**
 * The non-secret part of a connection, as stored in {@code session.yaml}. {@code url} has no credentials.
 * Values may be {@code null} when the URL does not carry them.
 */
public record ConnectionInfo(String host, Integer port, String database, String user, String url, String profile) {

	private static final Pattern TARGET = Pattern.compile("^jdbc:postgresql://(?:[^/@?]*@)?(\\[[^\\]]+]|[^/:?]+)(?::(\\d+))?(?:/([^?]*))?");

	private static final Pattern SECRET_KEY = Pattern.compile("(?i).*(pass|secret|token|key).*|user");

	/** Takes the values from resolved parameters; the password is never read. */
	public static ConnectionInfo of(ConnectionParameters parameters, String profileName) {
		String url = parameters.url();
		var target = TARGET.matcher(url);
		String host = null;
		Integer port = null;
		String database = null;
		if (target.find()) {
			host = target.group(1);
			port = target.group(2) == null ? null : Integer.valueOf(target.group(2));
			database = target.group(3) == null || target.group(3).isEmpty() ? null : target.group(3);
		}
		String user = parameters.properties().get("user");
		return new ConnectionInfo(host, port, database, user, withoutCredentials(url), profileName);
	}

	/** {@code user@host:port/database}, the form shown in the Status bar and in the {@code /resume} list. */
	public String label() {
		var text = new StringBuilder();
		if (user != null) {
			text.append(user).append('@');
		}
		text.append(host == null ? "?" : host);
		if (port != null) {
			text.append(':').append(port);
		}
		if (database != null) {
			text.append('/').append(database);
		}
		return text.toString();
	}

	private static String withoutCredentials(String url) {
		String result = url.replaceFirst("^(jdbc:[a-z]+://)[^/@?]*@", "$1");
		int query = result.indexOf('?');
		if (query < 0) {
			return result;
		}
		String kept = Arrays.stream(result.substring(query + 1).split("&"))
			.filter(pair -> !SECRET_KEY.matcher(pair.split("=", 2)[0]).matches()).collect(Collectors.joining("&"));
		return result.substring(0, query) + (kept.isEmpty() ? "" : "?" + kept);
	}
}
