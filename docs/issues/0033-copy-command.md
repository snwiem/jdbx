# 0033: `/copy` sends the held query Result to the clipboard

**What to build:** `/copy` sends the held query Result to the terminal clipboard, so that the user can paste a Result into a chat, ticket or editor without `/save` and a file. The idea comes from the pi agent, which has the same Slash command. Feature request from the user.

**Blocked by:** 0014 (shares the "held Result" and the formatter path with `/save`); issue 0028 decides which Result stays in memory

**Status:** ready-for-manual-check

**Parent:** `docs/prd/mvp-1.0.md`, story 88

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

## Decision (implementation details)

- The fixed 64 KB means 65,536 bytes of the complete formatted UTF-8 text, including headers and final newlines, before base64 encoding. Exactly 65,536 bytes is allowed. No OSC 52 sequence is sent above that size.
- Query Results are Results returned by ordinary SQL execution, not schema Slash commands. A successful Statement without a Result disables `/copy` but does not discard the Result used by Save. An empty Result replaces the earlier Result and sends nothing, like Save. Info, errors and Dump do not replace the held Result or become clipboard content.
- Use the existing Format registry with `FormatOptions.from(settings)` and the current Orientation, exactly like `ResultFiles.save`. Do not copy frozen Transcript text or add row-count/truncation text to the clipboard payload. The original truncated Row limit equals the held row count, so later `/limit` changes do not alter the warning.
- `CopyCommand.execute` is the plain Unit-test seam. `ClipboardSink.write` accepts one complete OSC 52 sequence and owns writing and flushing it. Session dispatch is synchronous so the backend write runs on the terminal input thread. The adapter only calls `runner.backend().writeRaw` and `flush`. No clipboard tools, fallback, dependencies or key bindings are added. Existing glossary terms suffice.

## Acceptance criteria

- [x] Unit test: `/copy` builds the OSC 52 sequence for the formatted last Result (base64 of the exact text, current Format and Orientation)
- [x] Unit test: no Result gives an info box and no OSC 52 output
- [x] Unit test: output above the size limit is refused with a hint to use `/save`; nothing is sent
- [x] Unit test: a truncated Result is copied and the info band names the Row limit
- [x] The clipboard write goes through one thin seam (`ClipboardSink`), so tests need no terminal
- [x] `/help` and `README.md` describe `/copy`, its size limit and the terminal requirement for OSC 52
- [ ] Manual check: paste after `/copy` in Herdr on WSL, in a plain terminal, and in Windows Terminal


## Verification

- Full `./mvnw -q verify` passed: **408 Unit tests and 251 Integration tests**, no failures, errors or skips. Log: `/tmp/dbui-0033-verify.log`. `git diff --check` passed.
- Focused `CopyCommandTest`, `CopyCommandSessionTest` and `CopyCommandIT` checks passed: 21 Unit cases and 5 PostgreSQL Integration cases. The tests capture the complete OSC 52 sequence through `ClipboardSink`; they do not prove OS clipboard acceptance.
- A real pseudo-terminal smoke check of the rebuilt jar passed: ordinary SQL, later CSV and vertical settings, `/copy`, and `/quit`. The real TamboUI backend emitted exactly one complete OSC 52 sequence for `id,7\nname,Ä猫\n` and restored the alternate screen on exit. Script: `/tmp/dbui-0033-pty-smoke.py`; byte artifact: `/tmp/dbui-0033-pty-output.bin`. The temporary PostgreSQL container and app were closed. This is byte-level automation, not the human paste check.
- The terminal adapter has only the backend `writeRaw` and `flush` calls. `EditorKeys`, `ConfigLoader` and `SlashCommandRegistry` are unchanged, avoiding the issue 0018 worker's files.

### TDD evidence

| New test | Observed red before green? |
|---|---|
| `CopyCommandTest.copiesTheExactCurrentFormatAndOrientationAsUtf8Osc52` | Yes, compile-red: `CopyCommand` did not exist. The first implementation then passed the exact Unicode UTF-8/base64 sequence assertion. |
| `CopyCommandTest.noOrEmptyResultGivesInfoWithoutChangingTheClipboard` (2 cases) | Yes: null caused an exception; empty rows incorrectly sent OSC 52. |
| `CopyCommandTest.refusesMoreThan64KbOfFormattedUtf8BytesWithoutSendingAnything` | Yes: 65,537 UTF-8 bytes were accepted. |
| `CopyCommandTest.copiesATruncatedResultAndNamesItsOriginalRowLimit` | Yes: feedback omitted truncation and the Row limit. |
| `CopyCommandTest.acceptsExactly64KbBeforeBase64Expansion` | No; passed immediately as a regression guard. |
| `CopyCommandTest.aClipboardWriteFailureGivesAnErrorInsteadOfSuccess` | No; passed immediately as a regression guard. |
| `CopyCommandTest.everyFormatAndOrientationUsesTheSaveTextNotScreenText` (12 cases) | No; passed immediately as regression guards for all Formats and both Orientations. Expected text is literal, not generated by the formatter under test. |
| `CopyCommandSessionTest.copyWithoutAQueryResultNeedsNoConnectionAndDoesNotWriteToTheClipboard` | Yes, first compile-red for sink installation, then behavioral red because `/copy` was unknown. |
| `CopyCommandSessionTest.copyAcceptsNoArgumentAndHelpExplainsItsTerminalAndSizeRequirements` | No; passed immediately as a regression guard for registry behavior and help. |
| `CopyCommandIT.aLastStatementWithoutAResultRefusesCopyButDoesNotChangeSaveRetention` | Yes: `/copy` sent the older Result after a no-Result Statement. |
| `CopyCommandIT.schemaResultsDoNotCountAsQueryResultsForCopy` | Yes: `/copy` sent a schema Result. |
| `CopyCommandIT.copyUsesTheHeldQueryWithCurrentOutputSettingsAndItsOriginalRowLimitLikeSave` | No; passed immediately as a regression guard, including a later `/limit` change. |
| `CopyCommandIT.infoErrorsDumpAndTranscriptEvictionNeverBecomeClipboardContent` | No; passed immediately as a regression guard. |
| `CopyCommandIT.anEmptyQueryReplacesTheEarlierResultAndANewQueryEnablesCopyAgain` | No; passed immediately as a regression guard. |

## Human check (not run)

Repeat in Herdr on WSL, a plain terminal and Windows Terminal:

1. Run `select 7 as id, 'Ä猫' as name;`, change `/format csv`, then `/copy`. Paste into an editor and compare it with `/save` of the same Result. Repeat with vertical Orientation and JSON pretty.
2. Set `/limit 2`, run `select n from generate_series(1,3) n;`, then `/limit 10` and `/copy`. Paste must contain two rows; the info Message block must still name a Row limit of 2.
3. Run `/copy` before any Result, after an empty query, and after a Statement without a Result. It must show info without changing the existing clipboard.
4. In CSV Format, run `select repeat('x', 70000) as value;`, then `/copy`. It must refuse, mention `/save`, and leave the clipboard unchanged.

If paste fails in Herdr or a terminal, record which terminal and its OSC 52 settings. Do not mark the manual criterion done from byte-level tests. A fallback belongs in a separate issue.
