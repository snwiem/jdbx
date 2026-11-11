package com.datev.dbui.config;

/** Configurable editor actions. Enter sends input and is not configurable. */
public record KeyBindings(KeyBinding newline, KeyBinding orientationCycle, KeyBinding orientationFallback,
		KeyBinding complete, KeyBinding completeAlternate) {

	public KeyBindings {
		var actions = new java.util.LinkedHashMap<KeyBinding, String>();
		actions.put(new KeyBinding("enter"), "send");
		actions.put(new KeyBinding("ctrl-c"), "cancel");
		actions.put(new KeyBinding("ctrl-d"), "ignore");
		actions.put(new KeyBinding("ctrl-r"), "search");
		add(actions, newline, "newline");
		add(actions, orientationCycle, "orientation-cycle");
		add(actions, orientationFallback, "orientation-fallback");
		add(actions, complete, "complete");
		add(actions, completeAlternate, "complete-alternate");
	}

	private static void add(java.util.Map<KeyBinding, String> actions, KeyBinding key, String action) {
		String previous = actions.putIfAbsent(key, action);
		if (previous != null) {
			throw new ConfigException("config.yaml: 'key-bindings." + action + "' key '" + key.name()
				+ "' conflicts with '" + previous + "'. Bind each action to a different key.");
		}
	}

	public static KeyBindings from(Object value) {
		if (value == null) return defaults();
		if (!(value instanceof java.util.Map<?, ?> values)) {
			throw new ConfigException("config.yaml: 'key-bindings' must be a map of actions to key names.");
		}
		var known = java.util.Set.of("newline", "orientation-cycle", "orientation-fallback", "complete", "complete-alternate");
		for (var action : values.keySet()) {
			if (!(action instanceof String name) || !known.contains(name)) {
				throw new ConfigException("config.yaml: unknown 'key-bindings." + action + "'. Allowed actions: "
					+ String.join(", ", new java.util.TreeSet<>(known)));
			}
		}
		var defaults = defaults();
		return new KeyBindings(binding(values, "newline", defaults.newline()),
			binding(values, "orientation-cycle", defaults.orientationCycle()),
			binding(values, "orientation-fallback", defaults.orientationFallback()),
			binding(values, "complete", defaults.complete()),
			binding(values, "complete-alternate", defaults.completeAlternate()));
	}

	private static KeyBinding binding(java.util.Map<?, ?> values, String action, KeyBinding fallback) {
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
