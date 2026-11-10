package com.datev.dbui.session;

import com.datev.dbui.config.ProfileDialogModel;

/** A pending interaction, rendered by the terminal and answered through the Session API (never submit secrets). */
public sealed interface SessionInteraction {
	record Dialog(ProfileDialogModel model, boolean connect, String editingName) implements SessionInteraction { }
	record Password(String label) implements SessionInteraction { }
	record Confirmation(String profileName) implements SessionInteraction { }
}
