# 0027: One valid JSON document for several Results

**What to build:** With `--format json`, each Result is written as its own array, separated by a blank line. Repeated `-c` or several Statements give adjacent arrays. Typical tools cannot parse this as one document. Define the contract and implement it. Options: (a) one outer array that holds one array per Result; (b) JSON Lines (one array per line, no blank line). A single Result should keep its current output if possible. Found by code review (copilot-review.md, finding 5).

**Blocked by:** none

**Status:** open

**Parent:** `docs/prd/mvp-1.0.md`

- [ ] Decision recorded in this issue (`## Decision`), including the effect on a single Result
- [ ] Integration test: two Statements with `--format json` give output that a JSON parser accepts as specified
- [ ] Streaming of horizontal JSON stays row by row
- [ ] README or `--help` text describes the multi-Result output
