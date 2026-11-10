# 0014: `/save` and `/dump`

**What to build:** `/save <file>` writes the Result held in memory in the current Format and Orientation. `/dump <file> [sql]` runs the given SQL, or the last Statement, and streams all rows to the file without the Row limit, in the current Format and Orientation.

**Blocked by:** 0010 Execution controls, 0012 Output switching and `/help`

**Status:** ready-for-agent

**Parent:** `docs/prd/mvp-1.0.md`

- [ ] `/save` warns when the held Result was truncated
- [ ] `/dump` streams without holding all rows in memory (integration test with many rows)
- [ ] `/dump` refuses Statements that are not queries (a query includes `WITH ... SELECT`)
- [ ] `/dump` with no SQL and no earlier Statement gives a clear error band
- [ ] Success shows an info band with row count and file name
