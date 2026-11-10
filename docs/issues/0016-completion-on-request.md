# 0016: Completion on request

**What to build:** `Ctrl-Space` (configurable) opens Completion with SQL keywords and schema, table and column names. Completion never opens by itself. Suggestions come from the text near the cursor: after `FROM` or `JOIN` tables, after `table.` columns. No full SQL parsing.

**Blocked by:** 0019 TamboUI spike, 0015 Schema commands

**Status:** ready-for-agent

**Parent:** `docs/prd/mvp-1.0.md`

- [ ] Unit tests for the Completion engine without a terminal
- [ ] Nothing opens while the user types
- [ ] Quoted identifiers and mixed case are handled
- [ ] Completion works from the cache and does not query the database on each key press
- [ ] A few Pilot tests for the key binding
