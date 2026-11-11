package com.datev.dbui.config;

/** A terminal-independent key name from config.yaml. */
public record KeyBinding(String name) {

	public enum Modifier { NONE, CTRL, ALT, SHIFT }

	public KeyBinding {
		String original = name;
		name = name.strip().toLowerCase(java.util.Locale.ROOT);
		if (!name.matches("(?:tab|enter|shift-(?:tab|enter)|(?:ctrl|alt)-(?:[a-z/]|space|tab|enter))")) {
			throw new ConfigException("invalid key name '" + original
				+ "'. Use tab, enter, shift-tab, shift-enter, or ctrl-/alt- with a letter, /, space, tab or enter (for example ctrl-j, alt-/).");
		}
	}

	public String key() {
		return name.substring(name.indexOf('-') + 1);
	}

	public Modifier modifier() {
		int separator = name.indexOf('-');
		return separator < 0 ? Modifier.NONE : Modifier.valueOf(name.substring(0, separator).toUpperCase(java.util.Locale.ROOT));
	}

	/** The same spelling as in config.yaml, capitalized for /help. */
	public String display() {
		return java.util.Arrays.stream(name.split("-"))
			.map(part -> part.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + part.substring(1))
			.collect(java.util.stream.Collectors.joining("-"));
	}
}
