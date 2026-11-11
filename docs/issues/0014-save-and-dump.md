# 0014: `/save` and `/dump`

**What to build:** `/save <file>` writes the Result held in memory in the current Format and Orientation. `/dump <file> [sql]` runs ALL Statements of the given SQL, or of the last submitted input, and streams every row of every Result to the file without the Row limit, in the current Format and Orientation.

**Blocked by:** 0010 Execution controls, 0012 Output switching and `/help`; relates to 0028 (which Result stays in memory for `/save`)

**Status:** ready-for-agent

**Parent:** `docs/prd/mvp-1.0.md`

## Decision

- `/save` writes one Result. With `--format json` it is one valid JSON array.
- `/dump` can write several Results (several Statements). With `json` the file is one valid JSON document: an outer array of the Result arrays, written as a stream (`[`, the Results separated by commas, `]`). Other Formats separate Results with one blank line, as Batch does. A CSV, TSV or Markdown file with several Results is not one table; document this.
- `/dump` without SQL uses the Statements of the last submitted input (the editor is cleared on send, so this is the last input, not the editor text). The input is split like any input (`StatementSplitter`).
- Query check for `/dump`: if any Statement is not a query (`WITH ... SELECT` counts as a query), `/dump` refuses the whole input before it runs anything.
- `/dump` writes to a temporary file in the target directory and renames it only after all Statements succeed. On failure or cancel it removes the temporary file and shows an error band. No incomplete file is left, and an existing target file stays unchanged.
- Batch stdout with several JSON Results is a separate decision (JSON Lines, issue 0027).

## Acceptance criteria

- [ ] `/save` warns when the held Result was truncated
- [ ] `/save` with no Result, or an empty Result, shows an info box in the scrollback and writes no file
- [ ] `/save` with `json` writes one valid JSON document
- [ ] `/dump` runs all Statements of the given SQL, or of the last submitted input
- [ ] `/dump` with `json` and several Statements writes one valid JSON document (outer array); a test parses the file
- [ ] `/dump` streams without holding all rows in memory (integration test with many rows)
- [ ] `/dump` refuses the whole input, and runs nothing, when any Statement is not a query (`WITH ... SELECT` is a query)
- [ ] `/dump` with no SQL and no earlier input gives a clear error band
- [ ] A failing or cancelled `/dump` leaves no incomplete file and keeps an existing target file unchanged
- [ ] Success shows an info band with row count (all Results) and file name
