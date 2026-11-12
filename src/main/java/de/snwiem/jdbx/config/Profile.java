package de.snwiem.jdbx.config;

import java.util.Map;

/**
 * A named set of connection settings from {@code profiles.yaml}. Either {@code url} or the host fields are set.
 * {@code readOnly} is {@code null} when the Profile does not say. {@code schema} is the initial search path.
 * {@code toString} hides the password.
 */
public record Profile(String name, String url, String host, Integer port, String database, String user,
		String password, Boolean readOnly, String schema, Map<String, String> properties) {

	@Override
	public String toString() {
		return "Profile[" + name + "]";
	}

}
