package de.snwiem.jdbx.config;

/** A configuration or connection value problem with a message that is safe to show (never contains a password). */
public class ConfigException extends RuntimeException {

	public ConfigException(String message) {
		super(message);
	}

}
