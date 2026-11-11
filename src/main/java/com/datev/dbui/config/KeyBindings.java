package com.datev.dbui.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Configurable editor actions. Enter sends input and is not configurable. */
public record KeyBindings(KeyBinding newline, KeyBinding orientationCycle, KeyBinding orientationFallback,
		KeyBinding complete, KeyBinding completeAlternate) {

	public KeyBindings {
		var actions = new LinkedHashMap<KeyBinding, String>();
		actions.put(new KeyBinding("enter"), "send");
		actions.put(new KeyBinding("ctrl-c"), "cancel");
		actions.put(new KeyBinding("ctrl-d"), "ignore");
		actions.put(new KeyBinding("ctrl-r"), "search");
		actions.put(new KeyBinding("ctrl-w"), "delete-previous-word");
		add(actions, newline, "newline");
		add(actions, orientationCycle, "orientation-cycle");
		add(actions, orientationFallback, "orientation-fallback");
		add(actions, complete, "complete");
		add(actions, completeAlternate, "complete-alternate");
	}

	private static void add(Map<KeyBinding, String> actions, KeyBinding key, String action) {
		String previous = actions.putIfAbsent(key, action);
		if (previous != null) {
			throw new ConfigException("config.yaml: 'key-bindings." + action + "' key '" + key.name()
				+ "' conflicts with '" + previous + "'. Bind each action to a different key.");
		}
	}

	public static KeyBindings from(Object value) {
		if (value == null) return defaults();
		if (!(value instanceof Map<?, ?> values)) {
			throw new ConfigException("config.yaml: 'key-bindings' must be a map of actions to key names.");
		}
		return from(values);
	}

	/** Parse an editor key map without a config file or terminal. */
	public static KeyBindings from(Map<?, ?> values) {
		if (values == null) return defaults();
		var known = Set.of("newline", "orientation-cycle", "orientation-fallback", "complete", "complete-alternate");
		for (var action : values.keySet()) {
			if (!(action instanceof String name) || !known.contains(name)) {
				throw new ConfigException("config.yaml: unknown 'key-bindings." + action + "'. Allowed actions: "
					+ String.join(", ", new TreeSet<>(known)));
			}
		}
		var defaults = defaults();
		return new KeyBindings(binding(values, "newline", defaults.newline()),
			binding(values, "orientation-cycle", defaults.orientationCycle()),
			binding(values, "orientation-fallback", defaults.orientationFallback()),
			binding(values, "complete", defaults.complete()),
			binding(values, "complete-alternate", defaults.completeAlternate()));
	}

	private static KeyBinding binding(Map<?, ?> values, String action, KeyBinding fallback) {
		if (!values.containsKey(action)) return fallback;
		Object value = values.get(action);
		try {
			if (!(value instanceof String text)) {
				throw new ConfigException("key name must be text, but is '" + value + "'.");
			}
			return new KeyBinding(text);
		}
		catch (ConfigException e) {
			throw new ConfigException("config.yaml: 'key-bindings." + action + "': " + e.getMessage());
		}
	}

	public static KeyBindings defaults() {
		return new KeyBindings(new KeyBinding("ctrl-j"), new KeyBinding("shift-tab"), new KeyBinding("ctrl-o"),
			new KeyBinding("tab"), new KeyBinding("alt-/"));
	}
}
