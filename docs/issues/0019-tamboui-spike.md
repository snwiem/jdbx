# 0019: TamboUI spike

**What to build:** A throwaway prototype and a short findings note that prove or disprove that TamboUI can deliver the fullscreen interactive mode of ADR 0006 inside the Herdr multiplexer and a plain terminal: a Transcript of colored bands that scrolls while a multi-line editor and a status bar stay fixed.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

**Parent:** `docs/prd/mvp-1.0.md`

- [ ] Each capability below is tried and marked works / works with caveat / does not work in a `## Findings` section of this file
- [ ] The findings note recommends the widget and class structure for the real implementation (see `docs/assets/pi_layout.png`)
- [ ] If a capability does not work, the note proposes an alternative (for example plain JLine `Display`) and ADR 0006 is updated
- [ ] The prototype is marked throwaway and is not reused as production code

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
