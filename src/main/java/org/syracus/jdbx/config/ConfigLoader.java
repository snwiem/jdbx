package org.syracus.jdbx.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Reads {@code config.yaml} (Settings) and {@code profiles.yaml} (Profiles) from the config directory. Read side
 * only. The environment is injected so tests need no real environment variables.
 */
public class ConfigLoader {

	private static final Pattern ENV_REFERENCE = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");

	private static final Set<String> FORMATS = Set.of("table", "markdown", "csv", "tsv", "json", "json-pretty");

	private static final Set<String> ORIENTATIONS = Set.of("horizontal", "vertical");

	private static final Set<String> PROFILE_KEYS = Set.of("url", "host", "port", "database", "user", "password",
			"read-only", "schema", "properties");

	private final Path directory;

	private final Map<String, String> environment;

	public ConfigLoader(Path directory, Map<String, String> environment) {
		this.directory = directory;
		this.environment = environment;
	}

	/** Option, then {@code DBUI_CONFIG_DIR}, then {@code $XDG_CONFIG_HOME/dbui}, then {@code ~/.config/dbui}. */
	public static Path directory(String option, Map<String, String> environment, Path home) {
		if (option != null && !option.isBlank()) {
			return Path.of(option);
		}
		String fromEnvironment = environment.get("DBUI_CONFIG_DIR");
		if (fromEnvironment != null && !fromEnvironment.isBlank()) {
			return Path.of(fromEnvironment);
		}
		String xdg = environment.get("XDG_CONFIG_HOME");
		if (xdg != null && !xdg.isBlank()) {
			return Path.of(xdg).resolve("dbui");
		}
		return home.resolve(".config").resolve("dbui");
	}

	public Settings settings() {
		return settings(error -> { throw new ConfigException(error); });
	}

	/** Reject a bad key map, report its error, and retain the other settings with default editor keys. */
	public Settings settingsForInteractive(Consumer<String> keyBindingError) {
		return settings(error -> keyBindingError.accept(error + " Default key bindings apply."));
	}

	private Settings settings(Consumer<String> keyBindingError) {
		Map<String, Object> root = read("config.yaml");
		Settings defaults = Settings.defaults();
		int rowLimit = root.containsKey("row-limit") ? integer("config.yaml", "row-limit", root.get("row-limit"))
				: defaults.rowLimit();
		Duration timeout = root.get("timeout") == null ? null
				: Duration.ofSeconds(integer("config.yaml", "timeout", root.get("timeout")));
		String format = choice("format", root.get("format"), FORMATS, defaults.format());
		String orientation = choice("orientation", root.get("orientation"), ORIENTATIONS, defaults.orientation());
		boolean readOnly = root.containsKey("read-only") ? bool("config.yaml", "read-only", root.get("read-only"))
				: defaults.readOnly();
		String nullMarker = marker("null-marker", root.get("null-marker"), defaults.nullMarker());
		String multilineMarker = marker("multiline-marker", root.get("multiline-marker"), defaults.multilineMarker());
		int maxWidth = root.containsKey("max-width") ? integer("config.yaml", "max-width", root.get("max-width"))
				: defaults.maxWidth();
		if (maxWidth < 0) {
			throw new ConfigException("config.yaml: 'max-width' must be 0 or more, but is '" + maxWidth + "'.");
		}
		return new Settings(rowLimit, timeout, format, orientation, readOnly, nullMarker, multilineMarker, maxWidth,
				timezone(root.get("timezone")), historyEnabled(root.get("history")), sessionsKeep(root.get("sessions")),
				keyBindings(root.get("key-bindings"), keyBindingError));
	}

	private static KeyBindings keyBindings(Object value, Consumer<String> error) {
		try {
			return KeyBindings.from(value);
		}
		catch (ConfigException e) {
			error.accept(e.getMessage());
			return KeyBindings.defaults();
		}
	}

	/** {@code history: {enabled: true|false}}, the nested form of the PRD wording. */
	private static boolean historyEnabled(Object history) {
		if (history == null) {
			return true;
		}
		if (!(history instanceof Map<?, ?> map)) {
			throw new ConfigException("config.yaml: 'history' must be a map such as 'enabled: false', but is '" + history + "'.");
		}
		return map.get("enabled") == null ? true : bool("config.yaml", "history.enabled", map.get("enabled"));
	}

	/** {@code sessions: {keep: <n>}}, at least 1. */
	private static int sessionsKeep(Object sessions) {
		if (sessions == null) {
			return Settings.DEFAULT_SESSIONS_KEEP;
		}
		if (!(sessions instanceof Map<?, ?> map)) {
			throw new ConfigException("config.yaml: 'sessions' must be a map such as 'keep: 50', but is '" + sessions + "'.");
		}
		if (map.get("keep") == null) {
			return Settings.DEFAULT_SESSIONS_KEEP;
		}
		int keep = integer("config.yaml", "sessions.keep", map.get("keep"));
		if (keep < 1) {
			throw new ConfigException("config.yaml: 'sessions.keep' must be 1 or more, but is '" + keep + "'.");
		}
		return keep;
	}

	public List<String> profileNames() {
		return List.copyOf(new TreeSet<>(rawProfiles().keySet()));
	}

	/** Loads one Profile and resolves its {@code ${ENV}} references. Other Profiles are not resolved. */
	public Profile profile(String name) { return profile(name, true); }

	/** Resolve typed fields, but keep the literal password for the editor and its provenance rules. */
	public Profile profileForEditing(String name) { return profile(name, false); }

