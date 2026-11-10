# 0010: Execution controls

**What to build:** A running Statement can be limited, timed out and cancelled. dbui applies the Row limit (default 1000) through the JDBC statement, shows a truncated notice, and `/limit N` changes it. A timeout from settings applies and `/timeout` overrides it for the Session. `Ctrl-C` cancels a running Statement and returns to the prompt. The status bar shows a running timer, and the editor is locked while a Statement runs.

**Blocked by:** 0009 Errors, info blocks and Session log

**Status:** ready-for-agent

**Parent:** `docs/prd/mvp-1.0.md`

- [ ] Integration tests: 1500 rows give 1000 rows plus a truncated notice, and SQL text is not rewritten
- [ ] Integration test: `Ctrl-C` cancels a long `pg_sleep` and the Session still works
- [ ] Integration test: timeout stops a long Statement and shows an error band
- [ ] Status bar shows the Row limit and the last Result summary (rows, duration, truncated)
- [ ] Editor input is ignored while a Statement runs, except `Ctrl-C`
- [ ] Statement execution runs off the input thread so type-ahead can be added later
