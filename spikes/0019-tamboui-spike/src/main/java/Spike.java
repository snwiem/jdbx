// THROWAWAY spike for issue 0019 (TamboUI). Not production code. Glue only: the logic is in Session and Transcript.
import dev.tamboui.backend.jline3.JLineBackend;
import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Backend;
import dev.tamboui.terminal.Frame;
import dev.tamboui.terminal.KeyboardProtocol;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.event.*;
import dev.tamboui.widgets.input.TextArea;
import dev.tamboui.widgets.input.TextAreaState;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.List;

public final class Spike {
    static final Session session = new Session();
    static final Transcript transcript = new Transcript();
    static final TextAreaState editor = new TextAreaState();
    static final TextArea editorWidget = TextArea.builder().overflow(Overflow.WRAP_WORD).build();
    static final ScrollbarState sbState = new ScrollbarState();
    static final long start = System.currentTimeMillis();
    static volatile boolean working;
    static volatile boolean cancel;
    static volatile long workStart;
    static String lastKey = "-";
    static int lastTotal, lastView = 10;

    public static void main(String[] args) throws Exception {
        boolean dump = args.length > 0 && args[0].equals("--dump");
        boolean legacyKeys = System.getenv("KEYS") != null && System.getenv("KEYS").equals("legacy");
        Backend backend = System.getenv("NOFIX") != null ? new JLineBackend() : ctrlJFix(new JLineBackend());
        TuiConfig cfg = TuiConfig.builder()
                .backend(backend)
                .alternateScreen(true)
                .mouseCapture(true)
                .bracketedPaste(true)
                .hideCursor(true)
                .tickRate(Duration.ofMillis(100))
                .keyboardProtocol(legacyKeys ? KeyboardProtocol.LEGACY : KeyboardProtocol.AUTO)
                .build();
        try (TuiRunner runner = TuiRunner.create(cfg)) {
            runner.run((e, r) -> handle(e, r), Spike::render);
        }
        if (dump) dumpTranscript();
    }

    // ---- events -------------------------------------------------------------------------------
    static boolean handle(Event ev, TuiRunner r) {
        if (ev instanceof TickEvent) return true;
        if (ev instanceof MouseEvent m) {
            if (m.kind() == MouseEventKind.SCROLL_UP) transcript.by(3, lastTotal, lastView);
            else if (m.kind() == MouseEventKind.SCROLL_DOWN) transcript.by(-3, lastTotal, lastView);
            return true;
        }
        if (ev instanceof KeyEvent k) {
            lastKey = k.code() + (k.hasCtrl() ? "+Ctrl" : "") + (k.hasAlt() ? "+Alt" : "") + (k.hasShift() ? "+Shift" : "")
                    + (k.code() == KeyCode.CHAR ? " '" + k.string() + "'" : "") + (k.isRelease() ? " rel" : "");
            if (k.isRelease()) return true;
            if (k.isKey(KeyCode.PAGE_UP)) { transcript.by(lastView - 1, lastTotal, lastView); return true; }
            if (k.isKey(KeyCode.PAGE_DOWN)) { transcript.by(-(lastView - 1), lastTotal, lastView); return true; }
            if (working) { // locked editor: only Ctrl-C is read
                if (k.hasCtrl() && k.isCharIgnoreCase('c')) cancel = true;
                return true;
            }
            if (k.hasCtrl() && k.isCharIgnoreCase('c')) { editor.clear(); return true; } // idle: no exit
            if (k.hasCtrl() && k.isCharIgnoreCase('d')) { info("Ctrl-D does not exit. Type /quit."); return true; }
            if (k.hasCtrl() && k.isCharIgnoreCase('y')) { copy(r, session.lastResult()); return true; }
            boolean newline = (k.hasCtrl() && k.isCharIgnoreCase('j')) || (k.isKey(KeyCode.ENTER) && (k.hasAlt() || k.hasShift()));
            if (newline) { editor.insert('\n'); return true; }
            if (k.isKey(KeyCode.ENTER)) { send(r); return true; }
        }
        return TextEditing.handle(editor, ev, Overflow.WRAP_WORD);
    }

    static void info(String s) { session.bands.add(new Session.Band(Session.Kind.INFO, s)); transcript.scroll = 0; }

    static void copy(TuiRunner r, String text) {
        try {
            r.backend().writeRaw("\033]52;c;" + Base64.getEncoder().encodeToString(text.getBytes()) + "\007");
            r.backend().flush();
            info("Copied last result via OSC 52 (" + text.length() + " chars).");
        } catch (Exception e) { info("copy failed: " + e); }
    }

    static void send(TuiRunner r) {
        String text = editor.text();
        editor.clear();
        transcript.scroll = 0;
        switch (session.submit(text)) {
            case QUIT -> r.quit();
            case CRASH -> throw new IllegalStateException("deliberate crash");
            case COPY -> copy(r, session.lastResult());
            case WORK -> {
                working = true; cancel = false; workStart = System.currentTimeMillis();
                Thread t = new Thread(() -> {
                    long end = workStart + 3000;
                    while (System.currentTimeMillis() < end && !cancel) {
                        try { Thread.sleep(50); } catch (InterruptedException ignored) { }
                    }
                    boolean c = cancel;
                    r.runLater(() -> {
                        session.bands.add(c ? new Session.Band(Session.Kind.ERROR, "work cancelled")
                                : new Session.Band(Session.Kind.RESULT, "work done"));
                        working = false; transcript.scroll = 0;
                    });
                }, "work");
                t.setDaemon(true);
                t.start();
            }
            default -> { }
        }
    }

