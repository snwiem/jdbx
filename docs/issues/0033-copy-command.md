# 0033: `/copy` copies the last output to the clipboard

**What to build:** `/copy` puts the last output into the system clipboard, so that the user can paste a Result into a chat, ticket or editor without `/save` and a file. The idea comes from the pi agent, which has the same Slash command. Feature request from the user.

**Blocked by:** 0014 (shares the "held Result" and the formatter path with `/save`); issue 0028 decides which Result stays in memory

**Status:** needs-decision (see `## Open questions`)

**Parent:** `docs/prd/mvp-1.0.md` (new story; add it when the decisions are made)

## Background

- Issue 0019 (TamboUI spike) already checked an OSC 52 copy at byte level: `ESC ] 52 ; c ; <base64> BEL` through `backend().writeRaw(...)`. Whether Herdr and the outer terminal pass OSC 52 on is not checked (see its Findings, hand check 2).
- ADR 0006: with mouse capture on, native selection works only with `Shift`-drag. `/copy` is the app-side way to copy.
- WSL is the main environment. Clipboard tools differ there (`clip.exe`, `wl-copy`, `xclip`); OSC 52 needs no tool but needs terminal support.

## Proposed decisions (confirm before work)

1. **What is copied:** the last Result, in the current Format and Orientation, as `/save` would write it (same formatter, no rendering from the screen). If the last output was not a Result (an info or error band), copy its text.
2. **Mechanism:** OSC 52 through the TamboUI backend. No external tools and no new dependencies. If a later check shows it does not work in Herdr, add a fallback in a new issue.
3. **Size limit:** terminals cut OSC 52 payloads (often 75 to 100 KB, some less). `/copy` refuses an output above a fixed limit (default 64 KB, to be checked) and says to use `/save`. It never copies a partly cut text silently.
4. **Truncated Result:** it copies what is held and the info band says `copied N rows (truncated at LIMIT rows)`, like the `/save` warning.
5. **Feedback:** an info band `Copied N rows to the clipboard (Format)`. dbui cannot know whether the terminal accepted OSC 52, so the band says "sent to the terminal clipboard".
6. **No Result and no output:** an info box, no clipboard change (like `/save`).
7. **Argument:** none in the first version. A key binding is a later issue (with 0018).

## Open questions

- Q1: Is "last output" the last Result only, or also the last band or error text?
- Q2: Is OSC 52 enough, or do you also want a fallback to `clip.exe`/`wl-copy` on WSL?
- Q3: Should `/copy` use the current Format, or always a paste-friendly Format (for example tsv)? Tsv pastes into spreadsheets, markdown into tickets.

## Acceptance criteria

- [ ] Unit test: `/copy` builds the OSC 52 sequence for the formatted last Result (base64 of the exact text, current Format and Orientation)
- [ ] Unit test: no Result gives an info box and no OSC 52 output
- [ ] Unit test: output above the size limit is refused with a hint to use `/save`; nothing is sent
- [ ] Unit test: a truncated Result is copied and the info band names the Row limit
- [ ] The clipboard write goes through one thin seam (`ClipboardSink`), so tests need no terminal
- [ ] `/help` and `README.md` describe `/copy`, its size limit and the terminal requirement for OSC 52
- [ ] Manual check: paste after `/copy` in Herdr on WSL, in a plain terminal, and in Windows Terminal
