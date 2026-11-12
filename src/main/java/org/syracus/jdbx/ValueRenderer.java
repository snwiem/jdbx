package com.datev.dbui;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.HexFormat;

/** Turns one cell value into text, shared by all Formats. */
final class ValueRenderer {

	private final FormatOptions options;

	ValueRenderer(FormatOptions options) {
		this.options = options;
	}

	/** Text of a non-null value without any Format-specific cutting or escaping. Binary is the full hex string. */
	String plain(Object value) {
		return switch (value) {
			case byte[] bytes -> "\\x" + HexFormat.of().formatHex(bytes);
			case BigDecimal number -> number.toPlainString();
			case Instant instant -> instant.atZone(options.timeZone()).toOffsetDateTime()
					.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
			case OffsetDateTime time -> time.atZoneSameInstant(options.timeZone()).toOffsetDateTime()
					.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
			case ZonedDateTime time -> time.withZoneSameInstant(options.timeZone()).toOffsetDateTime()
					.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
			case java.sql.Timestamp timestamp -> plain(timestamp.toInstant());
			case java.sql.Date date -> date.toLocalDate().toString();
			case java.sql.Time time -> time.toLocalTime().toString();
			case LocalDateTime time -> time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
			case LocalDate date -> date.format(DateTimeFormatter.ISO_LOCAL_DATE);
			case LocalTime time -> time.format(DateTimeFormatter.ISO_LOCAL_TIME);
			case OffsetTime time -> time.format(DateTimeFormatter.ISO_OFFSET_TIME);
			default -> value.toString();
		};
	}

	/** Text for {@code table}: marker for newlines, ellipsis when longer than the maximum width, binary with length. */
	String forTable(Object value) {
		if (value == null) {
			return options.nullMarker();
		}
		if (value instanceof byte[] bytes) {
			String hex = cut(plain(bytes));
			return hex + " (" + bytes.length + (bytes.length == 1 ? " byte)" : " bytes)");
		}
		return cut(markNewlines(plain(value)));
	}

	/** Text for {@code markdown}: no cutting; newlines become {@code <br>} and pipes are escaped. */
	String forMarkdown(Object value) {
		if (value == null) {
			return options.nullMarker();
		}
		return plain(value).replace("|", "\\|").replaceAll("\r\n|\r|\n", "<br>");
	}

	private String markNewlines(String text) {
		return text.replaceAll("\r\n|\r|\n", java.util.regex.Matcher.quoteReplacement(options.multiLineMarker()));
	}

	private String cut(String text) {
		int max = options.maxWidth();
		if (max <= 0 || text.codePointCount(0, text.length()) <= max) {
			return text;
		}
		int end = text.offsetByCodePoints(0, Math.max(max - 1, 0));
		return text.substring(0, end) + "…";
	}
}
