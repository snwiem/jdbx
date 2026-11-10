package com.datev.dbui.profile;

/** /connect hook. Kept separate for migration to the shared Slash command registry (issue 0012). */
public final class ConnectCommand {
	private final ProfileCommands profiles;
	public ConnectCommand(ProfileCommands profiles) { this.profiles = profiles; }
	public ProfileCommands.Action execute(String argument) { return profiles.connect(argument); }
}
