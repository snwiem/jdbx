package de.snwiem.jdbx.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import de.snwiem.jdbx.Format;
import de.snwiem.jdbx.FormatOptions;
import de.snwiem.jdbx.Orientation;
import de.snwiem.jdbx.Result;
import de.snwiem.jdbx.config.Settings;

/** Formats the held query Result like Save, independently of screen rendering. */
public final class CopyCommand {
	private static final int MAX_BYTES = 64 * 1024;
	private final ClipboardSink sink;

	public CopyCommand(ClipboardSink sink) {
		this.sink = sink;
	}

	public MessageBlock execute(Result result, Settings settings, Integer truncatedAt) {
		if (result == null || result.rows().isEmpty()) {
			return MessageBlock.info("No non-empty query Result is held. Nothing was sent to the terminal clipboard.");
		}
		String text = Format.parse(settings.format())
			.formatter(FormatOptions.from(settings), Orientation.parse(settings.orientation())).format(result);
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		if (bytes.length > MAX_BYTES) {
			return MessageBlock.info("Formatted Result exceeds the 64 KB clipboard limit. Use /save <file>. Nothing was sent.");
		}
		try {
			sink.write("\u001b]52;c;" + Base64.getEncoder().encodeToString(bytes) + "\u0007");
			return MessageBlock.info("Sent " + result.rows().size() + " rows to the terminal clipboard (" + settings.format() + ")"
				+ (truncatedAt == null ? "." : ", truncated at " + truncatedAt + " rows (Row limit)."));
		}
		catch (IOException e) {
			return MessageBlock.error("Cannot copy: " + e.getMessage());
		}
	}
}
