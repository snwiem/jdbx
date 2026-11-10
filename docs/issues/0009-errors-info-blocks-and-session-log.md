# 0009: Errors, info blocks and Session log

**What to build:** Errors show as red bands with the server message, SQL state and position when the server gives one. Notices show as yellow info bands. Stack traces never reach the terminal and go to a log file in the Session directory. `NO_COLOR` is respected.

**Blocked by:** 0008 Interactive tracer

**Status:** done

**Parent:** `docs/prd/mvp-1.0.md`

- [x] Unit tests for error and info band text
- [x] A SQL error integration test shows message, SQL state and position and keeps the Session usable
- [x] An unexpected error shows a short message with the log file path
- [x] Each Session writes its own log, and two parallel Sessions never share a log file
- [x] With `NO_COLOR` set, no color codes are written
- [x] A Statement without a Result (for example `create table`) shows an info band with the command tag and update count, and not only the input echo (finding of issue 0008)

## Notes

- State directory: `$DBUI_STATE_DIR`, else `$XDG_STATE_HOME/dbui`, else `~/.local/state/dbui`; Session directory `sessions/<UTC timestamp>-<8 hex>/` with `session.log`. No `--state-dir` option yet (would touch `BatchCommand`).
- `NO_COLOR` (non-empty): no color codes; labels `> `, `info: `, `error: ` and dim/bold/reverse styles; Status bar reversed.
- Command tag is derived from the first keywords (INSERT shows `0 <count>`; UPDATE, DELETE, MERGE, COPY show the count; CREATE/DROP/ALTER show the object keyword).
- The PostgreSQL driver is now a compile-scope dependency (for `PSQLException` server error fields).
