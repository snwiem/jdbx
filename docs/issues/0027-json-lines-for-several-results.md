# 0027: JSON Lines for several Results in Batch mode

**What to build:** With `--format json`, Batch mode writes each Result as its own JSON array. Today a blank line separates them. Repeated `-c` or several Statements therefore give adjacent arrays with blank lines. Change the separator: one JSON array per line (JSON Lines), no blank line. Found by code review (copilot-review.md, finding 5).

**Blocked by:** none

**Status:** open

**Parent:** `docs/prd/mvp-1.0.md`

## Decision

- Batch stdout: JSON Lines. Each Result is one complete JSON array on one line; Results are separated by a newline only. Compact output (no pretty-printing across lines) is required for this.
- A single Result keeps its current content. Check whether a single Result is pretty-printed today. If yes, decide in this issue whether Batch JSON becomes compact (needed for JSON Lines) and document it.
- Interactive scrollback: each Statement shows its own block; no change.
- `/save` and `/dump` are not affected: `/save` writes one Result; `/dump` writes one valid document with an outer array (issue 0014).
- Horizontal JSON stays row by row (streaming).

## Acceptance criteria

- [ ] Integration test: two Statements with `--format json` give two lines; each line parses as a JSON array
- [ ] A single Result gives one valid JSON array as before (format unchanged, or the change is documented)
- [ ] Streaming of horizontal JSON stays row by row (the small-heap test still passes)
- [ ] `--help` and `README.md` say that several Results in `--format json` are JSON Lines, and that one Statement per call gives one JSON document
