# 0001: JLine spike

**What to build:** A throwaway prototype and a short findings note that prove or disprove that JLine can deliver the inline editor of ADR 0001: `Enter` sends, `Ctrl-J` inserts a newline, word and line navigation, a status bar that stays at the bottom while output scrolls, bracketed paste, a locked editor while work runs, and a hook for on-demand Completion.

**Blocked by:** None (can start immediately)

**Status:** done

**Parent:** `docs/prd/mvp-1.0.md`

- [x] Each capability above is tried and marked works / works with caveat / does not work in the findings note
- [x] The findings note lists the editor and status bar layout that the real implementation will use (see `docs/assets/pi_layout.png`)
- [x] If a capability does not work, the note proposes an alternative and ADR 0001 is updated
- [x] The prototype code is clearly marked throwaway and is not reused as production code

## Findings

Setup: JLine 4.6.0 (`org.jline:jline`), Java 25. Code: `spikes/0001-jline-spike/` (throwaway). Checked on a real pseudo-terminal (Python `pty`, `TERM=xterm-256color`, `LANG=C.UTF-8`) with the screen emulator `pyte`. Bytes and screen content were judged. Feel and flicker were not.

### Capabilities

| # | Capability | Result | Reason |
|---|-----------|--------|--------|
| 1 | `Enter` sends, `Ctrl-J` newline, editor grows | works | Custom widget writes `\n` into the buffer. Editor grows downward and the scroll region moves up. |
| 2 | Cursor movement | works with caveat | Up/Down between buffer lines, `Alt-B`/`Alt-F`, `Ctrl-Left`/`Right`, `Ctrl-A`/`Ctrl-E` and Home/End work. Arrow and Home/End keys arrive as `ESC O x` after JLine sets keypad mode; the default key maps handle that. Word jumps follow Emacs rules and cross line ends. |
| 3 | Status bar at the bottom, editor between two rules | works with caveat | `Status` keeps N lines at the bottom (scroll region). First line of the status is the lower rule; the upper rule is the first line of a two-line prompt. At start the editor sits at the top, so the app must print blank lines first to push it down to the status bar. |
| 4 | Bands in normal scrollback, no alternate screen | works | No `?1049h` sent. Lines that leave the region top land in the terminal scrollback (pyte history). `printAbove` with full-width padded `AttributedString`s gives bands with blank lines above and below. |
| 5 | Bracketed paste | works | Multi-line paste (`\n` or `\r` inside) stays one input. `BRACKETED_PASTE` is on by default. |
| 6 | Locked editor during work | works | Work runs on an executor thread. After `readLine` returns, the main thread reads raw keys with a 100 ms timeout, updates the status timer, ignores all but `Ctrl-C`, and cancels the future. Program keeps running. `Ctrl-C` and `Ctrl-D` at the idle prompt throw `UserInterruptException` / `EndOfFileException`; we catch both. Only `/quit` exits (exit code 0). |
| 7 | Completion on demand | works | Own widget calls `complete-word` on `Ctrl-Space` (NUL) and `Alt-/`. `Tab` is unbound. Typing never opens a list. Candidates print under the editor, above the status bar. Ctrl-Space may not arrive in some terminals (Windows terminal, some SSH setups); the fallback key covers that. |
| 8 | Changeable key bindings | works with caveat | Keys come from a `Map<action, sequence>` and are bound with `KeyMap.bind`. Caveat: the old key keeps its JLine default (for example `Ctrl-J` is still `accept-line`), so the app must `unbind` it. |
| 9 | Terminal resize | works with caveat | Status redraws at the new width and bottom. The upper rule is part of the prompt, so it is stale until we override `handleSignal` and call `setPrompt` (done in the spike). `pyte` does not reflow, so how the old text looks after a resize in a real terminal is untested by agent. |

### Recommended layout for the real implementation

- `LineReaderImpl` (subclass for `handleSignal`) is the editor. Do not use the JLine `Status` for the editor, only for the bottom lines.
- Prompt: `rule + "\n> "`. Secondary prompt (`SECONDARY_PROMPT_PATTERN`) is two spaces. Rule width comes from `terminal.getWidth()` and is rebuilt on `WINCH`.
- Status bar: `Status.getStatus(terminal).update(List<AttributedString>)` with `[lower rule, status line 1, ...]`. Call it from any thread; the spike calls it from the timer loop and from `WINCH`.
- Start: print about `height - 5` blank lines so the editor sits right above the status bar.
- Keys: a `Map<String,String>` of action to key sequence. Custom `Widget`s: `send` (`accept-line`), `newline` (`buffer.write("\n")`), `complete-on-demand` (`callWidget(COMPLETE_WORD)`). Bind in both `main` and `emacs` key maps. Unbind `Tab` and the old defaults.
- Output: `reader.printAbove(AttributedString)` for each band line (blank, text lines padded to terminal width with a background color, blank). It works while `readLine` runs and when it does not.
- Locked editor: do not call `readLine` while work runs. Run work on another thread. The input thread polls `terminal.reader().read(timeout)` in raw mode (`enterRawMode`), reads only `0x03`, restores the attributes at the end.
- Completion: normal JLine `Completer` plus `Option.AUTO_LIST`. This only matters for the explicit widget call.
- Quit: `/quit` is the only exit. Catch `UserInterruptException` and `EndOfFileException` at the idle prompt.

### Caveats and risks

- `jline` 4.x warns about restricted native access on Java 25. Start with `--enable-native-access=ALL-UNNAMED` (the `run.sh` does) or put it in the jar manifest (`Enable-Native-Access: ALL-UNNAMED`).
- In a non-UTF-8 locale JLine draws the rule `─` with ACS line drawing (`ESC ( 0`). This looks right in real terminals. Check with `LANG=C`.
- `Status` lines must be at most the terminal width; JLine pads and relies on terminal auto-wrap. Truncated lines end with `…`.
- `pyte` mis-scrolls the status rows with scroll regions. The test harness patches it. This is an emulator bug, not a JLine bug.
- After a resize that makes the terminal taller, a gap can appear between editor and status bar until the next prompt.
- Several scroll-region programs (status bar via `ESC [ r`) behave differently in multiplexers and in Windows Terminal. Test there.
- Input pasted while work runs is read and dropped by the locked editor, by design.

### Check by hand (untested by agent)

Run `./spikes/0001-jline-spike/run.sh` in the real terminal:

1. Flicker and feel: hold a key, type fast, run `/out 200`, run `/work`. The timer should not flicker.
2. Scrollback: after `/out 200`, scroll up with the mouse or `Shift-PgUp`. All bands must be there.
3. Resize: drag the window narrower, wider, taller, shorter. Status bar and rules must be at the bottom with the right width.
4. `Ctrl-Space` in your terminal. If nothing opens, use `Alt-/`.
5. `Ctrl-Left`/`Ctrl-Right`, Home/End and Backspace/Delete in your terminal and in Windows Terminal, if used.
6. Paste a multi-line SQL text from the system clipboard: it must stay one input.
7. `LANG=C ./spikes/0001-jline-spike/run.sh`: rules still look right.
8. `KEY_NEWLINE=$'\033\r' ./spikes/0001-jline-spike/run.sh`: `Alt-Enter` inserts a newline.
