package com.datev.dbui.profile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.datev.dbui.config.*;
import com.datev.dbui.session.ConnectionInfo;

/** Profile command logic, independent of the terminal and Session execution. */
public final class ProfileCommands {
	public sealed interface Action permits Info, Connect, Edit, Remove { }
	public record Info(String text) implements Action { }
	public record Connect(Profile profile) implements Action { }
	public record Edit(ProfileDialogModel model, boolean connect, String editingName) implements Action { }
	public record Remove(String name) implements Action { }
	private final ProfileStore store;
	private final ConfigLoader loader;
	private final Map<String, String> environment;
	private List<String> listing;

	public ProfileCommands(Path directory, Map<String, String> environment) {
		store = new ProfileStore(directory);
		loader = new ConfigLoader(directory, environment);
		this.environment = Map.copyOf(environment);
	}

	public String warning() { return store.warning(); }
	public static boolean handles(String input) {
		return input.strip().matches("/(connect|profile)(\\s.*)?");
	}
	public Action execute(String input) {
		String[] parts = input.strip().split("\\s+", 2);
		String argument = parts.length == 1 ? "" : parts[1];
		return parts[0].equals("/connect") ? new ConnectCommand(this).execute(argument) : new ProfileCommand(this).execute(argument);
	}
	Action connect(String argument) {
		if (argument.isEmpty()) {
			if (store.names().isEmpty()) return new Edit(new ProfileDialogModel(new Profile(null, null, null, null, null, null, null, null, null, Map.of()), null), true, null);
			return new Info(list());
		}
		String name = argument;
		if (argument.matches("\\d+")) {
			List<String> names = listing == null ? store.names() : listing;
			try {
				int number = Integer.parseInt(argument);
				if (number < 1 || number > names.size()) throw new NumberFormatException();
				name = names.get(number - 1);
			}
			catch (NumberFormatException e) { throw new ConfigException("No Profile number " + argument + ". Type /connect to list Profiles."); }
		}
		return new Connect(load(name));
	}
	public Profile load(String name) { return resolve(loader.profileForEditing(name)); }

	/** Resolve dialog references, but request a prompt when only the password reference is unavailable. */
	public Profile resolve(Profile raw) {
		String password;
		try { password = resolve(raw.password()); }
		catch (ConfigException e) { password = null; }
		var properties = new java.util.LinkedHashMap<String, String>();
		raw.properties().forEach((key, value) -> properties.put(key, resolve(value)));
		return new Profile(raw.name(), resolve(raw.url()), resolve(raw.host()), raw.port(), resolve(raw.database()),
			resolve(raw.user()), password, raw.readOnly(), resolve(raw.schema()), properties);
	}
	private String resolve(String value) {
		if (value == null) return null;
		var matcher = java.util.regex.Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}").matcher(value);
		return matcher.replaceAll(match -> {
			String replacement = environment.get(match.group(1));
			if (replacement == null) throw new ConfigException("Environment variable " + match.group(1) + " is not set.");
			return java.util.regex.Matcher.quoteReplacement(replacement);
		});
	}

	Action edit(String name) {
		Profile initial = name == null ? new Profile(null, null, null, null, null, null, null, null, null, Map.of()) : loader.profileForEditing(name);
		return new Edit(new ProfileDialogModel(initial, initial.password()), false, name);
	}
	Action remove(String name) { store.profile(name); return new Remove(name); }
	public void removeConfirmed(String name) { store.remove(name); }
	public void save(ProfileDialogModel model, String editingName) {
		if (editingName != null && !editingName.equals(model.savedProfile().name())) throw new ConfigException("Keep the existing Profile name when editing. Use /profile add for a new name.");
		if (editingName == null && store.names().contains(model.savedProfile().name())) throw new ConfigException("That Profile already exists. Use /profile edit <name>.");
		store.save(model.savedProfile());
	}
	String list() {
		listing = store.names();
		var text = new StringBuilder("Profiles. Type /connect <name|number>. Use /profile add to create a Profile.");
		if (listing.isEmpty()) text.append("\n(none)");
		for (int i = 0; i < listing.size(); i++) {
			Profile profile = loader.profileForEditing(listing.get(i));
			String url = profile.url() == null ? "jdbc:postgresql://" + profile.host() + ":"
				+ (profile.port() == null ? 5432 : profile.port()) + "/" + profile.database() : profile.url();
			String label = ConnectionInfo.of(new ConnectionParameters(url, profile.user() == null ? Map.of() : Map.of("user", profile.user()), false), profile.name()).label();
			text.append('\n').append(i + 1).append("  ").append(profile.name()).append("  ").append(label);
		}
		return text.toString();
	}
}
