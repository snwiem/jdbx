# 0019: TamboUI spike

**What to build:** A throwaway prototype and a short findings note that prove or disprove that TamboUI can deliver the fullscreen interactive mode of ADR 0006 inside the Herdr multiplexer and a plain terminal: a Transcript of colored bands that scrolls while a multi-line editor and a status bar stay fixed.

**Blocked by:** None (can start immediately)

**Status:** done

**Parent:** `docs/prd/mvp-1.0.md`

- [x] Each capability below is tried and marked works / works with caveat / does not work in a `## Findings` section of this file
- [x] The findings note recommends the widget and class structure for the real implementation (see `docs/assets/pi_layout.png`)
- [x] If a capability does not work, the note proposes an alternative (for example plain JLine `Display`) and ADR 0006 is updated
- [x] The prototype is marked throwaway and is not reused as production code

Capabilities to try:

1. Multi-line editor widget: `Enter` sends, `Ctrl-J` (and a rebindable key) inserts a newline, the editor grows and shrinks, cursor, word and line movement, `Up`/`Down` between lines.
2. Transcript viewport: colored full-width bands with padding. Mouse wheel and `PageUp`/`PageDown` scroll the Transcript while editor and status bar stay fixed. A scrollbar shows position.
3. Selection and copy: with mouse capture on, does `Shift`-drag give native selection in Herdr and in a plain terminal? Can the app copy a band or the last Result (OSC 52 or an app key)?
4. Resize and multiplexer: window resize, and Herdr split and unsplit (the failure seen with the first spike, see `docs/assets/prompt_too_big.png`) redraw the layout correctly.
5. Keys: does `Ctrl-J`, `Ctrl-Space`, `Ctrl-C`, `Ctrl-D`, `Shift-Tab` and `Alt-Enter` arrive? Is the Kitty keyboard protocol active in Herdr and does it matter?
6. Bracketed paste: a multi-line paste stays one input.
7. Locked editor: a 3 s background job, a running timer in the status bar, only `Ctrl-C` is read and cancels the job without exiting. `Ctrl-C` and `Ctrl-D` at the idle prompt do not exit. Only `/quit` exits.
8. Exit behavior: restoring the terminal cleanly on exit and on crash. Options to print the Transcript to the normal screen on exit.
9. Testing: how far does the TamboUI test tool (Pilot) go for key and screen tests of capabilities 1 and 2?

## Findings

Setup: TamboUI 0.7.0 (newest on Maven Central, checked 2026-11-10), modules `tamboui-core`, `-widgets`, `-tui`, `-jline3-backend` (pulls JLine 3.30.17), Java 25. Code: `spikes/0019-tamboui-spike/` (throwaway). Checked on a real pseudo-terminal (Python `pty`, `TERM=xterm-256color`) with the screen emulator `pyte`, and in a throwaway Herdr 0.9.5 workspace that the agent created and closed (`herdr pane run/read/split/close/send-keys`). Bytes and screen content were judged. Feel and flicker were not.

### Capabilities

