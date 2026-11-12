package com.datev.dbui.tui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.datev.dbui.Result;
import com.datev.dbui.session.MessageBlock;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;

class MessageBlockRendererTest {

	private final MessageBlockRenderer renderer = new MessageBlockRenderer();

	@Test
	void transcriptUsesConfiguredValueMarkersWidthAndTimeZoneWithoutShiftingPlainTimestamp() {
		var options = com.datev.dbui.FormatOptions.defaults().withNullMarker("∅").withMultiLineMarker("↵")
			.withMaxWidth(25).withTimeZone(java.time.ZoneId.of("America/New_York"));
		var configured = new MessageBlockRenderer(true, options);
		var result = new Result(List.of("plain", "zoned", "nothing", "lines", "long"),
			List.of(java.util.Arrays.asList(java.time.LocalDateTime.parse("2024-01-15T12:34:56"),
				java.time.OffsetDateTime.parse("2024-01-15T12:34:56Z"), null, "a\nb", "abcdefghijklmnopqrstuvwxyz")));
		assertThat(configured.rows(List.of(MessageBlock.result(result)), 160))
			.extracting(MessageBlockRenderer.Row::text)
			.contains("2024-01-15T12:34:56 | 2024-01-15T07:34:56-05:00 | ∅       | a↵b   | abcdefghijklmnopqrstuvwx…");
	}

	@Test
	void inputEchoIsAFullWidthGrayBandWithPaddingAndNoBorder() {
		var rows = renderer.rows(List.of(MessageBlock.input("select 1")), 12);
		assertThat(rows).extracting(MessageBlockRenderer.Row::text).containsExactly("", "select 1", "");
		var buffer = Buffer.empty(Rect.of(12, 3));
		renderer.render(buffer, buffer.area(), rows, 0);
		assertThat(line(buffer, 1)).isEqualTo(" select 1   ");
		for (int y = 0; y < 3; y++) {
			for (int x = 0; x < 12; x++) {
				assertThat(buffer.get(x, y).style().bg()).contains(Color.indexed(238));
			}
		}
	}

	@Test
	void resultUsesTheExistingHorizontalTableWithPadding() {
		var result = new Result(List.of("id", "name"), List.of(List.of(1, "alice")));
		var rows = renderer.rows(List.of(MessageBlock.result(result)), 24);
		assertThat(rows).extracting(MessageBlockRenderer.Row::text)
			.containsExactly("", "id | name", "---+------", "1  | alice", "");
		var buffer = Buffer.empty(Rect.of(24, 5));
		renderer.render(buffer, buffer.area(), rows, 0);
		assertThat(line(buffer, 1)).isEqualTo(" id | name              ");
		assertThat(buffer.get(0, 1).style().bg()).isEmpty();
	}

	@Test
	void resultKeepsAnEmptyLastRowInsteadOfTreatingItAsPadding() {
		var result = new Result(List.of("value"), List.of(List.of("")));
		assertThat(renderer.rows(List.of(MessageBlock.result(result)), 20))
			.extracting(MessageBlockRenderer.Row::text).containsExactly("", "value", "-----", "", "");
	}

	@Test
	void narrowBandsWrapWithoutWritingOutsideTheViewport() {
		var rows = renderer.rows(List.of(MessageBlock.input("abcdef\ngh")), 6);
		assertThat(rows).extracting(MessageBlockRenderer.Row::text).containsExactly("", "abcd", "ef", "gh", "");
		var buffer = Buffer.empty(Rect.of(10, 5));
		renderer.render(buffer, new Rect(1, 0, 6, 5), rows, 0);
		assertThat(line(buffer, 1)).isEqualTo("  abcd    ");
		assertThat(buffer.get(7, 1).style().bg()).isEmpty();
	}

	@Test
	void sqlErrorUsesARedBand() {
		var buffer = Buffer.empty(Rect.of(24, 3));
		renderer.render(buffer, buffer.area(), renderer.rows(List.of(MessageBlock.error("bad Statement")), 24), 0);
		assertThat(line(buffer, 1)).startsWith(" bad Statement");
		assertThat(buffer.get(23, 1).style().bg()).contains(Color.indexed(52));
	}

	@Test
	void infoIsAYellowBandWithTheCommandTag() {
		var rows = renderer.rows(List.of(MessageBlock.info("INSERT 0 3")), 24);
		assertThat(rows).extracting(MessageBlockRenderer.Row::text).containsExactly("", "INSERT 0 3", "");
		var buffer = Buffer.empty(Rect.of(24, 3));
		renderer.render(buffer, buffer.area(), rows, 0);
		assertThat(line(buffer, 1)).startsWith(" INSERT 0 3");
		assertThat(buffer.get(23, 1).style().bg()).contains(Color.indexed(58));
	}

	@Test
	void errorBandKeepsTheServerTextWithSqlStateAndPosition() {
		var text = "ERROR: column \"nme\" does not exist (42703), position 8";
		assertThat(renderer.rows(List.of(MessageBlock.error(text)), 80))
			.extracting(MessageBlockRenderer.Row::text).containsExactly("", text, "");
	}

	@Test
	void withoutColorNoColorIsWrittenAndBandsDifferByLabel() {
		var plain = new MessageBlockRenderer(false);
		var blocks = List.of(MessageBlock.input("select 1"), MessageBlock.info("INSERT 0 3"), MessageBlock.error("bad"));
		var rows = plain.rows(blocks, 20);
		assertThat(rows).extracting(MessageBlockRenderer.Row::text)
			.containsExactly("", "> select 1", "", "", "info: INSERT 0 3", "", "", "error: bad", "");
		var buffer = Buffer.empty(Rect.of(20, rows.size()));
		plain.render(buffer, buffer.area(), rows, 0);
		for (int y = 0; y < rows.size(); y++) {
			for (int x = 0; x < 20; x++) {
				assertThat(buffer.get(x, y).style().fg()).isEmpty();
				assertThat(buffer.get(x, y).style().bg()).isEmpty();
			}
		}
	}

	@Test
	void noColorEnvironmentVariableMustBeNonEmpty() {
		assertThat(MessageBlockRenderer.colorEnabled(java.util.Map.of())).isTrue();
		assertThat(MessageBlockRenderer.colorEnabled(java.util.Map.of("NO_COLOR", ""))).isTrue();
		assertThat(MessageBlockRenderer.colorEnabled(java.util.Map.of("NO_COLOR", "1"))).isFalse();
	}

	private static String line(Buffer buffer, int y) {
		var text = new StringBuilder();
		for (int x = 0; x < buffer.area().width(); x++) {
			text.append(buffer.get(x, y).symbol());
		}
		return text.toString();
	}
}
