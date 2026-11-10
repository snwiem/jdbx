package com.datev.dbui.session;

import java.nio.file.Path;
import java.util.function.Function;

import com.datev.dbui.config.ConnectionParameters;
import com.datev.dbui.config.Profile;
import com.datev.dbui.config.Settings;

/**
 * What a Session needs to store its history and Session metadata and to Resume an earlier Session.
 *
 * @param stateBase the state directory (see {@link SessionDirectory#stateBase})
 * @param parameters the connection that is open, including the password; it stays in memory
 * @param profileName the Profile in use, or {@code null}
 * @param profiles loads a Profile by name with {@code ${ENV}} resolved; may throw {@code ConfigException}
 */
public record SessionContext(Path stateBase, Settings settings, ConnectionParameters parameters, String profileName,
		Function<String, Profile> profiles) { }