| # | Capability | Result | Reason |
|---|-----------|--------|--------|
| 1 | Multi-line editor | works with caveat | TamboUI 0.7.0 already has `TextArea` + `TextAreaState` + `TextEditing` (multi-line, word wrap, cursor, word and line moves, Up/Down by display row, paste). We only add send/newline keys and the height. Caveat: the legacy key parser maps LF (`Ctrl-J`) and CR to `ENTER`. Fix in the spike: a `Backend` proxy turns a bare LF into `CSI 106;5u`. With the Kitty protocol, `Ctrl-J`, `Shift-Enter`, `Alt-Enter` arrive as distinct keys. The editor grows to 8 rows and shrinks. `TextArea` keeps a stale scroll after growing, so the spike resets it with `scrollUp`. `Alt-B`/`Alt-F` arrive as `CHAR+Alt`; word movement by them was not checked. `Ctrl-Left`, Home, End, Up, Down, Delete, Backspace work. |
| 2 | Transcript viewport | works | Own code: a list of rows, a scroll offset, `buffer.fill` for full-width bands, `Scrollbar` + `ScrollbarState` for position. SGR wheel reports (3 rows each) and `PageUp`/`PageDown` scroll. Editor and status bar stay fixed. 300 bands render without delay. |
| 3 | Selection and copy | untested by agent: needs human check | The app enables mouse capture (`?1000h ?1002h ?1006h ?1015h`) and consumes only wheel events. Native `Shift`-drag is a terminal feature and cannot be judged from bytes. OSC 52 copy works at byte level (`/copy`, `Ctrl-Y` emit `ESC ] 52 ; c ; base64 BEL` for the last Result). Whether Herdr and the outer terminal pass it on is unknown. |
| 4 | Resize and multiplexer | works | Resize in the pty to 80x24, 50x12, 30x8, 100x40, 60x24, 120x60 (smaller and larger, taller and wider): editor and status bar always at the bottom rows, no gap. In real Herdr: split down, unsplit, split right, unsplit, and 5 quick split/unsplit rounds. The layout returned to full size each time (226x60), no stuck editor. Real window drag was not tested. |
| 5 | Keys | works | Arrive as `CHAR+Ctrl 'c'`, `'d'`, `' '` (Ctrl-Space = NUL), `TAB+Shift`, `ENTER+Alt`. With Kitty also `ENTER+Shift`, `ENTER+Ctrl`, `CHAR+Ctrl 'j'`. TamboUI pushes the Kitty flags (`CSI > 5 u`) without a query and pops them on exit. Herdr honors the protocol: `herdr pane send-keys` `shift+enter`, `ctrl+enter`, `ctrl+j` arrive as distinct keys. Real keyboards through the outer terminal were not tested. |
| 6 | Bracketed paste | works with caveat | Needs `TuiConfig.bracketedPaste(true)`; the default is off. Then a paste with LF and CRLF arrives as one `PasteEvent` and stays one input. Caveat: the spike proxy must not rewrite LF inside a paste (it tracks `ESC[200~`/`ESC[201~`). |
| 7 | Locked editor | works | `/work` starts a thread, `tickRate(100ms)` redraws the timer ("WORKING 1.8s"). While locked, typed text, `Ctrl-D`, `Enter` are ignored. The wheel and `PageUp` still scroll (our choice). `Ctrl-C` cancels, the app keeps running. Idle `Ctrl-C` clears the editor, idle `Ctrl-D` shows an info band. Only `/quit` exits (exit code 0). Work reports back with `runner.runLater`. |
| 8 | Exit behavior | works with caveat | `/quit`, `SIGTERM`, `SIGHUP` restore the screen: `?1049l`, mouse off, paste off, Kitty pop, cursor on. `SIGKILL` and a `SIGINT` signal leave the terminal dirty (no restore). A handler exception shows the default error screen (stack trace, `q` quits), then the terminal is restored; exit code stays 0. Printing the Transcript to the normal screen after `run()` returns works (`--dump`, 1200 lines appear after `?1049l`). |
| 9 | Testing | works with caveat | `tamboui-tui` and `tamboui-core` test fixtures (classifier `test-fixtures`) give `TuiTestRunner.runTest(handler, renderer, size)` and `Pilot` for `press(String/KeyCode/char, modifiers)`, mouse, `resize`, `pause`. It works for plain `TuiRunner` apps, not only the Toolkit DSL. Key, paste-free editor and scroll state were asserted. Caveat: `TestBackend.rawOutput()` was nearly empty and `backend().size()` did not follow `resize`, so assert screens with `Frame.forTesting(Buffer)` and read cells (done at 60x20, 40x10, 100x50). No wheel method: use `runner.dispatch(MouseEvent.scrollUp(x, y))`. Mouse wheel bytes and Kitty bytes are not covered by Pilot. |

### Recommended structure for the real implementation

