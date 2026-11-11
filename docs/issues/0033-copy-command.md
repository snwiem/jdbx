# 0033: `/copy` copies the last output to the clipboard

**What to build:** `/copy` puts the last output into the system clipboard, so that the user can paste a Result into a chat, ticket or editor without `/save` and a file. The idea comes from the pi agent, which has the same Slash command. Feature request from the user.

**Blocked by:** 0014 (shares the "held Result" and the formatter path with `/save`); issue 0028 decides which Result stays in memory

**Status:** ready-for-agent

**Parent:** `docs/prd/mvp-1.0.md` (new story; add it when the decisions are made)

## Background

- Issue 0019 (TamboUI spike) already checked an OSC 52 copy at byte level: `ESC ] 52 ; c ; <base64> BEL` through `backend().writeRaw(...)`. Whether Herdr and the outer terminal pass OSC 52 on is not checked (see its Findings, hand check 2).
- ADR 0006: with mouse capture on, native selection works only with `Shift`-drag. `/copy` is the app-side way to copy.
- WSL is the main environment. Clipboard tools differ there (`clip.exe`, `wl-copy`, `xclip`); OSC 52 needs no tool but needs terminal support.

## Decision (confirmed by the user)

1. **What is copied:** the last Result, in the current Format and Orientation, as `/save` would write it (same formatter, no rendering from the screen; what you see is what you copy). Only the Result of a query is copied. Info and error bands are never copied.
2. **Mechanism:** OSC 52 through the TamboUI backend only. No external tools and no new dependencies. If the manual check shows it does not work in Herdr, add a fallback in a new issue.
3. **Size limit (64 KB):** terminals cut OSC 52 payloads (often 75 to 100 KB, some less). `/copy` refuses an output above a fixed limit (64 KB, fixed; confirmed by the user) and says to use `/save`. It never copies a partly cut text silently.
4. **Truncated Result:** it copies what is held and the info band says `copied N rows (truncated at LIMIT rows)`, like the `/save` warning.
5. **Feedback:** an info band `Copied N rows to the clipboard (Format)`. dbui cannot know whether the terminal accepted OSC 52, so the band says "sent to the terminal clipboard".
6. **No Result:** an info box, no clipboard change (like `/save`). This includes a last Statement that gave no Result set.
7. **Argument:** none in the first version. A key binding is a later issue (with 0018).

## Acceptance criteria

- [ ] Unit test: `/copy` builds the OSC 52 sequence for the formatted last Result (base64 of the exact text, current Format and Orientation)
- [ ] Unit test: no Result gives an info box and no OSC 52 output
- [ ] Unit test: output above the size limit is refused with a hint to use `/save`; nothing is sent
- [ ] Unit test: a truncated Result is copied and the info band names the Row limit
- [ ] The clipboard write goes through one thin seam (`ClipboardSink`), so tests need no terminal
- [ ] `/help` and `README.md` describe `/copy`, its size limit and the terminal requirement for OSC 52
- [ ] Manual check: paste after `/copy` in Herdr on WSL, in a plain terminal, and in Windows Terminal
