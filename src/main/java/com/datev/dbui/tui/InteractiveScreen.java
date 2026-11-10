package com.datev.dbui.tui;

import java.util.List;
import java.util.function.Consumer;

import com.datev.dbui.FormatOptions;
import com.datev.dbui.completion.Completion;
import com.datev.dbui.completion.CompletionEngine;
import com.datev.dbui.completion.SchemaModel;
import com.datev.dbui.session.ExecutionStatus;
import com.datev.dbui.session.MessageBlock;
import com.datev.dbui.config.Settings;

import dev.tamboui.buffer.Buffer;
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
	private final Style warningStyle;
	private final Transcript transcript;
	private final ScrollbarState scrollbar = new ScrollbarState();
	private int transcriptHeight;
	private boolean busy;
	private ExecutionStatus status;
	private Settings settings;
	private String connection;
	private final Consumer<String> send;
	private final Runnable cancel;
	private final Runnable cycleOrientation;
	private final EditorKeys keys;
	private final TextAreaState editor = new TextAreaState();
	private final HistoryNavigator history = new HistoryNavigator();
	private List<String> historyEntries = List.of();
	private ReverseSearch search;
	private String searchDraft = "";
	private CompletionBackend completionBackend = (text, cursor) -> java.util.concurrent.CompletableFuture
		.completedFuture(CompletionEngine.standard().complete(text, cursor, SchemaModel.EMPTY));
	private java.util.concurrent.CompletableFuture<Completion> pendingCompletion;
	private String pendingText;
	private int pendingCursor;
	private CompletionPopup popup;
	private String completionNote;
	private final Style popupStyle;
	private final Style popupSelectedStyle;
	private final Style noteStyle;
	private final TextArea editorWidget = TextArea.builder().overflow(Overflow.WRAP_WORD).build();

	public InteractiveScreen(String connection, Consumer<String> send) {
		this(connection, send, EditorKeys.defaults());
	}

	public InteractiveScreen(String connection, Consumer<String> send, EditorKeys keys) {
		this(connection, send, keys, MessageBlockRenderer.colorEnabled(System.getenv()));
	}

	/** With {@code color == false} (NO_COLOR) the screen uses no color codes; the Status bar is reversed instead. */
	InteractiveScreen(String connection, Consumer<String> send, EditorKeys keys, boolean color) {
		this(connection, send, keys, color, () -> { }, Settings.defaults(), () -> { });
	}

	public InteractiveScreen(String connection, Consumer<String> send, Runnable cancel, Settings settings) {
		this(connection, send, cancel, settings, () -> { });
	}

	public InteractiveScreen(String connection, Consumer<String> send, Runnable cancel, Settings settings, Runnable cycleOrientation) {
		this(connection, send, EditorKeys.defaults(), MessageBlockRenderer.colorEnabled(System.getenv()), cancel, settings, cycleOrientation);
	}

	private InteractiveScreen(String connection, Consumer<String> send, EditorKeys keys, boolean color, Runnable cancel, Settings settings,
			Runnable cycleOrientation) {
		this.cancel = cancel;
		this.cycleOrientation = cycleOrientation;
		this.settings = settings;
		this.status = new ExecutionStatus(settings.rowLimit(), false, java.time.Duration.ZERO, null, settings.readOnly(), true);
		this.renderer = new MessageBlockRenderer(color, FormatOptions.from(settings));
		this.transcript = new Transcript(renderer);
		this.rule = color ? Style.EMPTY.fg(Color.indexed(244)) : Style.EMPTY;
		this.statusStyle = color ? Style.EMPTY.fg(Color.indexed(252)).bg(Color.indexed(236)) : Style.EMPTY.reversed();
		this.warningStyle = color ? Style.EMPTY.fg(Color.indexed(214)).bg(Color.indexed(236)).bold() : null;
		this.popupStyle = color ? Style.EMPTY.fg(Color.indexed(252)).bg(Color.indexed(238)) : Style.EMPTY;
		this.popupSelectedStyle = color ? Style.EMPTY.fg(Color.indexed(255)).bg(Color.indexed(25)) : Style.EMPTY.reversed();
		this.noteStyle = color ? Style.EMPTY.fg(Color.YELLOW).bg(Color.indexed(236)) : Style.EMPTY.reversed();
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

	/** Where Ctrl-Space gets its candidates. The default knows keywords only. */
	public void completion(CompletionBackend backend) {
		this.completionBackend = backend;
	}

	public void busy(boolean busy) {
		this.busy = busy;
	}

	/** Current Session settings, including changes made by Slash commands and Resume. */
	public void settings(Settings settings) {
		this.settings = settings;
	}

	public void status(ExecutionStatus status) {
		this.status = status;
	}

	public boolean handle(Event event) {
		if (event instanceof TickEvent && pendingCompletion != null) {
			return finishCompletionIfReady();
		}
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
				else if (search != null) {
					return handleSearch(key);
				}
				else {
					editor.clear();
					history.reset();
					closeCompletion();
				}
				return true;
			}
			if (busy) {
				return true;
			}
			if (search != null) {
				return handleSearch(key);
			}
			completionNote = null;
			if (handleCompletionKey(key)) {
				return true;
			}
			if (keys.complete().matches(key) || keys.completeAlternate().matches(key)) {
				requestCompletion();
				return true;
			}
			if (keys.orientationCycle().matches(key) || keys.orientationFallback().matches(key)) {
				cycleOrientation.run();
				return true;
			}
			if (key.hasCtrl() && key.isCharIgnoreCase('r')) {
				searchDraft = editor.text();
				search = new ReverseSearch(historyEntries);
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

	// Completion: opens only on the key. While open, Up/Down/Tab/send key/Esc belong to the popup; any other key closes it.

	private boolean handleCompletionKey(KeyEvent key) {
		if (popup != null) {
			if (key.isKey(KeyCode.UP) || key.isKey(KeyCode.DOWN)) {
				if (key.isKey(KeyCode.UP)) {
					popup.up();
				}
				else {
					popup.down();
				}
				return true;
			}
			if (key.isKey(KeyCode.TAB) && !key.hasShift() && !key.hasCtrl() && !key.hasAlt() || keys.send().matches(key)) {
				var edit = popup.accept(editor.text());
				popup = null;
				setEditor(edit);
				return true;
			}
			boolean escape = key.isKey(KeyCode.ESCAPE);
			popup = null;
			return escape;
		}
		if (pendingCompletion != null) {
			if (keys.complete().matches(key) || keys.completeAlternate().matches(key)) {
				return true;
			}
			closeCompletion();
		}
		return false;
	}

	private void closeCompletion() {
		popup = null;
		completionNote = null;
		if (pendingCompletion != null) {
			pendingCompletion.cancel(true);
			pendingCompletion = null;
		}
	}

	private void requestCompletion() {
		pendingText = editor.text();
		pendingCursor = cursorOffset();
		completionNote = "loading schema…";
		pendingCompletion = completionBackend.complete(pendingText, pendingCursor);
		finishCompletionIfReady();
	}

	private boolean finishCompletionIfReady() {
		if (!pendingCompletion.isDone()) {
			return false;
		}
		var future = pendingCompletion;
		pendingCompletion = null;
		completionNote = null;
		try {
			var completion = future.join();
			if (editor.text().equals(pendingText) && cursorOffset() == pendingCursor) {
				var outcome = CompletionOutcome.of(pendingText, completion);
				if (outcome.edit() != null) {
					setEditor(outcome.edit());
				}
				popup = outcome.popup();
				completionNote = outcome.note();
			}
		}
		catch (java.util.concurrent.CancellationException e) {
			// The request was dropped.
		}
		catch (RuntimeException e) {
			Throwable cause = e.getCause() != null ? e.getCause() : e;
			completionNote = "completion failed: " + cause.getMessage();
		}
		return true;
	}

	/** The cursor as a character offset into the editor text. */
	private int cursorOffset() {
		int offset = editor.cursorCol();
		for (int row = 0; row < editor.cursorRow(); row++) {
			offset += editor.getLine(row).length() + 1;
		}
		return offset;
	}

	private void setEditor(Completion.Edit edit) {
		editor.setText(edit.text());
		editor.moveCursorToStart();
		for (int i = 0; i < edit.cursor(); i++) {
			editor.moveCursorRight();
		}
		history.reset();
	}

	private void renderCompletion(Buffer buffer, int width, int transcriptHeight) {
		if (popup != null && transcriptHeight > 0) {
			var visible = popup.visible();
			int rows = Math.min(visible.size(), transcriptHeight);
			int labelWidth = visible.stream().mapToInt(c -> CharWidth.of(c.label())).max().orElse(0);
			int kindWidth = visible.stream().mapToInt(c -> c.kind().name().length()).max().orElse(0);
			int popupWidth = Math.min(width, labelWidth + kindWidth + 5);
			int top = transcriptHeight - rows;
			boolean more = popup.completion().candidates().size() > visible.size();
			for (int row = 0; row < rows; row++) {
				var candidate = visible.get(row);
				var style = row == popup.selectedRow() ? popupSelectedStyle : popupStyle;
				String kind = candidate.kind().name().toLowerCase(java.util.Locale.ROOT);
				String mark = more && row == 0 && popup.firstVisible() > 0 ? "▲" : more && row == rows - 1 && popup.hasMoreBelow() ? "▼" : " ";
				String line = " " + candidate.label() + " ".repeat(Math.max(1, labelWidth - CharWidth.of(candidate.label()) + 2)) + kind;
				line += " ".repeat(Math.max(0, popupWidth - 1 - CharWidth.of(line))) + mark;
				buffer.fill(new Rect(0, top + row, popupWidth, 1), new Cell(" ", style));
				buffer.setString(0, top + row, CharWidth.substringByWidth(line, popupWidth), style);
			}
		}
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
		renderCompletion(buffer, width, transcriptHeight);
		var status = StatusBarText.lines(this.status, settings, connection, completionNote, statusHeight);
		for (int row = 0; row < statusHeight; row++) {
			int y = height - statusHeight + row;
			var style = completionNote != null && row == statusHeight - 1 ? noteStyle : statusStyle;
			buffer.fill(new Rect(0, y, width, 1), new Cell(" ", style));
			buffer.setString(0, y, CharWidth.substringByWidth(" " + status.get(row), width), style);
			if ((statusHeight == 1 || row == 1) && warningStyle != null && !(this.status.readOnly() && this.status.connected())) {
				buffer.setString(1, y, CharWidth.substringByWidth(StatusBarText.mode(this.status), width - 1), warningStyle);
			}
		}
	}
}
