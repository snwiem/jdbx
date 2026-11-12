package de.snwiem.jdbx.session;

import de.snwiem.jdbx.config.ProfileDialogModel;

/** A pending interaction, rendered by the terminal and answered through the Session API (never submit secrets). */
public sealed interface SessionInteraction {
	record Dialog(ProfileDialogModel model, boolean connect, String editingName) implements SessionInteraction { }
	record Password(String label) implements SessionInteraction { }
	record Confirmation(String profileName) implements SessionInteraction { }
}
