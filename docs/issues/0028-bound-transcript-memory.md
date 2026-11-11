# 0028: Bound the Transcript memory

**What to build:** `Session` keeps every message in an unbounded list. Each Result block holds both the rendered text and the original `Result`. A long Interactive Session keeps all rows in memory, although the UI only needs the frozen text of older Results. Keep the original `Result` only where a later operation needs it (for example the latest one for `/dump` or `/save`), and bound the retained text. Found by code review (copilot-review.md, finding 6).

**Blocked by:** none (check with issue 0014 which Result it needs)

**Status:** done

**Parent:** `docs/prd/mvp-1.0.md`

- [x] Decide the retention rule (for example: only the newest Result keeps its rows; the Transcript keeps at most N lines or blocks) and write it in a `## Decision` section
- [x] Unit test: after many large Results, only the policy-defined number of Results still holds row data
- [x] Scrollback of older Results still shows their frozen text
- [x] Glossary terms are used (Transcript, Result)

## Decision

- Retain the newest 200 Message blocks. Discard older blocks, without changing history or Session metadata.
- Only the newest Result keeps row data. Earlier Result blocks keep their frozen rendered text until their block is discarded.
- Keep that newest Result even if its block is discarded after many info or input blocks. `/save` can use it until another Result replaces it. An empty Result also replaces it.
- Resume keeps its existing behavior: restore settings, connection values and history, not earlier Result rows. The current Transcript stays visible, subject to the same retention rule.
- This bounds block count, not bytes: one unusually large Result or input can still be large. The Row limit bounds normal Interactive Results. History retention is outside this issue.

## Verification

- `TranscriptMemoryTest`: 50 Results of 1000 rows retain one Result; earlier frozen text still renders. Another test verifies eviction at 200 blocks and preservation of the held Result.
- `SessionIT`: 110 inputs retain 200 blocks and one Result. Existing multi-Result assertions now check frozen text for older Results instead of discarded rows.
- Targeted verification passed: `TranscriptMemoryTest`, `SessionIT`, `OutputSwitchingIT`, `SchemaCommandsIT` and `SessionResumeIT`.
- TDD: the retention API test first failed compilation; the block-bound test observed 501 blocks instead of 200. The integration retention check passed immediately after wiring and is regression coverage.
- Human check: scroll up through older Results, change Format, and resize. Confirm retained text stays readable and the oldest blocks disappear after 200 blocks.
