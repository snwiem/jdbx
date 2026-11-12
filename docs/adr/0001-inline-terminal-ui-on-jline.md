---
status: superseded by ADR-0006
---
# Build the interactive mode inline on JLine

Interactive mode renders into the normal terminal scrollback, with a multi-line editor and a status bar at the bottom, like the pi agent. We build it directly on JLine, with picocli for Slash commands. A full-screen TUI was rejected: it removes native scrollback, copy and paste, and needs custom widgets.

## Considered Options

- **TamboUI** (full-screen widgets): its inline mode uses the alternate screen by default, which removes native scrollback.
- **Spring Shell**: its TUI layer is experimental, and its command model would compete with picocli.
- **Plain JLine plus picocli**: chosen. Full control of editor keys, status bar and scrollback.

## Consequences

Features that need panes (a result viewer, a schema tree) cannot be added inline. A temporary full-screen pager may come later. JLine behavior must be confirmed with a spike before the main build.

## Spike findings (issue 0001)

JLine 4.6.0 delivers the inline editor. All nine capabilities work or work with caveats; none fails. The decision stood until the spike showed the limits recorded in ADR 0006. Setup: JLine 4.6.0, Java 25, a real pseudo-terminal (Python `pty`, `TERM=xterm-256color`) with the screen emulator `pyte`. Bytes and screen content were judged. Feel and flicker were not.

| # | Capability | Result | Reason |
|---|-----------|--------|--------|
| 1 | `Enter` sends, `Ctrl-J` newline, editor grows | works | A custom widget writes `\n` into the buffer. The editor grows downward and the scroll region moves up. |
| 2 | Cursor movement | works with caveat | Up/Down, `Alt-B`/`Alt-F`, `Ctrl-Left`/`Right`, `Ctrl-A`/`Ctrl-E`, Home/End work. Arrow and Home/End keys arrive as `ESC O x` after JLine sets keypad mode; the default key maps handle that. |
| 3 | Status bar at the bottom, editor between two rules | works with caveat | JLine `Status` keeps N lines at the bottom (scroll region). The upper rule is the first line of a two-line prompt. At start the editor sits at the top, so the app must print blank lines to push it down. |
| 4 | Bands in the normal scrollback, no alternate screen | works | No `?1049h` is sent. Lines that leave the region land in the terminal scrollback. |
| 5 | Bracketed paste | works | A multi-line paste stays one input. |
| 6 | Locked editor during work | works | Work runs on an executor thread. The main thread reads raw keys with a 100 ms timeout, updates the status timer, ignores all but `Ctrl-C` and cancels the work. `Ctrl-C` and `Ctrl-D` at the idle prompt throw `UserInterruptException` / `EndOfFileException`; both are caught. Only `/quit` exits. |
| 7 | Completion on demand | works | An own widget calls `complete-word` on `Ctrl-Space` (NUL) and `Alt-/`. `Tab` is unbound. Ctrl-Space may not arrive in some terminals (Windows Terminal, some SSH setups), so a fallback key is needed. |
| 8 | Changeable key bindings | works with caveat | Keys bind through `KeyMap.bind`. The old key keeps its JLine default (for example `Ctrl-J` stays `accept-line`), so the app must `unbind` it. |
| 9 | Terminal resize | works with caveat | The status redraws at the new width and bottom. The upper rule is part of the prompt, so it is stale until `handleSignal` is overridden and `setPrompt` is called. |

Further caveats and risks:

- JLine 4.x warns about restricted native access on Java 25. Start with `--enable-native-access=ALL-UNNAMED` or put `Enable-Native-Access: ALL-UNNAMED` in the jar manifest.
- In a non-UTF-8 locale JLine draws the rule `─` with ACS line drawing.
- After a resize that makes the terminal taller, a gap can appear between editor and status bar until the next prompt. This is the failure that led to ADR 0006.
- Scroll-region status bars behave differently in multiplexers and in Windows Terminal.
- Text pasted while work runs is read and dropped by the locked editor, by design.

The spike code and the findings note were deleted from the working tree. They are in git at commit `e4ef411`: `docs/issues/0001-jline-spike.md` and `spikes/0001-jline-spike/`.
