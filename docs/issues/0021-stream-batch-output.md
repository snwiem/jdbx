# 0021: Stream Batch output

**What to build:** Batch mode writes rows to the output while it reads them, and does not hold a whole Result in memory. This makes large exports (and later `/dump`) possible without memory problems.

**Blocked by:** 0005 Vertical Orientation, 0020 Batch polish

**Status:** ready-for-agent

**Parent:** `docs/prd/mvp-1.0.md`

- [ ] A Result of several million rows is written in Batch mode with a small, constant memory use (for example a test with a low heap limit)
- [ ] The driver uses a fetch size with autocommit off for the read, so the server streams rows
- [ ] `table` (which needs column widths) and vertical layouts either buffer with a documented limit, or use a bounded sample. Decide in the issue and document it. `csv`, `tsv`, `json` and `markdown` horizontal stream without buffering
- [ ] `--limit N` still works and still reports `truncated at N rows`
- [ ] The formatter interface gets a row-source abstraction that Interactive mode and `/dump` (issue 0014) can reuse
