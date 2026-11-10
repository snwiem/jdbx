package com.datev.dbui.profile;

import com.datev.dbui.config.ConfigException;

/** /profile hook. Kept separate for migration to the shared Slash command registry (issue 0012). */
public final class ProfileCommand {
	private final ProfileCommands profiles;
	public ProfileCommand(ProfileCommands profiles) { this.profiles = profiles; }
	public ProfileCommands.Action execute(String argument) {
		if (argument.equals("list")) return new ProfileCommands.Info(profiles.list());
		if (argument.equals("add")) return profiles.edit(null);
		if (argument.startsWith("edit ") && !argument.substring(5).isBlank()) return profiles.edit(argument.substring(5).strip());
		if (argument.startsWith("remove ") && !argument.substring(7).isBlank()) return profiles.remove(argument.substring(7).strip());
		throw new ConfigException("Usage: /profile list|add|edit <name>|remove <name>");
	}
}
