# 0028: Bound the Transcript memory

**What to build:** `Session` keeps every message in an unbounded list. Each Result block holds both the rendered text and the original `Result`. A long Interactive Session keeps all rows in memory, although the UI only needs the frozen text of older Results. Keep the original `Result` only where a later operation needs it (for example the latest one for `/dump` or `/save`), and bound the retained text. Found by code review (copilot-review.md, finding 6).

**Blocked by:** none (check with issue 0014 which Result it needs)

**Status:** open

**Parent:** `docs/prd/mvp-1.0.md`

- [ ] Decide the retention rule (for example: only the newest Result keeps its rows; the Transcript keeps at most N lines or blocks) and write it in a `## Decision` section
- [ ] Unit test: after many large Results, only the policy-defined number of Results still holds row data
- [ ] Scrollback of older Results still shows their frozen text
- [ ] Glossary terms are used (Transcript, Result)