	private Profile profile(String name, boolean resolvePassword) {
		Map<String, Object> raw = rawProfiles();
		if (!raw.containsKey(name)) {
			throw new ConfigException("Unknown Profile '" + name + "'. Known Profiles in "
					+ directory.resolve("profiles.yaml") + ": "
					+ (raw.isEmpty() ? "(none)" : String.join(", ", profileNames())));
		}
		String where = "Profile '" + name + "'";
		Object value = raw.get(name);
		if (!(value instanceof Map<?, ?> fields)) {
			throw new ConfigException(where + " must be a map of fields.");
		}
		for (Object key : fields.keySet()) {
			if (!PROFILE_KEYS.contains(String.valueOf(key))) {
				throw new ConfigException(where + ": unknown field '" + key + "'. Known fields: "
						+ String.join(", ", new TreeSet<>(PROFILE_KEYS)));
			}
		}
		String url = resolvePassword ? text(where, "url", fields) : fields.get("url") == null ? null : String.valueOf(fields.get("url"));
		String host = text(where, "host", fields);
		if (url != null && host != null) {
			throw new ConfigException(where + ": give either 'url' or 'host', not both.");
		}
		Integer port = fields.get("port") == null ? null : integer(where, "port", resolve(where, fields.get("port")));
		Boolean readOnly = fields.get("read-only") == null ? null
				: bool(where, "read-only", resolve(where, fields.get("read-only")));
		Map<String, String> properties = new LinkedHashMap<>();
		Object rawProperties = fields.get("properties");
		if (rawProperties != null) {
			if (!(rawProperties instanceof Map<?, ?> map)) {
				throw new ConfigException(where + ": 'properties' must be a map.");
			}
			map.forEach((k, v) -> properties.put(String.valueOf(k), String.valueOf(resolvePassword ? resolve(where, v) : v)));
		}
		return new Profile(name, url, host, port, text(where, "database", fields), text(where, "user", fields),
				resolvePassword ? text(where, "password", fields) : fields.get("password") == null ? null : String.valueOf(fields.get("password")),
				readOnly, text(where, "schema", fields), properties);
	}

	private Map<String, Object> rawProfiles() {
		Object profiles = read("profiles.yaml").get("profiles");
		if (profiles == null) {
			return Map.of();
		}
		if (!(profiles instanceof Map<?, ?> map)) {
			throw new ConfigException("profiles.yaml: 'profiles' must be a map of Profile names.");
		}
		Map<String, Object> result = new LinkedHashMap<>();
		map.forEach((k, v) -> result.put(String.valueOf(k), v));
		return result;
	}

	private String text(String where, String key, Map<?, ?> fields) {
		Object value = fields.get(key);
		return value == null ? null : String.valueOf(resolve(where + " field '" + key + "'", value));
	}

	/** Replaces {@code ${NAME}} in strings. Other values pass through. */
	private Object resolve(String where, Object value) {
		if (!(value instanceof String string)) {
			return value;
		}
		Matcher matcher = ENV_REFERENCE.matcher(string);
		StringBuilder result = new StringBuilder();
		while (matcher.find()) {
			String variable = matcher.group(1);
			String replacement = environment.get(variable);
			if (replacement == null) {
				throw new ConfigException(where + " uses ${" + variable + "}, but that environment variable is not set.");
			}
			matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(result);
		return result.toString();
	}

	private Map<String, Object> read(String file) {
		Path path = directory.resolve(file);
		if (!Files.exists(path)) {
			return Map.of();
		}
		try {
			Object loaded = new Yaml(new LoaderOptions()).load(Files.readString(path));
			if (loaded == null) {
				return Map.of();
			}
			if (!(loaded instanceof Map<?, ?> map)) {
				throw new ConfigException(path + ": the top level must be a map.");
			}
			Map<String, Object> result = new LinkedHashMap<>();
			map.forEach((k, v) -> result.put(String.valueOf(k), v));
			return result;
		}
		catch (IOException e) {
			throw new ConfigException("Cannot read " + path + ": " + e.getMessage());
		}
		catch (YAMLException e) {
			throw new ConfigException("Invalid YAML in " + path + ": " + e.getMessage());
		}
	}

	private static String marker(String key, Object value, String fallback) {
		if (value == null) {
			return fallback;
		}
		if (value instanceof Map<?, ?> || value instanceof List<?>) {
			throw new ConfigException("config.yaml: '" + key + "' must be text, but is '" + value + "'.");
		}
		return String.valueOf(value);
	}

	private static ZoneId timezone(Object value) {
		if (value == null) {
			return null;
		}
		try {
			return ZoneId.of(String.valueOf(value));
		}
		catch (DateTimeException e) {
			throw new ConfigException("config.yaml: 'timezone' is '" + value
					+ "'. Use a time zone id such as 'Europe/Berlin' or 'UTC'.");
		}
	}

	private static String choice(String key, Object value, Set<String> allowed, String fallback) {
		if (value == null) {
			return fallback;
		}
		String text = String.valueOf(value);
		if (!allowed.contains(text)) {
			throw new ConfigException("config.yaml: '" + key + "' is '" + text + "'. Allowed: "
					+ String.join(", ", new TreeSet<>(allowed)));
		}
		return text;
	}

	private static int integer(String where, String key, Object value) {
		if (value instanceof Integer i) {
			return i;
		}
		if (value instanceof String s) {
			try {
				return Integer.parseInt(s.trim());
			}
			catch (NumberFormatException e) {
				// fall through
			}
		}
		throw new ConfigException(where + ": '" + key + "' must be a whole number, but is '" + value + "'.");
	}

	private static boolean bool(String where, String key, Object value) {
		if (value instanceof Boolean b) {
			return b;
		}
		if (value instanceof String s && (s.equals("true") || s.equals("false"))) {
			return Boolean.parseBoolean(s);
		}
		throw new ConfigException(where + ": '" + key + "' must be true or false, but is '" + value + "'.");
	}

}
