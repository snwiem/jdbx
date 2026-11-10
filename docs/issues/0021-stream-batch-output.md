# 0021: Stream Batch output

**What to build:** Batch mode writes rows to the output while it reads them, and does not hold a whole Result in memory. This makes large exports (and later `/dump`) possible without memory problems.

**Blocked by:** 0005 Vertical Orientation, 0020 Batch polish

**Status:** done

**Parent:** `docs/prd/mvp-1.0.md`

- [x] A Result of several million rows is written in Batch mode with a small, constant memory use (for example a test with a low heap limit)
- [x] The driver uses a fetch size with autocommit off for the read, so the server streams rows
- [x] `table` (which needs column widths) and vertical layouts either buffer with a documented limit, or use a bounded sample. Decide in the issue and document it. `csv`, `tsv`, `json` and `markdown` horizontal stream without buffering
- [x] `--limit N` still works and still reports `truncated at N rows`
- [x] The formatter interface gets a row-source abstraction that Interactive mode and `/dump` (issue 0014) can reuse

## Decision

- `csv`, `tsv`, `json` and horizontal `markdown` stream row by row.
- Horizontal `table` buffers the first 1000 rows (`TableFormatter.SAMPLE_ROWS`), takes the column widths from them, then streams the rest with these widths. A later wider value overflows its column. `max-width` still cuts values as before.
- Vertical layouts (all Formats) buffer the whole Result, because each line needs one value of every row. In practice `--limit` bounds this.
- Batch mode reads a Statement that starts with `select`, `with`, `values`, `table` or `show` with fetch size 1000 and autocommit off, then commits and restores autocommit. Other Statements keep autocommit on, because some (for example `vacuum`) cannot run in a transaction block.
- `RowSource` (columns and a one-pass row iterator) is the formatter input; `ResultFormatter.write(Result, Writer)` stays as a convenience. `JdbcRowSource` holds the JDBC reading.
