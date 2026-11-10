package com.datev.dbui.session;

import com.datev.dbui.config.Settings;

/** Session-local execution settings and their Slash commands. No JDBC or terminal knowledge. */
final class ExecutionControls {

	private int rowLimit;
	private int timeoutSeconds;

	ExecutionControls(Settings settings) {
		if (settings.rowLimit() < 1) {
			throw new IllegalArgumentException("Row limit must be 1 or more");
		}
		if (settings.timeout() != null && settings.timeout().isNegative()) {
			throw new IllegalArgumentException("Timeout must be 0 or more seconds");
		}
		rowLimit = settings.rowLimit();
		timeoutSeconds = settings.timeout() == null ? 0 : Math.toIntExact(settings.timeout().toSeconds());
	}

	int rowLimit() {
		return rowLimit;
	}

	int timeoutSeconds() {
		return timeoutSeconds;
	}

	/** Returns null when another command handler should handle this input. */
	MessageBlock handle(String input) {
		String[] parts = input.strip().split("\\s+");
		if (parts[0].equals("/timeout")) {
			return timeout(parts);
		}
		if (!parts[0].equals("/limit")) {
			return null;
		}
		try {
			if (parts.length != 2 || Integer.parseInt(parts[1]) < 1) {
				return MessageBlock.error("Usage: /limit N (1 to 2147483647)");
			}
			rowLimit = Integer.parseInt(parts[1]);
			return MessageBlock.info("Row limit: " + rowLimit);
		}
		catch (NumberFormatException e) {
			return MessageBlock.error("Usage: /limit N (1 to 2147483647)");
		}
	}

	private MessageBlock timeout(String[] parts) {
		if (parts.length == 2 && parts[1].equals("off")) {
			timeoutSeconds = 0;
			return MessageBlock.info("Timeout: off");
		}
		try {
			if (parts.length != 2 || Integer.parseInt(parts[1]) < 1) {
				return MessageBlock.error("Usage: /timeout <seconds|off> (seconds: 1 to 2147483647)");
			}
			timeoutSeconds = Integer.parseInt(parts[1]);
			return MessageBlock.info("Timeout: " + timeoutSeconds + " s");
		}
		catch (NumberFormatException e) {
			return MessageBlock.error("Usage: /timeout <seconds|off> (seconds: 1 to 2147483647)");
		}
	}
}
