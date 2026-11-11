# 0014: `/save` and `/dump`

**What to build:** `/save <file>` writes the Result held in memory in the current Format and Orientation. `/dump <file> [sql]` runs ALL Statements of the given SQL, or of the last submitted input, and streams every row of every Result to the file without the Row limit, in the current Format and Orientation.

**Blocked by:** 0010 Execution controls, 0012 Output switching and `/help`; relates to 0028 (which Result stays in memory for `/save`)

**Status:** done

**Parent:** `docs/prd/mvp-1.0.md`

## Decision

- `/save` writes the newest Result retained by issue 0028, including a schema Result. An empty Result replaces the earlier held Result. Info, errors and Dump do not replace it. The saved file uses the current Format, Orientation and value settings, not the frozen Transcript text. With `json` or `json-pretty` it is one valid JSON array.
- `/dump` can write several Results (several Statements). With `json` or `json-pretty` the file is one valid JSON document: an outer array of the Result arrays, written as a stream (`[`, the Results separated by commas, `]`), including for one Result. Pretty arrays have consistent nested indentation. Other Formats separate Results with one blank line, as Batch does. A CSV, TSV or Markdown file with several Results is not one table.
- `/dump` without SQL uses the last ordinary SQL input submitted in this running Session, including a failed input. Slash commands and explicit Dump SQL do not replace it. This is not the editor draft or a history lookup. Resume does not load SQL or Result rows from the earlier Session. The input is split like any input (`StatementSplitter`).
- Query check for `/dump`: if any Statement is not a query (`WITH ... SELECT` counts as a query), `/dump` refuses the whole input before it runs anything.
- `/dump` writes to a temporary file in the target directory and renames it only after all Statements succeed. On failure or cancel it removes the temporary file and shows an error band. No incomplete file is left, and an existing target file stays unchanged.
- Batch stdout uses concatenated JSON documents (issue 0027), not the Dump document rule.
- Query preflight accepts `SELECT`, query-only `WITH`, `VALUES`, `TABLE` and `SHOW`, including leading comments and parentheses. The shared SQL scanner ignores strings, quoted identifiers and comments. Conservatively reject unquoted `into`, `insert`, `update`, `delete` and `merge` anywhere, plus `FOR SHARE` and `FOR KEY SHARE`. This also rejects `FOR UPDATE` and `FOR NO KEY UPDATE`; quote identifiers with those names. This is not a full SQL parser.
- Each Dump Statement runs in a READ ONLY transaction and rolls back, even in a read-write Session. The Session mode stays unchanged. As with ADR 0007, functions affecting other connections or non-transactional state can still have side effects. This is a mistake guard, not a security boundary.
- Refuse Dump while a user transaction is open, before creating a temporary file. pgjdbc's transaction state catches SQL `BEGIN`, which does not change JDBC's autocommit flag. Dump must not commit or roll back the user's work.
- Reuse `StreamingRead` and `JdbcRowSource`: fetch 1000 rows per round trip, no Row limit, JDBC timeout for initial execution and transaction-local server timeout for later fetches. Ctrl-C also uses pgjdbc connection cancellation, because `Statement.cancel()` alone ignores cursor fetches. Cancellation targets only the active input.
- Horizontal Formatters stream as in Batch; horizontal `table` samples 1000 rows. Vertical Dump transposes into temporary column files in the target directory, then streams them into the final temporary file. This avoids Batch's whole-Result vertical buffer. It needs extra disk space and one open stream per column; a single large value can still require large memory.
- File names are literal paths in the current directory unless absolute. Both commands accept single or double quotes around a name with spaces; the matching quote cannot occur inside it. There is no shell expansion, and SQL after the file name is not shell-parsed. The target directory must already exist and support atomic replacement. Completed files use mode `600` on POSIX systems. Replacement does not preserve old permissions and replaces a target symlink rather than following it.
- Both Save and Dump use same-directory temporary files. Cancellation before publication leaves the target unchanged; cancellation after the atomic publication point cannot retract a completed file. Unexpected failures produce an error Message block and a trace in the Session log, not a failed future that ends Interactive mode.

## Acceptance criteria

