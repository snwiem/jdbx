// THROWAWAY spike for issue 0001 (JLine capabilities). Never reuse as production code.
import java.util.*;
import java.util.concurrent.*;
import org.jline.keymap.KeyMap;
import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.terminal.*;
import org.jline.utils.*;

public class Spike {
    // action -> key sequence. Change here (or via -Dkey.newline=...) to rebind.
    static final Map<String, String> KEYS = new LinkedHashMap<>();
    static {
        KEYS.put("send", "\r");                      // Enter
        KEYS.put("newline", System.getProperty("key.newline", KeyMap.ctrl('J')));
        KEYS.put("complete", KeyMap.ctrl(' '));      // Ctrl-Space = NUL
        KEYS.put("complete-fallback", KeyMap.alt('/'));
        KEYS.put("word-back", KeyMap.alt('b'));
        KEYS.put("word-fwd", KeyMap.alt('f'));
    }

    static Terminal terminal;
    static LineReaderImpl reader;
    static Status status;
    static volatile String statusText = "idle";

    public static void main(String[] a) throws Exception {
        terminal = TerminalBuilder.builder().system(true).build();   // no alternate screen
        status = Status.getStatus(terminal);
        Completer completer = (r, line, cands) -> {
            for (String k : List.of("SELECT", "FROM", "WHERE", "public.users", "public.orders"))
                cands.add(new Candidate(k));
        };
        reader = new LineReaderImpl(terminal, "spike", new HashMap<>()) {
            @Override protected void handleSignal(Terminal.Signal sig) {   // resize: rebuild rule at new width
                if (sig == Terminal.Signal.WINCH) { drawStatus(); setPrompt(rule() + "\n> "); }
                super.handleSignal(sig);
            }
        };
        reader.setCompleter(completer);
        reader.setParser(new DefaultParser().eofOnUnclosedQuote(false));
        reader.setVariable(LineReader.SECONDARY_PROMPT_PATTERN, "  ");
        reader.setOpt(LineReader.Option.BRACKETED_PASTE);
        reader.setOpt(LineReader.Option.AUTO_LIST);   // only affects explicit Completion; typing never calls it
        reader.unsetOpt(LineReader.Option.AUTO_MENU);
        bind();
        drawStatus();
        // Push the cursor to the bottom of the scroll region so the editor sits right above the status bar.
        for (int i = 0; i < terminal.getHeight() - 5; i++) terminal.writer().println();
        terminal.writer().flush();
        band("info", "dbui spike: Enter sends, Ctrl-J newline, /quit exits, /work runs 3 s, /out N prints N bands");
        while (true) {
            String line;
            try {
                line = reader.readLine(rule() + "\n> ");
            } catch (UserInterruptException e) { band("info", "Ctrl-C at idle ignored"); continue;
            } catch (EndOfFileException e) { band("info", "Ctrl-D at idle ignored"); continue; }
            if (line.equals("/quit")) break;
            band("input", line);
            if (line.startsWith("/out")) {
                int n = Integer.parseInt(line.substring(4).trim().isEmpty() ? "5" : line.substring(4).trim());
                for (int i = 1; i <= n; i++) band("result", "result row " + i);
            } else if (line.equals("/work")) work();
        }
        status.update(List.of());
        terminal.close();
    }

    static void bind() {
        Map<String, Widget> w = reader.getWidgets();
        w.put("send", () -> { reader.callWidget(LineReader.ACCEPT_LINE); return true; });
        w.put("newline", () -> { reader.getBuffer().write("\n"); return true; });
        w.put("complete-on-demand", () -> { reader.callWidget(LineReader.COMPLETE_WORD); return true; });
        Map<String, String> actionToWidget = Map.of("send", "send", "newline", "newline",
            "complete", "complete-on-demand", "complete-fallback", "complete-on-demand",
            "word-back", LineReader.BACKWARD_WORD, "word-fwd", LineReader.FORWARD_WORD);
        for (KeyMap<Binding> km : List.of(reader.getKeyMaps().get(LineReader.MAIN), reader.getKeyMaps().get(LineReader.EMACS))) {
            km.unbind("\t");   // typing/Tab never opens Completion
            km.bind(new Reference(LineReader.BACKWARD_WORD), "\033[1;5D");
            km.bind(new Reference(LineReader.FORWARD_WORD), "\033[1;5C");
            for (var e : KEYS.entrySet())
                km.bind(new Reference(actionToWidget.get(e.getKey())), e.getValue());
        }
    }

    static String rule() { return "\033[90m" + "─".repeat(Math.max(1, terminal.getWidth())) + "\033[0m"; }

    static void drawStatus() {
        AttributedStringBuilder b = new AttributedStringBuilder();
        b.append(new AttributedString("─".repeat(Math.max(1, terminal.getWidth())), AttributedStyle.DEFAULT.foreground(8)));
        status.update(List.of(b.toAttributedString(),
            new AttributedString(" " + statusText),
            new AttributedString(" pg://localhost/db  table  horizontal  limit 100  w=" + terminal.getWidth() + " h=" + terminal.getHeight())));
    }

    static void band(String kind, String text) {
        int bg = switch (kind) { case "input" -> 240; case "info" -> 3; case "error" -> 1; default -> 24; };
        int w = terminal.getWidth();
        List<AttributedString> out = new ArrayList<>();
        AttributedStyle st = AttributedStyle.DEFAULT.background(bg).foreground(kind.equals("info") ? 0 : 15);
        out.add(pad("", w, st));
        for (String l : text.split("\n", -1)) out.add(pad("  " + l, w, st));
        out.add(pad("", w, st));
        // printAbove is safe while readLine runs, and also works when it does not.
        for (AttributedString s : out) reader.printAbove(s);
    }

    static AttributedString pad(String s, int w, AttributedStyle st) {
        return new AttributedString(s + " ".repeat(Math.max(0, w - s.length())), st);
    }

    /** Locked editor: the input thread polls raw keys, ignores all but Ctrl-C; work runs on another thread. */
    static void work() throws Exception {
        ExecutorService ex = Executors.newSingleThreadExecutor();
        Future<?> f = ex.submit(() -> { Thread.sleep(3000); return null; });
        long t0 = System.currentTimeMillis();
        Attributes prev = terminal.enterRawMode();
        NonBlockingReader in = terminal.reader();
        int ignored = 0;
        boolean cancelled = false;
        try {
            while (!f.isDone()) {
                statusText = String.format("running %.1f s  (Ctrl-C cancels)  ignored keys: %d", (System.currentTimeMillis() - t0) / 1000.0, ignored);
                drawStatus();
                int c = in.read(100);
                if (c == 3) { f.cancel(true); cancelled = true; break; }
                if (c >= 0) ignored++;
            }
        } finally { terminal.setAttributes(prev); ex.shutdownNow(); }
        statusText = "idle";
        drawStatus();
        band(cancelled ? "error" : "result", cancelled ? "cancelled" : "work done");
    }
}
