package com.datev.dbui.config;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.datev.dbui.session.ConnectionInfo;

/** Terminal-independent dialog fields. Transient credentials are never mapped to a saved Profile. */
public final class ProfileDialogModel {
	public enum Field {
		NAME("Name"), URL("JDBC URL"), HOST("Host"), PORT("Port"), DATABASE("Database"), USER("User"),
		PASSWORD("Password"), READ_ONLY("Read-only (true/false)"), SCHEMA("Schema"), PROPERTIES("Properties (k=v;k=v)");
		private final String label;
		Field(String label) { this.label = label; }
		public String label() { return label; }
	}

	private final Map<Field, String> fields = new EnumMap<>(Field.class);
	private final String transientPassword;
	private final String savedPassword;
	private boolean passwordEdited;

	/** savedPassword is the original literal password from profiles.yaml, never a resolved environment value. */
	public ProfileDialogModel(Profile initial, String savedPassword) {
		this.transientPassword = initial.password();
		this.savedPassword = savedPassword;
		fields.put(Field.NAME, text(initial.name()));
		fields.put(Field.URL, text(initial.url()));
		fields.put(Field.HOST, text(initial.host()));
		fields.put(Field.PORT, initial.port() == null ? "" : initial.port().toString());
		fields.put(Field.DATABASE, text(initial.database()));
		fields.put(Field.USER, text(initial.user()));
		fields.put(Field.PASSWORD, text(savedPassword));
		fields.put(Field.READ_ONLY, initial.readOnly() == null ? "false" : initial.readOnly().toString());
		fields.put(Field.SCHEMA, text(initial.schema()));
		fields.put(Field.PROPERTIES, initial.properties().entrySet().stream().filter(e -> !secret(e.getKey()))
			.map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(";")));
	}

	public String value(Field field) { return fields.get(field); }
	public void set(Field field, String value) {
		fields.put(field, value);
		if (field == Field.PASSWORD) passwordEdited = true;
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
		if (!value(Field.READ_ONLY).equals("true") && !value(Field.READ_ONLY).equals("false")) {
			throw new ConfigException("Read-only must be true or false.");
		}
		connectionProfile(); // validates the driver property syntax
	}

	public Profile connectionProfile() {
		return profile(passwordEdited || savedPassword != null ? password() : transientPassword, false);
	}

	public Profile savedProfile() {
		return profile(passwordEdited ? password() : savedPassword, true);
	}

	private String password() { return value(Field.PASSWORD).isEmpty() ? null : value(Field.PASSWORD); }

	private Profile profile(String password, boolean saving) {
		String url = blank(value(Field.URL));
		if (saving && url != null) url = ConnectionInfo.of(new ConnectionParameters(url, Map.of(), false), null).url();
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
			blank(value(Field.DATABASE)), blank(value(Field.USER)), password, Boolean.valueOf(value(Field.READ_ONLY)),
			blank(value(Field.SCHEMA)), properties);
	}

	private static boolean secret(String key) { return key.toLowerCase(java.util.Locale.ROOT).matches(".*(pass|secret|token|key).*|user"); }
	private static String text(String value) { return value == null ? "" : value; }
	private static String blank(String value) { return value.isBlank() ? null : value.strip(); }
}
