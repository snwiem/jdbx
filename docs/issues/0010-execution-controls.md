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

- `ExecutionControls` owns `/limit` and `/timeout`. Its hook in `Session.submit` is separate from other Slash commands to limit merge conflicts with issues 0015 and 0017.
- Both modes use `JdbcResultReader`; `FormatOptions.from(Settings)` supplies the same value formatting options. Interactive mode remains horizontal `table`; startup Format and Orientation selection belongs to issue 0012.
- A read-only review found pgjdbc can ignore cancellation immediately before execution. A deterministic failing Integration test now pauses at that JDBC boundary over real PostgreSQL. Cancellation retries on a virtual thread until that Statement completes; it never targets a later input. Closing the Session also requests cancellation. Invalid configured Row limits and negative timeouts are rejected before execution.
- Red-before-green: behavior tests and review fixes failed before their production changes. Some new API tests first failed at compilation. The terminal check exposed a missing timer redraw before the tick handler changed. The invalid Slash command, first-error stop, immediate-cancel and ESC/BEL tests characterized behavior that was already green; parsing validation was implemented with the command handlers before those extra cases were added. No artificial failing step was introduced for them.

## Human check still required

Run in the real terminal and in Herdr. Judge band colors and padding, timer readability and flicker, editor locking and the return to usable input after Ctrl-C. Repeat after resize. Automated checks verify behavior and screen content, not look and feel.