- Modules: `tamboui-core`, `tamboui-widgets`, `tamboui-tui`, `tamboui-jline3-backend`. No Toolkit DSL (it brings focus, CSS and elements we do not need). Test scope: the two `test-fixtures` jars.
- Use `TuiRunner.create(TuiConfig)` with `alternateScreen`, `mouseCapture`, `bracketedPaste(true)`, `tickRate`, `keyboardProtocol(AUTO)`. Write one `EventHandler` and one `Renderer` (the glue). Keep them thin.
- Plain classes (tested without a terminal, no TamboUI types): `Session` (Slash commands, bands, last Result), `Transcript` (wrapping to rows, scroll offset, clamp), `EditorKeys` (maps a key to send / newline / none; configurable keys), `StatusModel`.
- Editor: `TextArea` (`Overflow.WRAP_WORD`) with `TextAreaState` and `TextEditing.handle` for all keys except send and newline. Height = `preferredHeight` clamped to 1..max. Reset `scrollUp` when the text fits. Draw both rules ourselves.
- Transcript: flatten bands to rows at the current width on each frame (cheap for a few hundred bands; cache by width and band count if needed). Draw with `buffer.fill` and `setString`. Reserve the last column for `Scrollbar`.
- Layout: manual `Rect` arithmetic: Transcript (fill), rule, editor, rule, status (2 rows). `Layout` constraints would also work.
- Locked editor: a `locked` flag in the handler. Background work on its own thread or executor. Return to the UI thread with `runner.runLater`. Cancel with a flag or `Future.cancel`. Status timer from `TickEvent`.
- Ctrl-J: keep a thin `Backend` wrapper (as in the spike) or an upstream fix, unless we require the Kitty protocol. Offer a rebindable second key (`Alt-Enter`).
- Copy: `backend().writeRaw(OSC 52)` from an app key and `/copy`.
- Exit: `try-with-resources` on `TuiRunner`; optionally print the Transcript after it closes. Use a custom `errorHandler` (for example `RenderErrorHandlers.writeToFile` or `logAndQuit`) so a crash does not block on a `q` prompt.

### Caveats and risks

- API stability: TamboUI says "experimental, APIs may change". Pin `0.7.0` and import the BOM. Hide TamboUI behind our own thin classes.
- Java 25: start with `--enable-native-access=ALL-UNNAMED` (the `run.sh` does).
- The JLine backend pulls JLine 3.30.17, not 4.x. Check the clash with other JLine use (picocli shell, ADR 0001 code).
- The legacy parser cannot tell LF from CR (above). TamboUI also reads `ESC` + next byte with a 50 ms peek timeout.
- The cursor of `TextArea` is a styled cell, not the hardware cursor. IME placement and a blinking cursor are not available unless we call `Frame.setCursorPosition`.
- Fixed 100 ms ticks redraw every frame. The diff renderer sends only changed cells, so output is small. Check CPU by hand.
- Wide characters (CJK, emoji) in bands were not tested. The spike wraps by `String.length`.
- `SIGKILL` leaves mouse capture and the Kitty mode on. The user then runs `reset`.
- `pyte` has no Kitty or OSC 52 support; those were judged from bytes only.

### Decision recommendation

Stay with TamboUI. Every capability works or has a small, known fix. `TextArea` removes the biggest risk (the multi-line editor). Herdr split and unsplit showed no stuck editor and no gap, which was the failure of the inline spike. The fallback (plain JLine `Display`) is not needed now. Keep the UI boundary thin so it stays possible. Reconsider if the hand checks below show a blocking problem with mouse selection or OSC 52 in Herdr. ADR 0006 gets a short note.

### Check by hand (untested by agent)

Run `./spikes/0019-tamboui-spike/run.sh` in your real terminal, once directly and once inside Herdr.

1. Selection: hold `Shift` and drag over a band. Text must highlight and copy with the terminal's copy key. Do it in Herdr and in the plain terminal. If it fails, note which terminal.
2. OSC 52: type `/copy`, then paste elsewhere. The text `id | name`, `300 | row 300` should appear. Try also `Ctrl-Y`.
3. Mouse wheel: scroll up and down in the Transcript. Scrollbar thumb moves, editor and status bar stay.
4. Real resize: drag the window narrower, wider, taller, shorter. Then split the Herdr pane and unsplit it with the Herdr keys. Editor and status bar must stay at the bottom.
5. Keys on your keyboard: `Ctrl-J`, `Shift-Enter`, `Alt-Enter` (newline), `Ctrl-Space`, `Shift-Tab`, `Ctrl-Left/Right`, `Alt-B/F`, Home/End. The status bar shows the last key. Repeat with `KEYS=legacy ./spikes/0019-tamboui-spike/run.sh` and `NOFIX=1 ...`.
6. Paste a multi-line SQL text from the clipboard: it must stay one input.
7. Flicker and feel: hold a key, type fast, run `/work`. The timer must not flicker.
8. Exit: `/quit` returns a clean shell. `./run.sh --dump` prints the Transcript to the normal screen. `/crash` shows the error screen; `q` restores the shell. In another pane run `kill -9 <java pid>`, then `reset`.
9. `LANG=C ./spikes/0019-tamboui-spike/run.sh`: rules and scrollbar still look right.
