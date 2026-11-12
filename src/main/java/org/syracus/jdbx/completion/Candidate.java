package org.syracus.jdbx.completion;

/**
 * One suggestion. {@code insert} is the exact text that replaces the typed word (quoted when the name needs it);
 * {@code label} is what the popup shows.
 */
public record Candidate(String insert, String label, Kind kind) {

	public enum Kind {
		KEYWORD, SCHEMA, TABLE, VIEW, COLUMN, COMMAND, ARGUMENT
	}
}
