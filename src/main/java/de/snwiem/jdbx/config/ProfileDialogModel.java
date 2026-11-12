package org.syracus.jdbx.config;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.syracus.jdbx.session.ConnectionInfo;

/** Terminal-independent dialog fields. Transient credentials are never mapped to a saved Profile. */
public final class ProfileDialogModel {
	public enum Field {
		NAME("Name"), URL("JDBC URL"), HOST("Host"), PORT("Port"), DATABASE("Database"), USER("User"),
		PASSWORD("Password"), READ_ONLY("Read-only (true/false/empty=current)"), SCHEMA("Schema"), PROPERTIES("Properties (k=v;k=v)");
		private final String label;
		Field(String label) { this.label = label; }
		public String label() { return label; }
	}

	private final Map<Field, String> fields = new EnumMap<>(Field.class);
	private final String transientPassword;
	private final String savedPassword;
	private boolean passwordEdited;
	private boolean urlEdited;
	private final String transientUrl;
	private final String persistedUrl;

	/** savedPassword is the original literal password from profiles.yaml, never a resolved environment value. */
	public ProfileDialogModel(Profile initial, String savedPassword) { this(initial, savedPassword, null, false); }

	public static ProfileDialogModel edit(Profile persisted) { return new ProfileDialogModel(persisted, persisted.password(), persisted, true); }

