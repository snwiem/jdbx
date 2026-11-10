# 0009: Errors, info blocks and Session log

**What to build:** Errors show as red bands with the server message, SQL state and position when the server gives one. Notices show as yellow info bands. Stack traces never reach the terminal and go to a log file in the Session directory. `NO_COLOR` is respected.

**Blocked by:** 0008 Interactive tracer

**Status:** ready-for-agent

**Parent:** `docs/prd/mvp-1.0.md`

- [ ] Unit tests for error and info band text
- [ ] A SQL error integration test shows message, SQL state and position and keeps the Session usable
- [ ] An unexpected error shows a short message with the log file path
- [ ] Each Session writes its own log, and two parallel Sessions never share a log file
- [ ] With `NO_COLOR` set, no color codes are written
- [ ] A Statement without a Result (for example `create table`) shows an info band with the command tag and update count, and not only the input echo (finding of issue 0008)
