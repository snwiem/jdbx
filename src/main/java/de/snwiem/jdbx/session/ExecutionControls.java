package de.snwiem.jdbx.session;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;

import de.snwiem.jdbx.config.Settings;

/** Session-local execution settings and their Slash commands. No JDBC or terminal knowledge. */
final class ExecutionControls {

	private final Supplier<Settings> settings;
	private final Consumer<Settings> update;

	ExecutionControls(Supplier<Settings> settings, Consumer<Settings> update) {
		this.settings = settings;
		this.update = update;
		validate(settings.get());
	}

	static void validate(Settings settings) {
		de.snwiem.jdbx.Format.parse(settings.format());
		de.snwiem.jdbx.Orientation.parse(settings.orientation());
		if (settings.rowLimit() < 1) {
			throw new IllegalArgumentException("Row limit must be 1 or more");
		}
		if (settings.timeout() != null && settings.timeout().isNegative()) {
			throw new IllegalArgumentException("Timeout must be 0 or more seconds");
		}
		if (settings.timeout() != null) {
			Math.toIntExact(settings.timeout().toSeconds());
		}
	}

	int rowLimit() {
		return settings.get().rowLimit();
	}

	int timeoutSeconds() {
		var timeout = settings.get().timeout();
		return timeout == null ? 0 : Math.toIntExact(timeout.toSeconds());
	}

	/** Picocli converter shared by /limit and /timeout. */
	static String positiveInteger(String argument) {
		try {
			int value = Integer.parseInt(argument);
			if (value > 0) {
				return Integer.toString(value);
			}
		}
		catch (NumberFormatException ignored) {
			// Use the same range message for a non-integer and an overflow.
		}
		throw new picocli.CommandLine.TypeConversionException("Use an integer from 1 to 2147483647");
	}

	/** Picocli converter for /width: a whole number from 0, or {@code off}. */
	static String widthArgument(String argument) {
		if (argument.equals("off")) {
			return argument;
		}
		try {
			if (Integer.parseInt(argument) >= 0) {
				return Integer.toString(Integer.parseInt(argument));
			}
		}
		catch (NumberFormatException ignored) {
			// Use the same range message for a non-integer and an overflow.
		}
		throw new picocli.CommandLine.TypeConversionException("Use an integer from 0 to 2147483647, or off");
	}

	/** The registry has already validated the argument through picocli. */
	MessageBlock limit(String argument) {
		var current = settings.get();
		update.accept(current.withExecution(Integer.parseInt(argument), current.timeout(), current.readOnly()));
		return MessageBlock.info("Row limit: " + rowLimit());
	}

	MessageBlock timeout(String argument) {
		if (argument.equals("off")) {
			var current = settings.get();
			update.accept(current.withExecution(current.rowLimit(), null, current.readOnly()));
			return MessageBlock.info("Timeout: off");
		}
		var current = settings.get();
		update.accept(current.withExecution(current.rowLimit(), Duration.ofSeconds(Integer.parseInt(argument)), current.readOnly()));
		return MessageBlock.info("Timeout: " + timeoutSeconds() + " s");
	}

	/** {@code argument} is a number from 0 or {@code off}; both 0 and {@code off} mean no cut. */
	MessageBlock width(String argument) {
		int width = argument.equals("off") ? 0 : Integer.parseInt(argument);
		update.accept(settings.get().withMaxWidth(width));
		return MessageBlock.info("Maximum cell width: " + (width == 0 ? "off" : width));
	}
}
