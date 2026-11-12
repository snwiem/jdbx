package org.syracus.jdbx;

/** A {@code json} or {@code jsonb} column value. Holds valid JSON text that formatters embed unchanged in {@code json}. */
public record JsonValue(String text) {

	@Override
	public String toString() {
		return text;
	}
}
