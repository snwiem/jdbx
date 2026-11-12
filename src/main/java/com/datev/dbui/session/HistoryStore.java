package com.datev.dbui.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * The input history of one Session: one entry per line in the {@code history} file. Line breaks and backslashes
 * inside an entry are escaped ({@code \n}, {@code \r}, {@code \\}), so multi-line entries round-trip.
 * A write failure never throws; the store keeps its entries in memory and reports {@link #failure()}.
 */
public final class HistoryStore {

	private static final java.util.regex.Pattern INLINE_CREDENTIALS = java.util.regex.Pattern.compile(
		"(?i)(?:^|[\\s?&;])(?:--?(?:(?:pg)?pass(?:word|wd)?|pwd|secret|token)(?:=|\\s|$)|(?:(?:pg)?pass(?:word|wd)?|pwd|secret|token)(?:\\s*=|\\s+))|jdbc:|://");

	private final Path file;
	private final boolean enabled;
	private final List<String> entries = new ArrayList<>();
	private IOException failure;

	private HistoryStore(Path file, boolean enabled) {
		this.file = file;
		this.enabled = enabled;
	}

	/** Loads the existing file, if any. With {@code enabled == false} the store keeps and writes nothing. */
	public static HistoryStore open(Path file, boolean enabled) {
		var store = new HistoryStore(file, enabled);
		if (enabled && file != null && Files.isRegularFile(file)) {
			try {
				for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
					store.entries.add(unescape(line));
				}
			}
			catch (IOException e) {
				store.entries.clear();
				store.failure = e;
			}
		}
		return store;
	}

	public static HistoryStore disabled() {
		return new HistoryStore(null, false);
	}

	public boolean enabled() {
		return enabled;
	}

	/** Adds an entry unless it is blank, starts with a space, is a {@code /resume}, {@code /quit} or {@code /history} line, or repeats the last entry. */
	public synchronized void add(String input) {
		if (!enabled || input.isBlank() || input.startsWith(" ") || isLocalOnly(input)) {
			return;
		}
		if (!entries.isEmpty() && entries.getLast().equals(input)) {
			return;
		}
		entries.add(input);
		if (file == null || failure != null) {
			return;
		}
		try {
			Files.writeString(file, escape(input) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
				StandardOpenOption.APPEND);
		}
		catch (IOException e) {
			failure = e;
		}
	}

	public synchronized List<String> entries() {
		return List.copyOf(entries);
	}

	/** Removes all entries, also from the file. A write failure is kept like in {@link #add}. */
	public synchronized void clear() {
		entries.clear();
		if (!enabled || file == null || failure != null) {
			return;
		}
		try {
			Files.writeString(file, "", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		}
		catch (IOException e) {
			failure = e;
		}
	}

	/** The first write or read failure, or {@code null}. */
	public synchronized IOException failure() {
		return failure;
	}

	private static boolean isLocalOnly(String input) {
		String name = input.strip().split("\\s+", 2)[0];
		return name.equals("/quit") || name.equals("/resume") || name.equals("/history") || containsProfileCredentials(input);
	}

	/** Profile credentials belong in masked interactions, never in raw input or a diagnostic quoting that input. */
	static boolean containsProfileCredentials(String input) {
		String[] parts = input.stripLeading().split("\\s+", 2);
		return parts.length == 2 && (parts[0].equals("/connect") || parts[0].equals("/profile"))
			&& INLINE_CREDENTIALS.matcher(parts[1]).find();
	}

	static String escape(String entry) {
		return entry.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
	}

	static String unescape(String line) {
		var result = new StringBuilder();
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '\\' && i + 1 < line.length()) {
				char next = line.charAt(++i);
				result.append(switch (next) {
					case 'n' -> '\n';
					case 'r' -> '\r';
					default -> next;
				});
			}
			else {
				result.append(c);
			}
		}
		return result.toString();
	}
}