	private ProfileDialogModel(Profile initial, String savedPassword, Profile persisted, boolean keepPersistedUrl) {
		this.persistedUrl = keepPersistedUrl && persisted != null ? persisted.url() : null;
		this.transientPassword = initial.password();
		this.savedPassword = savedPassword;
		this.transientUrl = initial.url();
		fields.put(Field.NAME, text(initial.name()));
		fields.put(Field.URL, initial.url() == null ? "" : ConnectionInfo.of(new ConnectionParameters(initial.url(), Map.of(), false), null).url());
		fields.put(Field.HOST, text(initial.host()));
		fields.put(Field.PORT, initial.port() == null ? "" : initial.port().toString());
		fields.put(Field.DATABASE, text(initial.database()));
		fields.put(Field.USER, text(first(initial.user(), userFromUrl(initial.url()))));
		fields.put(Field.PASSWORD, text(savedPassword));
		fields.put(Field.READ_ONLY, initial.readOnly() == null ? "" : initial.readOnly().toString());
		fields.put(Field.SCHEMA, text(initial.schema()));
		fields.put(Field.PROPERTIES, initial.properties().entrySet().stream().filter(e -> !secret(e.getKey()))
			.map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(";")));
	}

	public static ProfileDialogModel prefill(ConnectionOptions options, Profile resolved, Profile raw, Settings settings) {
		Profile fallback = resolved == null ? new Profile(null, null, null, null, null, null, null, null, null, Map.of()) : resolved;
		boolean hostOptions = options.host != null || options.port != null || options.database != null;
		String url = options.url != null ? options.url : hostOptions ? null : fallback.url();
		Profile initial = new Profile(fallback.name(), url, url == null ? first(options.host, fallback.host()) : null,
			url == null ? first(options.port, fallback.port()) : null, url == null ? first(options.database, fallback.database()) : null,
			first(options.user, fallback.user()), first(options.password, fallback.password()),
			options.readOnly != null ? options.readOnly : fallback.readOnly(),
			first(options.schema, fallback.schema()), raw == null ? fallback.properties() : raw.properties());
		return new ProfileDialogModel(initial, raw == null ? null : raw.password(), raw, options.url == null && !hostOptions);
	}

	private static <T> T first(T option, T fallback) { return option == null ? fallback : option; }

	public String value(Field field) { return fields.get(field); }
	public void set(Field field, String value) {
		fields.put(field, value);
		if (field == Field.PASSWORD) passwordEdited = true;
		if (field == Field.URL) urlEdited = true;
	}

	public void validate(boolean saving) {
		if (saving && value(Field.NAME).isBlank()) throw new ConfigException("Name is required to save a Profile.");
		if (value(Field.NAME).contains("\n") || value(Field.NAME).contains("\r")) throw new ConfigException("Name must be one line.");
		if (value(Field.URL).isBlank()) {
			if (value(Field.HOST).isBlank()) throw new ConfigException("Host or JDBC URL is required.");
			if (value(Field.DATABASE).isBlank()) throw new ConfigException("Database is required.");
			if (value(Field.USER).isBlank()) throw new ConfigException("User is required.");
		}
		else {
			if (!value(Field.URL).startsWith("jdbc:postgresql:")) throw new ConfigException("Use a jdbc:postgresql: URL.");
			if (!value(Field.HOST).isBlank()) throw new ConfigException("Give either JDBC URL or Host, not both.");
		}
		if (!value(Field.PORT).isBlank()) {
			try {
				int port = Integer.parseInt(value(Field.PORT));
				if (port < 1 || port > 65535) throw new NumberFormatException();
			}
			catch (NumberFormatException e) { throw new ConfigException("Port must be a whole number from 1 to 65535."); }
		}
		if (!value(Field.READ_ONLY).isBlank() && !value(Field.READ_ONLY).equals("true") && !value(Field.READ_ONLY).equals("false")) {
			throw new ConfigException("Read-only must be true, false or empty (current Session mode).");
		}
		connectionProfile(); // validates the driver property syntax
	}

	public Profile connectionProfile() {
		return profile(passwordEdited ? password() : transientPassword, false);
	}

	public Profile savedProfile() {
		return profile(passwordEdited ? password() : savedPassword, true);
	}

	private String password() { return value(Field.PASSWORD).isEmpty() ? null : value(Field.PASSWORD); }

	private Profile profile(String password, boolean saving) {
		String url = !saving && !urlEdited ? transientUrl : blank(value(Field.URL));
		if (saving && !urlEdited && persistedUrl != null) url = persistedUrl;
		else if (saving && url != null) url = ConnectionInfo.of(new ConnectionParameters(url, Map.of(), false), null).url();
		if (passwordEdited && url != null) url = withoutPassword(url);
		Map<String, String> properties = new LinkedHashMap<>();
		for (String line : value(Field.PROPERTIES).split("[;\n]")) {
			if (line.isBlank()) continue;
			String[] pair = line.split("=", 2);
			if (pair.length != 2 || pair[0].isBlank()) throw new ConfigException("Driver properties must use key=value;key=value.");
			if (secret(pair[0].strip())) throw new ConfigException("Use the password field for credentials, not driver properties.");
			properties.put(pair[0].strip(), pair[1]);
		}
		String port = blank(value(Field.PORT));
		return new Profile(blank(value(Field.NAME)), url, blank(value(Field.HOST)), port == null ? null : Integer.valueOf(port),
			blank(value(Field.DATABASE)), blank(value(Field.USER)), password, value(Field.READ_ONLY).isBlank() ? null : Boolean.valueOf(value(Field.READ_ONLY)),
			blank(value(Field.SCHEMA)), properties);
	}

	private static String userFromUrl(String url) {
		if (url == null || !url.contains("?")) return null;
		for (String pair : url.substring(url.indexOf('?') + 1).split("&")) {
			if (pair.startsWith("user=")) {
				try { return java.net.URLDecoder.decode(pair.substring(5), java.nio.charset.StandardCharsets.UTF_8); }
				catch (IllegalArgumentException e) { throw new ConfigException("The JDBC URL user has invalid URL encoding."); }
			}
		}
		return null;
	}

	private static String withoutPassword(String url) {
		int query = url.indexOf('?');
		if (query < 0) return url;
		String kept = java.util.Arrays.stream(url.substring(query + 1).split("&"))
			.filter(pair -> !pair.startsWith("password=")).collect(Collectors.joining("&"));
		return url.substring(0, query) + (kept.isEmpty() ? "" : "?" + kept);
	}

	private static boolean secret(String key) { return key.equalsIgnoreCase("password") || key.equalsIgnoreCase("user"); }
	private static String text(String value) { return value == null ? "" : value; }
	private static String blank(String value) { return value.isBlank() ? null : value.strip(); }
}