    // ---- rendering ----------------------------------------------------------------------------
    static final Style RULE = Style.EMPTY.fg(Color.indexed(244));
    static final Style STATUS = Style.EMPTY.fg(Color.indexed(252)).bg(Color.indexed(236));

    static Style band(Session.Kind k) {
        return switch (k) {
            case INPUT -> Style.EMPTY.fg(Color.indexed(255)).bg(Color.indexed(238));
            case RESULT -> Style.EMPTY;
            case INFO -> Style.EMPTY.fg(Color.indexed(228)).bg(Color.indexed(58));
            case ERROR -> Style.EMPTY.fg(Color.indexed(217)).bg(Color.indexed(52));
        };
    }

    static void fillRow(Buffer b, int x, int y, int w, String text, Style s) {
        b.fill(new Rect(x, y, w, 1), new Cell(" ", s));
        if (text != null) b.setString(x + 1, y, text, s);
    }

    static void render(Frame f) {
        int w = f.width(), h = f.height();
        Buffer buf = f.buffer();
        int maxEd = Math.max(1, Math.min(8, (h - 4) / 2));
        int edH = Math.max(1, Math.min(maxEd, editorWidget.preferredHeight(Math.max(1, w), editor)));
        if (editorWidget.preferredHeight(Math.max(1, w), editor) <= edH) editor.scrollUp(1000); // stale scroll after the editor grew
        int tH = Math.max(0, h - 2 - 2 - edH);
        // Transcript
        List<Transcript.Row> rows = transcript.rows(session.bands, w - 1);
        lastTotal = rows.size(); lastView = Math.max(1, tH);
        transcript.by(0, lastTotal, lastView);
        int first = Math.max(0, rows.size() - tH - transcript.scroll);
        for (int i = 0; i < tH; i++) {
            int idx = first + i;
            if (idx >= rows.size()) break;
            Transcript.Row row = rows.get(idx);
            fillRow(buf, 0, i, w - 1, row.text(), band(row.kind()));
        }
        if (tH > 0) {
            sbState.contentLength(rows.size()).viewportContentLength(tH).position(first);
            f.renderStatefulWidget(Scrollbar.vertical(), new Rect(w - 1, 0, 1, tH), sbState);
        }
        // rule, editor, rule
        int y = tH;
        buf.setString(0, y, "─".repeat(w), RULE);
        Rect ed = new Rect(0, y + 1, w, edH);
        if (working) {
            buf.setString(0, y + 1, "working... Ctrl-C cancels", Style.EMPTY.dim());
        } else {
            editorWidget.renderWithCursor(ed, buf, editor, f);
        }
        buf.setString(0, y + 1 + edH, "─".repeat(w), RULE);
        // status (2 lines)
        long up = (System.currentTimeMillis() - start) / 1000;
        String l1 = " ● connected  demo@localhost:5432/demo  mode: read-only  " + w + "x" + h;
        String l2 = String.format(" up %02d:%02d  %s  last key: %s", up / 60, up % 60,
                working ? "WORKING " + (System.currentTimeMillis() - workStart) / 100 / 10.0 + "s" : "idle", lastKey);
        fillRow(buf, 0, h - 2, w, null, STATUS); buf.setString(0, h - 2, trim(l1, w), STATUS);
        fillRow(buf, 0, h - 1, w, null, STATUS); buf.setString(0, h - 1, trim(l2, w), STATUS);
    }

    static String trim(String s, int w) { return s.length() <= w ? s : s.substring(0, Math.max(0, w)); }

    // ---- Ctrl-J: the legacy parser maps LF (0x0a) and CR to ENTER. Turn a bare LF into Kitty form CSI 106;5u (Ctrl+j).
    static Backend ctrlJFix(Backend delegate) {
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        StringBuilder window = new StringBuilder();
        boolean[] inPaste = {false};
        int[] prev = {0};
        return (Backend) Proxy.newProxyInstance(Backend.class.getClassLoader(), new Class<?>[]{Backend.class}, (p, m, a) -> {
            try {
                if (m.getName().equals("peek") && a != null && !queue.isEmpty()) return queue.peek();
                if (m.getName().equals("read") && a != null) {
                    if (!queue.isEmpty()) return queue.poll();
                    int c = (Integer) delegate.read((Integer) a[0]);
                    if (c >= 0) {
                        window.append((char) c);
                        if (window.length() > 8) window.delete(0, window.length() - 8);
                        String ws = window.toString();
                        if (ws.endsWith("\033[200~")) inPaste[0] = true;
                        if (ws.endsWith("\033[201~")) inPaste[0] = false;
                    }
                    int before = prev[0];
                    prev[0] = c;
                    if (c == 10 && before != 27 && !inPaste[0]) {
                        for (char ch : "[106;5u".toCharArray()) queue.add((int) ch);
                        return 27;
                    }
                    return c;
                }
                return m.invoke(delegate, a);
            } catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
        });
    }

    static void dumpTranscript() {
        StringBuilder sb = new StringBuilder();
        for (Session.Band b : session.bands) {
            String pre = switch (b.kind()) { case INPUT -> "\033[48;5;238m"; case INFO -> "\033[33m"; case ERROR -> "\033[31m"; default -> ""; };
            for (String line : b.text().split("\n")) sb.append(pre).append(line).append("\033[0m\n");
        }
        System.out.print(sb);
    }
}
