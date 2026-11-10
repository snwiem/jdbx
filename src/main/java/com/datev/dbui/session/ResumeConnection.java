package com.datev.dbui.session;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.function.Function;

import com.datev.dbui.config.ConfigException;
import com.datev.dbui.config.ConnectionOptions;
import com.datev.dbui.config.ConnectionParameters;
import com.datev.dbui.config.Profile;
import com.datev.dbui.config.Settings;

/**
 * Decides how Resume gets a connection without asking for a password. The password is never stored in the
 * Session directory, so it must come from the current connection or from a Profile.
 */
public final class ResumeConnection {

	/** The outcome of {@link #plan}. */
	public sealed interface Plan permits Keep, Reconnect, Unavailable { }

	/** The chosen Session uses the connection that is open now. */
	public record Keep() implements Plan { }

	/** Open this connection instead of the current one. */
	public record Reconnect(ConnectionParameters parameters) implements Plan { }

	/** No password is available without a prompt; the message tells the user what is missing. */
	public record Unavailable(String message) implements Plan { }

	private ResumeConnection() { }

	/**
	 * @param profiles loads a Profile by name with its {@code ${ENV}} references resolved; may throw
	 *            {@link ConfigException} or return {@code null}
	 */
	public static Plan plan(String sessionId, ConnectionInfo stored, boolean storedReadOnly, ConnectionInfo currentInfo,
			ConnectionParameters current, Function<String, Profile> profiles, Settings settings) {
		if (sameTarget(stored, currentInfo)) {
			return new Keep();
		}
		String profileProblem = null;
		if (stored.profile() != null) {
			try {
				Profile profile = profiles.apply(stored.profile());
				if (profile == null) {
					profileProblem = "Profile '" + stored.profile() + "' does not exist any more.";
				}
				else {
					var resolved = ConnectionParameters.resolve(new ConnectionOptions(), profile, settings);
					return new Reconnect(new ConnectionParameters(resolved.url(), resolved.properties(), storedReadOnly));
				}
			}
			catch (ConfigException e) {
				profileProblem = e.getMessage();
			}
		}
		boolean sameLogin = stored.url() != null && Objects.equals(stored.user(), currentInfo.user())
			&& Objects.equals(stored.host(), currentInfo.host()) && Objects.equals(stored.port(), currentInfo.port());
		if (sameLogin && current.properties().get("password") != null) {
			return new Reconnect(new ConnectionParameters(stored.url(), new LinkedHashMap<>(current.properties()), storedReadOnly));
		}
		return new Unavailable("Cannot resume Session " + sessionId + ": the password for " + stored.label()
			+ " is missing. dbui never stores passwords, and the password dialog (issue 0013) is not built yet. "
			+ (profileProblem == null ? "" : profileProblem + " ")
			+ "Start dbui with that connection, or use a Profile that reads the password from ${ENV}.");
	}

	private static boolean sameTarget(ConnectionInfo stored, ConnectionInfo current) {
		return Objects.equals(stored.host(), current.host()) && Objects.equals(stored.port(), current.port())
			&& Objects.equals(stored.database(), current.database()) && Objects.equals(stored.user(), current.user());
	}
}
