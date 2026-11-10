# 0010: Execution controls

**What to build:** A running Statement can be limited, timed out and cancelled. dbui applies the Row limit (default 1000) through the JDBC statement, shows a truncated notice, and `/limit N` changes it. A timeout from settings applies and `/timeout` overrides it for the Session. `Ctrl-C` cancels a running Statement and returns to the prompt. The status bar shows a running timer, and the editor is locked while a Statement runs.

**Blocked by:** 0009 Errors, info blocks and Session log

**Status:** done

**Parent:** `docs/prd/mvp-1.0.md`

- [x] Integration tests: 1500 rows give 1000 rows plus a truncated notice, and SQL text is not rewritten
- [x] Integration test: `Ctrl-C` cancels a long `pg_sleep` and the Session still works
- [x] Integration test: timeout stops a long Statement and shows an error band
- [x] Status bar shows the Row limit and the last Result summary (rows, duration, truncated)
- [x] Editor input is ignored while a Statement runs, except `Ctrl-C`
- [x] Statement execution runs off the input thread so type-ahead can be added later
- [x] Interactive mode reads column values the same way as Batch mode (a shared reader): a plain `timestamp` is shown as stored and never shifted, `timestamptz` uses the configured time zone, and the formatter options from `config.yaml` (NULL marker, multi-line marker, maximum width, time zone) apply (finding of issue 0020)
- [x] A `Frame.forTesting` regression test renders all five specified ESC/BEL payloads and checks rendered cells, cells sent to the terminal backend, and raw application output
- [x] Interactive mode uses `StatementSplitter` for several Statements in one input, runs them in order, stops at the first error, and gives each Result its own Message block

## Verification

- `./mvnw -q verify` passes, including the existing Batch mode tests.
- `SessionIT` covers the default and configured Row limit, `/limit`, unchanged SQL (`current_query()`), exact-limit versus truncated Results, timeout settings and `/timeout off`, invalid commands, cancellation and reuse, immediate cancellation, ordered Statements and stopping at the first error.
- Unit tests cover configured value formatting and Status bar text. Pilot and `Frame.forTesting` tests cover the locked editor, Ctrl-C and the screen. The ESC/BEL test is a characterization test: TamboUI already removes these control characters from cells. No renderer fix was necessary. Legitimate terminal protocol escapes are not SQL data; the test disables Kitty negotiation when checking raw application output.
- A Python `pty` + `pyte` check (virtual environment and artifacts under `/tmp`, not the repository) ran the packaged application against the compose PostgreSQL. It checked config values, local timestamp preservation, the configured time zone and markers, width cutting, the running timer, ignored input while locked, Ctrl-C recovery, `/limit 5`, truncation, timeout, ordered Statements, stopping on error, all five malicious control strings, and terminal restoration after `/quit`.

## Implementation notes

- `ExecutionControls` handles `/limit` and `/timeout` through the Session's Settings, without keeping a separate copy. With a `SessionContext`, `SessionState` owns these Settings and persists each successful change immediately. Without a context, the Session keeps the Settings in memory for tests.
- Batch mode streams through `JdbcRowSource`, which calls the shared conversion method `JdbcResultReader.readValue`. Interactive mode uses `JdbcResultReader.read` to buffer this same row source up to the Row limit and probe for truncation. `FormatOptions.from(Settings)` supplies the same value formatting options. Interactive mode remains horizontal `table`; startup Format and Orientation selection belongs to issue 0012.
- A read-only review found pgjdbc can ignore cancellation immediately before execution. A deterministic failing Integration test now pauses at that JDBC boundary over real PostgreSQL. Cancellation retries on a virtual thread until that Statement completes; it never targets a later input. Closing the Session also requests cancellation. Invalid configured Row limits and negative timeouts are rejected before execution.
- Red-before-green: behavior tests and review fixes failed before their production changes. Some new API tests first failed at compilation. The terminal check exposed a missing timer redraw before the tick handler changed. The invalid Slash command, first-error stop, immediate-cancel and ESC/BEL tests characterized behavior that was already green; parsing validation was implemented with the command handlers before those extra cases were added. No artificial failing step was introduced for them.

## Integration with main (0015, 0017 and 0021)

