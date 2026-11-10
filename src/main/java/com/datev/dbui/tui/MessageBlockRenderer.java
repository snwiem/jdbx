package com.datev.dbui.tui;

import java.util.ArrayList;
import java.util.List;

import com.datev.dbui.TableFormatter;
import com.datev.dbui.session.MessageBlock;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.CharWidth;

/** Lays out Message blocks as full-width, padded bands without borders. */
public final class MessageBlockRenderer {

	public record Row(MessageBlock.Kind kind, String text) { }

	public List<Row> rows(List<MessageBlock> blocks, int width) {
		List<Row> rows = new ArrayList<>();
		for (var block : blocks) {
			rows.add(new Row(block.kind(), ""));
			String text = block.text();
			if (block.kind() == MessageBlock.Kind.RESULT) {
				String table = new TableFormatter().format(block.result());
				// Remove only the formatter's line terminator, not an empty row or trailing cell whitespace.
				text = table.endsWith("\n") ? table.substring(0, table.length() - 1) : table;
			}
			for (String line : text.split("\n", -1)) {
				do {
					String part = CharWidth.substringByWidth(line, Math.max(1, width - 2));
					// A wide glyph cannot fit in a one-cell viewport. Consume it without splitting a surrogate pair.
					if (part.isEmpty() && !line.isEmpty()) {
						line = line.substring(Character.charCount(line.codePointAt(0)));
						rows.add(new Row(block.kind(), "?"));
					}
					else {
						rows.add(new Row(block.kind(), part));
						line = line.substring(part.length());
					}
				} while (!line.isEmpty());
			}
			rows.add(new Row(block.kind(), ""));
		}
		return rows;
	}

	public void render(Buffer buffer, Rect area, List<Row> rows, int first) {
		if (area.width() < 1) {
			return;
		}
		for (int y = 0; y < area.height() && first + y < rows.size(); y++) {
			Row row = rows.get(first + y);
			Style style = switch (row.kind()) {
				case INPUT -> Style.EMPTY.fg(Color.indexed(255)).bg(Color.indexed(238));
				case ERROR -> Style.EMPTY.fg(Color.indexed(217)).bg(Color.indexed(52));
				case INFO -> Style.EMPTY.fg(Color.indexed(228)).bg(Color.indexed(58));
				case RESULT -> Style.EMPTY;
			};
			buffer.fill(new Rect(area.x(), area.y() + y, area.width(), 1), new Cell(" ", style));
			if (area.width() > 2) {
				buffer.setString(area.x() + 1, area.y() + y,
					CharWidth.substringByWidth(row.text(), area.width() - 2), style);
			}
		}
	}
}
