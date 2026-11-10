package com.datev.dbui.profile;

/** Profile selection logic called by the shared registry's /connect action. */
public final class ConnectCommand {
	private final ProfileCommands profiles;
	public ConnectCommand(ProfileCommands profiles) { this.profiles = profiles; }
	public ProfileCommands.Action execute(String argument) { return profiles.connect(argument); }
}
