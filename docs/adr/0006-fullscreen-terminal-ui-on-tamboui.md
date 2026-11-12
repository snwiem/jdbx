---
status: accepted
---
# Build the interactive mode fullscreen on TamboUI

Interactive mode owns the whole terminal window (alternate screen) like the pi agent in its default fullscreen mode. The editor and the status bar stay fixed, and the app scrolls its own transcript. It is built on TamboUI, with JLine only as the terminal backend, and picocli for Slash commands. This supersedes ADR 0001. The JLine spike (ADR 0001) showed that an inline editor cannot stay fixed while the user scrolls, because the terminal owns the scrollback. After a taller resize (for example when a multiplexer pane is unsplit) it also leaves an empty gap between editor and status bar. Both problems disappear when the app owns the viewport.

## Considered Options

- **Inline on JLine** (ADR 0001): native scrollback and copy, but the editor scrolls away and the resize gap stays.
- **Fullscreen on plain JLine** (`Display`): same behavior, but we would write layout, diff rendering, table and scroll widgets ourselves.
- **Fullscreen on TamboUI**: chosen. It provides layout, diff rendering, table, scrollbar, mouse events and a test tool (Pilot).

## Consequences

- Native scrollback and plain mouse selection are lost. The app must handle wheel scrolling, selection and copy, or rely on `Shift`-drag with mouse capture on. The spike below checked mouse capture and OSC 52 at byte level. A human check confirmed that `Shift`-drag selects text and that `/copy` copies the last Result.
- TamboUI 0.7.0 has a multi-line `TextArea`. We add send and newline keys and the height logic. Legacy key parsing cannot tell `Ctrl-J` from `Enter`; use the Kitty protocol (active in Herdr) or a thin backend wrapper.
- TamboUI is young (0.7.0, "experimental"). The UI stays behind a thin boundary so plain JLine remains a fallback.
- The Session log in the terminal disappears on exit. Whether to print the transcript on exit is open. The spike showed that printing it to the normal screen after the runner closes works technically.

## Spike findings (issue 0019)

The spike confirmed the decision. Every capability works or has a small, known fix. `TextArea` removes the biggest risk, the multi-line editor. Herdr split and unsplit showed no stuck editor and no gap, which was the failure of the inline spike. Setup: TamboUI 0.7.0 (newest on Maven Central on 2026-11-10), modules `tamboui-core`, `-widgets`, `-tui`, `-jline3-backend` (pulls JLine 3.30.17), Java 25. The checks ran on a real pseudo-terminal with `pyte`, and in a throwaway Herdr 0.9.5 workspace. Bytes and screen content were judged. Feel and flicker were not.

| # | Capability | Result | Reason |
|---|-----------|--------|--------|
| 1 | Multi-line editor | works with caveat | `TextArea`, `TextAreaState` and `TextEditing` give multi-line editing, word wrap, cursor and word moves and paste. We add the send and newline keys and the height. The legacy key parser maps LF (`Ctrl-J`) and CR to `ENTER`. The fix is a `Backend` proxy that turns a bare LF into `CSI 106;5u`. With the Kitty protocol, `Ctrl-J`, `Shift-Enter` and `Alt-Enter` arrive as distinct keys. `TextArea` keeps a stale scroll after growing, so reset it with `scrollUp`. |
| 2 | Transcript viewport | works | Own code: a list of rows, a scroll offset, `buffer.fill` for full-width bands, `Scrollbar` for the position. The wheel and `PageUp`/`PageDown` scroll. Editor and status bar stay fixed. 300 bands render without delay. |
| 3 | Selection and copy | works (human check) | The app consumes only wheel events with mouse capture on. `Shift`-drag selects text natively. OSC 52 copy works (`ESC ] 52 ; c ; base64 BEL`). |
| 4 | Resize and multiplexer | works | Resizes between 30x8 and 120x60 and repeated Herdr split and unsplit rounds keep editor and status bar at the bottom, with no gap. |
| 5 | Keys | works | `Ctrl-C`, `Ctrl-D`, `Ctrl-Space` (NUL), `Shift-Tab` and `Alt-Enter` arrive. With Kitty also `Shift-Enter`, `Ctrl-Enter` and `Ctrl-J`. TamboUI pushes the Kitty flags without a query and pops them on exit. Herdr honors the protocol. |
| 6 | Bracketed paste | works with caveat | Needs `TuiConfig.bracketedPaste(true)`; the default is off. A paste then arrives as one `PasteEvent`. The proxy of row 1 must not rewrite LF inside a paste. |
| 7 | Locked editor | works | A background thread does the work, `tickRate(100ms)` redraws the timer, and `runner.runLater` reports back. `Ctrl-C` cancels and the app keeps running. Only `/quit` exits. |
| 8 | Exit behavior | works with caveat | `/quit`, `SIGTERM` and `SIGHUP` restore the screen. `SIGKILL` leaves mouse capture and the Kitty mode on (the user runs `reset`). A handler exception shows the default error screen first. |
| 9 | Testing | works with caveat | The `test-fixtures` jars give `TuiTestRunner` and `Pilot` for keys, mouse and resize. `TestBackend.rawOutput()` was nearly empty and `backend().size()` did not follow `resize`, so assert screens with `Frame.forTesting(Buffer)` and read cells. There is no wheel method: use `runner.dispatch(MouseEvent.scrollUp(x, y))`. |

Design notes from the spike:

- Use only `tamboui-core`, `-widgets`, `-tui` and `-jline3-backend`, with the two `test-fixtures` jars in test scope. Do not use the Toolkit DSL. It brings focus, CSS and elements that are not needed.
- Use `TuiRunner.create(TuiConfig)` with `alternateScreen`, `mouseCapture`, `bracketedPaste(true)`, `tickRate` and `keyboardProtocol(AUTO)`. Keep the `EventHandler` and the `Renderer` thin. Put logic in plain classes without TamboUI types.
- Keep a thin `Backend` wrapper for `Ctrl-J`, unless the Kitty protocol is required. Offer a rebindable second newline key (`Alt-Enter`).
- Use a custom `errorHandler` so that a crash does not wait for a `q` key press.

Risks:

- TamboUI states "experimental, APIs may change". Pin `0.7.0` and hide TamboUI behind our own classes.
- The JLine backend pulls JLine 3.30.17, not 4.x.
- The `TextArea` cursor is a styled cell, not the hardware cursor, so IME placement and a blinking cursor are not available.
- Wide characters (CJK, emoji) were not tested. The spike wrapped by `String.length`.

Hand check by the user on 2026-11-10: resize works, editing feels the same as with plain JLine, `Shift`-drag selects but does not copy automatically, `/copy` copies the last Result, and look and feel are good with no flicker. The spike was a full success.

The spike code and the findings note were deleted from the working tree. They are in git at commit `e4ef411`: `docs/issues/0019-tamboui-spike.md` and `spikes/0019-tamboui-spike/`.
