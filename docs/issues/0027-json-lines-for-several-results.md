# 0027: Concatenated JSON for several Results in Batch mode

**What to build:** With `--format json`, Batch mode writes each Result as its own JSON array, with a blank line between them. Remove the blank line: Results follow each other directly, each in its current multi-line layout (`[`, one object per line, `]`). This is a stream of JSON documents that `jq` and similar tools read. Found by code review (copilot-review.md, finding 5).

**Blocked by:** none

**Status:** open

**Parent:** `docs/prd/mvp-1.0.md`

## Decision

- Batch stdout: concatenated JSON documents. Each Result keeps today's layout. Only the blank line between Results goes away. A single Result is unchanged.
- It is not strictly JSON Lines and not one document. Call it "concatenated JSON" in the docs. A user who needs one document runs one Statement per call.
- Interactive scrollback: each Statement shows its own block; no change.
- `/save` writes one Result (one valid array). `/dump` writes one valid document with an outer array (issue 0014).
- Horizontal JSON stays row by row (streaming).
- Full pretty-printing is a separate Format, `json-pretty` (issue 0032), not a setting.

## Acceptance criteria

- [ ] Integration test: two Statements with `--format json` give two arrays with no blank line; a test reads them with a JSON parser that accepts concatenated documents
- [ ] A single Result gives exactly the same output as before
- [ ] Streaming of horizontal JSON stays row by row (the small-heap test still passes)
- [ ] `--help` and `README.md` describe the output for several Results and the one-Statement-per-call advice
