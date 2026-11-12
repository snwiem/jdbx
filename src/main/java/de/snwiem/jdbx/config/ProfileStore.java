package de.snwiem.jdbx.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

/** Raw Profile values: environment references remain literal. YAML comments are not retained. */
public final class ProfileStore {
	// JVM monitor avoids OverlappingFileLockException; the file lock also serializes other processes.
	private static final Object WRITES = new Object();
	private final Path file;

	public ProfileStore(Path directory) {
		file = directory.resolve("profiles.yaml");
	}

	public List<String> names() {
		return profiles(read()).keySet().stream().sorted().toList();
	}

	/** The caller displays this in a yellow info Message block. */
	public String warning() {
		try {
			if (Files.exists(file) && !PosixFilePermissions.fromString("rw-------").containsAll(Files.getPosixFilePermissions(file))) {
				return "Warning: profiles.yaml has wider permissions than 600. Saving a Profile sets mode 600.";
			}
		}
		catch (IOException | UnsupportedOperationException e) {
			return "Warning: cannot check profiles.yaml permissions. Use a filesystem that supports mode 600.";
		}
		return null;
	}

	public void remove(String name) {
		change(root -> {
			var profiles = profiles(root);
			if (profiles.remove(name) == null) throw new ConfigException("Unknown Profile '" + name + "'.");
			root.put("profiles", profiles);
		});
	}

	public Profile profile(String name) {
		Object value = profiles(read()).get(name);
		if (!(value instanceof Map<?, ?> fields)) {
			throw new ConfigException("Unknown Profile '" + name + "'.");
		}
		Map<String, String> properties = new LinkedHashMap<>();
		if (fields.get("properties") instanceof Map<?, ?> map) {
			map.forEach((k, v) -> properties.put(k.toString(), v.toString()));
		}
		return new Profile(name, text(fields, "url"), text(fields, "host"),
			fields.get("port") == null ? null : Integer.valueOf(text(fields, "port")), text(fields, "database"),
			text(fields, "user"), text(fields, "password"), fields.get("read-only") == null ? null
				: Boolean.valueOf(text(fields, "read-only")), text(fields, "schema"), properties);
	}

	public void save(Profile profile) {
		change(root -> saveInto(root, profile));
	}

	private void change(java.util.function.Consumer<Map<String, Object>> edit) {
		synchronized (WRITES) {
			try {
				Files.createDirectories(file.getParent());
				try (var channel = java.nio.channels.FileChannel.open(file.resolveSibling(".profiles.lock"),
					java.util.Set.of(java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE),
					PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
					var lock = channel.lock()) {
					var root = read();
					edit.accept(root);
					write(root);
				}
			}
			catch (IOException e) {
				throw new ConfigException("Cannot lock profiles.yaml for editing.");
			}
		}
	}

	private void saveInto(Map<String, Object> root, Profile profile) {
		Map<String, Object> profiles = profiles(root);
		Map<String, Object> fields = new LinkedHashMap<>();
		put(fields, "url", profile.url());
		put(fields, "host", profile.host());
		put(fields, "port", profile.port());
		put(fields, "database", profile.database());
		put(fields, "user", profile.user());
		put(fields, "password", profile.password());
		put(fields, "read-only", profile.readOnly());
		put(fields, "schema", profile.schema());
		if (!profile.properties().isEmpty()) fields.put("properties", profile.properties());
		profiles.put(profile.name(), fields);
		root.put("profiles", profiles);
	}

	private static String text(Map<?, ?> fields, String key) {
		return fields.get(key) == null ? null : fields.get(key).toString();
	}

	private static void put(Map<String, Object> fields, String key, Object value) {
		if (value != null) fields.put(key, value);
	}

	private Map<String, Object> read() {
		try {
			return Files.exists(file) ? map(new Yaml(new LoaderOptions()).load(Files.readString(file))) : new LinkedHashMap<>();
		}
		catch (IOException | org.yaml.snakeyaml.error.YAMLException e) {
			throw new ConfigException("Cannot read profiles.yaml.");
		}
	}

	private static Map<String, Object> map(Object value) {
		var result = new LinkedHashMap<String, Object>();
		if (value == null) return result;
		if (!(value instanceof Map<?, ?> values)) throw new ConfigException("profiles.yaml must contain a map.");
		values.forEach((k, v) -> result.put(k.toString(), v));
		return result;
	}

	private static Map<String, Object> profiles(Map<String, Object> root) {
		return map(root.get("profiles"));
	}

	private void write(Map<String, Object> root) {
		Path temporary = null;
		try {
			Files.createDirectories(file.getParent());
			temporary = Files.createTempFile(file.getParent(), ".profiles-", ".tmp",
				PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
			var options = new DumperOptions();
			options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
			Files.writeString(temporary, new Yaml(options).dump(root));
			Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (IOException e) {
			throw new ConfigException("Cannot save profiles.yaml.");
		}
		finally {
			if (temporary != null) {
				try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
			}
		}
	}
}
