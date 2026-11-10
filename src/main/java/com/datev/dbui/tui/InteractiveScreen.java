package com.datev.dbui.tui;

import java.util.List;
import java.util.function.Consumer;

import com.datev.dbui.session.MessageBlock;

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
	private String connection;
	private final Consumer<String> send;
	private final EditorKeys keys;
	private final TextAreaState editor = new TextAreaState();
	private final HistoryNavigator history = new HistoryNavigator();
	private List<String> historyEntries = List.of();
	private ReverseSearch search;
	private String searchDraft = "";
	private final TextArea editorWidget = TextArea.builder().overflow(Overflow.WRAP_WORD).build();

	public InteractiveScreen(String connection, Consumer<String> send) {
		this(connection, send, EditorKeys.defaults());
	}

	public InteractiveScreen(String connection, Consumer<String> send, EditorKeys keys) {
		this(connection, send, keys, MessageBlockRenderer.colorEnabled(System.getenv()));
	}

	/** With {@code color == false} (NO_COLOR) the screen uses no color codes; the Status bar is reversed instead. */
	InteractiveScreen(String connection, Consumer<String> send, EditorKeys keys, boolean color) {
		this.renderer = new MessageBlockRenderer(color);
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

	/** The history of the Session, oldest first. Up, Down and Ctrl-R use it. */
	public void history(List<String> entries) {
		historyEntries = List.copyOf(entries);
		history.entries(entries);
	}

	/** The connection text of the Status bar; it changes when {@code /resume} reconnects. */
	public void connection(String connection) {
		this.connection = connection;
	}

	public void busy(boolean busy) {
		this.busy = busy;
	}

	public boolean handle(Event event) {
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
			if (busy) {
				return true;
			}
			if (search != null) {
				return handleSearch(key);
			}
			if (key.hasCtrl() && key.isCharIgnoreCase('r')) {
				searchDraft = editor.text();
				search = new ReverseSearch(historyEntries);
				return true;
			}
			if (key.hasCtrl() && key.isCharIgnoreCase('c')) {
				editor.clear();
				return true;
			}
			if (!key.hasCtrl() && !key.hasAlt() && (key.isKey(KeyCode.UP) || key.isKey(KeyCode.DOWN))
					&& handleHistory(key.isKey(KeyCode.UP))) {
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
				history.reset();
				if (!input.isBlank()) {
					send.accept(input);
				}
				return true;
			}
		}
		return !busy && TextEditing.handle(editor, event, Overflow.WRAP_WORD);
	}

	/** Up on the first display row or Down on the last one moves through the history; elsewhere the editor moves. */
	private boolean handleHistory(boolean up) {
		int width = editor.lastRenderedWidth();
		var rows = editor.computeDisplayRows(width > 0 ? width : Integer.MAX_VALUE / 2, Overflow.WRAP_WORD);
		int row = TextAreaState.findDisplayRowIndex(rows, editor.cursorRow(), editor.cursorCol());
		if (up ? row > 0 : row < rows.size() - 1) {
			return false;
		}
		var text = up ? history.previous(editor.text()) : history.next();
		text.ifPresent(this::setEditorText);
		return true;
	}

	private void setEditorText(String text) {
		editor.setText(text);
		editor.moveCursorToEnd();
	}

	/** Ctrl-R again: older match. Enter accepts into the editor, Esc and Ctrl-C cancel. */
	private boolean handleSearch(KeyEvent key) {
		if (key.isKey(KeyCode.ENTER)) {
			search = null;
			history.reset();
		}
		else if (key.isKey(KeyCode.ESCAPE) || key.hasCtrl() && key.isCharIgnoreCase('c')) {
			search = null;
			setEditorText(searchDraft);
		}
		else if (key.hasCtrl() && key.isCharIgnoreCase('r')) {
			search.older();
		}
		else if (key.isKey(KeyCode.BACKSPACE)) {
			search.backspace();
		}
		else if (!key.hasCtrl() && !key.hasAlt() && key.code() == KeyCode.CHAR && !Character.isISOControl(key.character())) {
			search.type(key.character());
		}
		if (search != null) {
			setEditorText(search.match() != null ? search.match() : searchDraft);
		}
		return true;
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
			int bottom = transcriptHeight + editorHeight + 1;
			if (search != null) {
				String text = " (reverse-search)`" + search.query() + "'" + (search.failing() ? "  no match" : "")
					+ "  Enter accepts · Esc cancels";
				buffer.fill(new Rect(0, bottom, width, 1), new Cell(" ", statusStyle));
				buffer.setString(0, bottom, CharWidth.substringByWidth(text, width), statusStyle);
			}
			else {
				buffer.setString(0, bottom, "─".repeat(width), rule);
			}
		}
		String[] status = { " " + connection, busy ? " Running Statement…" : " Enter sends · Ctrl-J newline · /quit exits" };
		for (int row = 0; row < statusHeight; row++) {
			int y = height - statusHeight + row;
			buffer.fill(new Rect(0, y, width, 1), new Cell(" ", statusStyle));
			buffer.setString(0, y, CharWidth.substringByWidth(status[row], width), statusStyle);
		}
	}
}