- Production construction uses `SessionContext`. Its Settings include the effective read-only value after command-line and Profile precedence. `/resume` restores Row limit, timeout and read-only into the Settings that execution controls read. Invalid stored execution values give an error band before a fork or connection change.
- Schema commands keep their own dispatcher and asynchronous execution. Resume recreates the schema cache for the current connection and clears the previous Session's Result summary. Each new SQL Statement gets the current timeout and Row limit. Initial and resumed connections receive the current JDBC read-only flag. Resume applies that flag before creating a fork; a rejected read-only change (for example during an open transaction) leaves the current Session unchanged. pgjdbc enforcement with autocommit remains the existing limitation noted in issue 0017.
- Interactive mode keeps `createOrDisabled`, the log-file move on Resume, history navigation and reverse search. Rendering reads the current execution status for the Row limit, timer and last Result summary. Ctrl-C first cancels a running Statement, otherwise cancels reverse search and restores its draft, otherwise clears idle input.
- The execution, schema, Resume and quit Slash commands coexist. History keeps the existing issue 0017 rules: `/quit` and `/resume` are excluded; the other commands, including unknown commands, follow normal filtering.

### Merge verification

- `./mvnw -q verify`: 158 Unit tests and 106 Integration tests passed, with no failures, errors or skips. This includes both 3-million-row Batch tests with a 64 MB heap.
- New Integration tests cover changes through `/limit 3` and `/timeout 7`, immediate metadata updates, truncation after Resume, restored history and `/timeout off`. Another test reconnects through a Profile and checks the new connection's Row limit, timeout, JDBC read-only flag and fresh schema cache. All Slash-command families share the history test. Resume also rejects read-only changes during a transaction without creating a fork, and clears the previous Session's Result summary after success.
- Red-before-green: the persistence test first observed `row-limit: 1000` after `/limit 3`. The screen test first observed reverse search still open after Ctrl-C. Three invalid-metadata cases first resumed successfully instead of showing an error band. Review tests first reproduced misaligned in-memory tables after row 1000, successful Resume after a rejected read-only change, an old Result summary after Resume, and a slow cursor fetch completing despite `timeout: 1`. A packaged Batch check also reproduced a configured timeout replacing a shorter server timeout; the new regression keeps the shorter value. All now pass. The reconnect/cache and combined-command tests characterized behavior that was already green after conflict resolution.
- Review kept in-memory `Result` tables aligned using every buffered row, while streamed Batch tables still sample 1000 rows. A transaction-local PostgreSQL `statement_timeout` also protects later cursor fetches, which pgjdbc's JDBC timer does not cover. This server timeout applies per fetch, not as a total export deadline, and uses PostgreSQL's maximum of 2,147,483,647 milliseconds for larger configured values. A shorter existing server timeout takes precedence. The JDBC timeout still applies to initial execution. The existing last-Result semantics after SQL errors and non-Result Statements remain unchanged.
- A packaged-app Python `pty` + `pyte` check passed against the compose PostgreSQL, with its virtual environment and artifacts under `/tmp`. It checked formatter Settings, `/limit 3` and truncation, `/timeout 2` with `pg_sleep(30)`, the running timer, locked input, Ctrl-C during execution/search/idle, Up, Ctrl-R accept/cancel, restart, `/resume 1`, restored Settings/history, schema commands, unknown commands, an unusable state directory, command-line read-only metadata, `/quit` and terminal restoration. The original Session directory stayed byte-for-byte unchanged after Resume. Profile reconnection, invalid stored values, read-only Resume during a transaction, late-fetch timeout and the low-heap Batch cases were checked by Integration tests, not by this pseudo-terminal check. The table-width regression was checked by a Unit test.
- Existing limits outside this merge remain: the password dialog is deferred to issue 0013; schema output Format and Orientation selection is deferred to issue 0012. Ctrl-C cancels SQL Statements, not JDBC metadata calls or connection establishment. Invalid startup execution Settings are rejected, but their error does not yet name `config.yaml`.

## Human check still required

Run in the real terminal and in Herdr. Judge band colors and padding, timer readability and flicker, editor locking and the return to usable input after Ctrl-C. Repeat after resize. Automated checks verify behavior and screen content, not look and feel.