- [x] `/save` warns when the held Result was truncated
- [x] `/save` with no Result, or an empty Result, shows an info box in the scrollback and writes no file
- [x] `/save` with `json` writes one valid JSON document
- [x] `/dump` runs all Statements of the given SQL, or of the last submitted input
- [x] `/dump` with `json` and several Statements writes one valid JSON document (outer array); a test parses the file
- [x] `/dump` streams without holding all rows in memory (integration test with many rows)
- [x] `/dump` refuses the whole input, and runs nothing, when any Statement is not a query (`WITH ... SELECT` is a query)
- [x] `/dump` with no SQL and no earlier input gives a clear error band
- [x] A failing or cancelled `/dump` leaves no incomplete file and keeps an existing target file unchanged
- [x] Success shows an info band with row count (all Results) and file name

## Verification

- Final `./mvnw -q verify` passed: **335 Unit tests and 241 Integration tests**, no failures, errors or skips. Log: `/tmp/dbui-0014-final-verified.log`.
- `SaveAndDumpIT` has 52 cases at the Session seam against Testcontainers PostgreSQL 17. It covers Save truncation, absent/empty Results, current Formats and Orientations, quoted paths, all Statements of explicit or earlier SQL, valid nested JSON and pretty JSON, preflight refusal without executing a sequence probe, error/cancel atomicity, cursor-fetch timeout/cancel, runtime logging and reuse, open transaction preservation, read-only preservation, missing directories and connection loss followed by reconnect.
- Seven child-process checks each dump 3 million rows under a 64 MB heap: horizontal CSV and JSON, JSON pretty with vertical selected, and vertical CSV, TSV, table and Markdown. Vertical cleanup and CSV multiline/quote/NULL preservation have separate checks.
- Both review blockers were independently reproduced and fixed: Dump no longer rolls back an open user transaction, and cancel now interrupts later cursor fetches. Follow-up review found no correctness blocker. A further review confirmed the separate cancel lock's lock order and that it cannot send a packet to a later input. Slow cancel socket I/O does not hold the Session monitor or block status/another Ctrl-C.
- Terminal glue did not change. `/help`, dispatch and Completion obtain the new commands from the shared registry. README documents file rules, retention, multi-Result formats, conservative query checking and vertical disk use.

### TDD evidence

- Observed red before implementation: Save and Dump were unknown commands; writing CTEs and SELECT INTO were accepted; the vertical 3-million-row CSV child exhausted its heap; a quoted Dump path failed; Dump accepted an open user transaction; an unexpected JDBC-boundary failure escaped through the future; a slow cursor-fetch cancel timed out; FOR SHARE variants were accepted; a paused cancel packet blocked status.
- Other cases added after shared implementation passed immediately and are regression coverage, not separate red-before-green claims. These include JSON parsing/indentation, several Format/Orientation cases, later-fetch timeout, connection loss/reconnect, read-only preservation and missing-directory behavior.
- The function-write backstop test initially used a temporary table, which PostgreSQL permits in READ ONLY transactions. The corrected permanent-table case passed after the backstop existed; it is not claimed as a separate red-before-green cycle.

## Limits and human checks

- No real-terminal or pseudo-terminal check was run for these two issues. Automated Session tests and the existing terminal tests passed; they do not judge colors, flicker or terminal key forwarding.
- In a real terminal and Herdr, run a truncated Result, change Format/Orientation, then Save. Check the warning and file. Scroll to earlier frozen Results and pass 200 blocks, resize, then Resume and check settings/history.
- Dump several queries in JSON pretty and vertical CSV. Cancel a long Dump with Ctrl-C, including after rows start streaming. Confirm the editor unlocks, the error/info band is readable, an existing target stays unchanged, and the next Statement works.
- Vertical Dump needs disk space for column spools plus the output and one file descriptor per column. Very wide Results can exceed OS file-descriptor limits; this fails without publishing the target. Large individual cells can still need large memory.
- A reviewer noted a theoretical retry-cancel overlap with transaction cleanup. No failure was reproduced. The current cancellation loop and cleanup tests pass; this is a residual risk, not an observed defect.
