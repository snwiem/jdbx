// THROWAWAY spike for issue 0019, capability 9: how far do TuiTestRunner and Pilot go for the editor and the Transcript?
import dev.tamboui.layout.Size;
import dev.tamboui.tui.event.*;
import dev.tamboui.tui.pilot.TuiTestRunner;
import dev.tamboui.tui.pilot.Pilot;

public final class PilotCheck {
    public static void main(String[] a) throws Exception {
        try (TuiTestRunner t = TuiTestRunner.runTest(Spike::handle, Spike::render, new Size(60, 20))) {
            Pilot p = t.pilot();
            p.press("h", "i");
            p.pause();
            System.out.println("editor text after typing: '" + Spike.editor.text() + "'");
            p.press(KeyCode.ENTER);
            p.pause();
            System.out.println("editor after Enter: '" + Spike.editor.text() + "', last band: " + Spike.session.bands.get(Spike.session.bands.size() - 1));
            p.press('j', KeyModifiers.CTRL);
            p.pause();
            System.out.println("editor after Ctrl-J: " + Spike.editor.text().replace("\n", "\\n"));
            p.press(KeyCode.PAGE_UP);
            p.pause();
            System.out.println("scroll after PageUp: " + Spike.transcript.scroll);
            t.runner().dispatch(MouseEvent.scrollDown(5, 5));
            p.pause();
            System.out.println("scroll after wheel down: " + Spike.transcript.scroll);
            p.resize(40, 10);
            p.pause();
            System.out.println("size now: " + t.backend().size());
            String raw = t.backend().rawOutput();
            System.out.println("rawOutput length " + raw.length() + ", contains status text: " + raw.contains("connected"));
            System.out.println("transcript ops: " + t.backend().transcript().size());
            p.quit();
        }
        // Screen check without a terminal: render into a Buffer with Frame.forTesting.
        for (int[] sz : new int[][]{{60, 20}, {40, 10}, {100, 50}}) {
            var buf = dev.tamboui.buffer.Buffer.empty(dev.tamboui.layout.Rect.of(sz[0], sz[1]));
            Spike.render(dev.tamboui.terminal.Frame.forTesting(buf));
            StringBuilder status = new StringBuilder();
            for (int x = 0; x < 12; x++) status.append(buf.get(x, sz[1] - 2).symbol());
            System.out.println(sz[0] + "x" + sz[1] + ": status row starts '" + status + "', rule above: " + buf.get(0, sz[1] - 3).symbol());

        }
    }
}
