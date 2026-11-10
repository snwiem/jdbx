package com.datev.dbui.tui;

import java.util.List;
import java.util.function.Consumer;

import com.datev.dbui.FormatOptions;
import com.datev.dbui.session.ExecutionStatus;
import com.datev.dbui.session.MessageBlock;
import com.datev.dbui.config.Settings;

import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.text.CharWidth;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.tui.event.TextEditing;
import dev.tamboui.tui.event.TickEvent;
import dev.tamboui.widgets.input.TextArea;
import dev.tamboui.widgets.input.TextAreaState;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;

/** Terminal adapter for the editor and Transcript. Contains no JDBC or Slash command logic. */
public final class InteractiveScreen {

	private final MessageBlockRenderer renderer;
	private final Style rule;
	private final Style statusStyle;
	private final Transcript transcript;
	private final ScrollbarState scrollbar = new ScrollbarState();
	private int transcriptHeight;
	private boolean busy;
	private ExecutionStatus status;
	private final String connection;
	private final Consumer<String> send;
	private final Runnable cancel;
	private final EditorKeys keys;
	private final TextAreaState editor = new TextAreaState();
	private final TextArea editorWidget = TextArea.builder().overflow(Overflow.WRAP_WORD).build();

	public InteractiveScreen(String connection, Consumer<String> send) {
		this(connection, send, EditorKeys.defaults());
	}

	public InteractiveScreen(String connection, Consumer<String> send, EditorKeys keys) {
		this(connection, send, keys, MessageBlockRenderer.colorEnabled(System.getenv()));
	}

	/** With {@code color == false} (NO_COLOR) the screen uses no color codes; the Status bar is reversed instead. */
	InteractiveScreen(String connection, Consumer<String> send, EditorKeys keys, boolean color) {
		this(connection, send, keys, color, () -> { }, Settings.defaults());
	}

	public InteractiveScreen(String connection, Consumer<String> send, Runnable cancel, Settings settings) {
		this(connection, send, EditorKeys.defaults(), MessageBlockRenderer.colorEnabled(System.getenv()), cancel, settings);
	}

	private InteractiveScreen(String connection, Consumer<String> send, EditorKeys keys, boolean color, Runnable cancel, Settings settings) {
		this.cancel = cancel;
		this.status = new ExecutionStatus(settings.rowLimit(), false, java.time.Duration.ZERO, null);
		this.renderer = new MessageBlockRenderer(color, FormatOptions.from(settings));
		this.transcript = new Transcript(renderer);
		this.rule = color ? Style.EMPTY.fg(Color.indexed(244)) : Style.EMPTY;
		this.statusStyle = color ? Style.EMPTY.fg(Color.indexed(252)).bg(Color.indexed(236)) : Style.EMPTY.reversed();
		this.connection = connection;
		this.send = send;
		this.keys = keys;
	}

	public void update(List<MessageBlock> blocks) {
		transcript.replace(blocks);
	}

	public void busy(boolean busy) {
		this.busy = busy;
	}

	public void status(ExecutionStatus status) {
		this.status = status;
	}

	public boolean handle(Event event) {
		if (event instanceof TickEvent && busy) {
			return true;
		}
		if (event instanceof MouseEvent mouse && mouse.y() < transcriptHeight) {
			if (mouse.kind() == MouseEventKind.SCROLL_UP) {
				transcript.scroll(3);
			}
			else if (mouse.kind() == MouseEventKind.SCROLL_DOWN) {
				transcript.scroll(-3);
			}
			return true;
		}
		if (event instanceof KeyEvent key) {
			if (key.isRelease()) {
				return false;
			}
			if (key.isKey(KeyCode.PAGE_UP) || key.isKey(KeyCode.PAGE_DOWN)) {
				transcript.page(key.isKey(KeyCode.PAGE_UP) ? 1 : -1);
				return true;
			}
			if (key.hasCtrl() && key.isCharIgnoreCase('c')) {
				if (busy) {
					cancel.run();
				}
				else {
					editor.clear();
				}
				return true;
			}
			if (busy) {
				return true;
			}
			if (key.hasCtrl() && key.isCharIgnoreCase('d')) {
				return true;
			}
			if (keys.newline().matches(key)) {
				editor.insert('\n');
				return true;
			}
			if (keys.send().matches(key)) {
				String input = editor.text();
				editor.clear();
				if (!input.isBlank()) {
					send.accept(input);
				}
				return true;
			}
		}
		return !busy && TextEditing.handle(editor, event, Overflow.WRAP_WORD);
	}

	public void render(Frame frame) {
		int width = frame.width();
		int height = frame.height();
		if (width < 1 || height < 1) {
			return;
		}
		int statusHeight = height >= 5 ? 2 : 1;
		int available = Math.max(0, height - statusHeight - 2);
		int preferred = editorWidget.preferredHeight(width, editor);
		int editorHeight = Math.min(available, Math.clamp(preferred, 1, 8));
		transcriptHeight = Math.max(0, available - editorHeight);
		var buffer = frame.buffer();
		var rows = transcript.rows(Math.max(0, width - 1), transcriptHeight);
		renderer.render(buffer, new Rect(0, 0, Math.max(0, width - 1), transcriptHeight), rows, transcript.first());
		if (transcriptHeight > 0) {
			scrollbar.contentLength(rows.size()).viewportContentLength(transcriptHeight).position(transcript.first());
			frame.renderStatefulWidget(Scrollbar.vertical(), new Rect(width - 1, 0, 1, transcriptHeight), scrollbar);
		}
		if (editorHeight > 0) {
			buffer.setString(0, transcriptHeight, "─".repeat(width), rule);
			if (preferred <= editorHeight) {
				editor.scrollUp(Integer.MAX_VALUE);
			}
			editorWidget.renderWithCursor(new Rect(0, transcriptHeight + 1, width, editorHeight), buffer, editor, frame);
			buffer.setString(0, transcriptHeight + editorHeight + 1, "─".repeat(width), rule);
		}
		String[] status = { " " + connection, " " + StatusBarText.of(this.status) };
		for (int row = 0; row < statusHeight; row++) {
			int y = height - statusHeight + row;
			buffer.fill(new Rect(0, y, width, 1), new Cell(" ", statusStyle));
			buffer.setString(0, y, CharWidth.substringByWidth(status[row], width), statusStyle);
		}
	}
}
