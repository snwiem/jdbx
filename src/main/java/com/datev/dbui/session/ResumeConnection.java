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
 * Decides how Resume gets a connection, including a request for a masked password prompt. The password is never stored in the
 * Session directory, so it must come from the current connection or from a Profile.
 */
public final class ResumeConnection {

	/** The outcome of {@link #plan}. */
	public sealed interface Plan permits Keep, Reconnect, Prompt { }

	/** The chosen Session uses the connection that is open now. */
	public record Keep() implements Plan { }

	/** Open this connection instead of the current one. */
	public record Reconnect(ConnectionParameters parameters) implements Plan { }

	/** These non-secret parameters need a password from the masked prompt. */
	public record Prompt(String message, ConnectionParameters parameters) implements Plan { }

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
					if (profile.password() == null && (profile.url() == null || !profile.url().matches(".*[?&]password=.*"))) {
						var options = new ConnectionOptions(); options.password = "";
						var resolved = ConnectionParameters.resolve(options, profile, settings);
						var properties = new LinkedHashMap<>(resolved.properties()); properties.remove("password");
						return new Prompt("Session " + sessionId + ": password for " + stored.label(),
							new ConnectionParameters(resolved.url(), properties, storedReadOnly));
					}
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
		var properties = new LinkedHashMap<String, String>();
		properties.put("ApplicationName", ConnectionParameters.APPLICATION_NAME);
		if (stored.user() != null) properties.put("user", stored.user());
		return new Prompt("Session " + sessionId + ": password for " + stored.label()
			+ (profileProblem == null ? "" : ". " + profileProblem), new ConnectionParameters(stored.url(), properties, storedReadOnly));
	}

	private static boolean sameTarget(ConnectionInfo stored, ConnectionInfo current) {
		return Objects.equals(stored.host(), current.host()) && Objects.equals(stored.port(), current.port())
			&& Objects.equals(stored.database(), current.database()) && Objects.equals(stored.user(), current.user());
	}
}
