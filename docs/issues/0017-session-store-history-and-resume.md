# 0017: Session store, history and `/resume`

**What to build:** Each Session has its own directory with history, log and Session metadata (Profile values and settings, no password). `Up`, `Down` and `Ctrl-R` use the history of the current Session. `/resume` lists earlier Sessions with connection, start time and last Statement, and forks the chosen one into a new Session.

**Blocked by:** 0006 Settings and Profiles (read), 0008 Interactive tracer

**Status:** in-progress (one criterion open: password dialog, see Notes)

**Parent:** `docs/prd/mvp-1.0.md`

- [x] Unit tests with temporary directories: create, fork, cleanup by configured count
- [x] Two instances can resume the same Session and do not affect each other or the original
- [ ] A resumed Session restores Profile values, settings and history, and asks for the password again
- [x] History can be switched off and lines that start with a space are not saved
- [x] The Session directory also holds the log file from ticket 0009

## Notes (implementation)

- Open criterion: "asks for the password again" needs the password dialog of issue 0013. Until then `/resume` reconnects only when the password is known without a prompt: same connection (kept), same user, host and port as the current connection (current password reused, other database), or a Profile whose password resolves (`${ENV}`). Otherwise it shows an error band that names the connection, says the password is missing and that the dialog (issue 0013) is not built.
- Config keys use the nested form of the PRD wording: `history: {enabled: false}` and `sessions: {keep: 50}`. A flat `history-enabled` is not read.
- History skips blank lines, lines that start with a space, `/resume`, `/quit` and a repeat of the last entry.
- `session.yaml` holds started, connection (host, port, database, user, URL without credentials and password-like parameters, Profile name), settings (row-limit, timeout, read-only), last-statement (one line, 80 characters) and resumed-from.
- `/resume` restores row limit, timeout and read-only into the Session settings. pgjdbc does not enforce read-only on an autocommit connection; that is outside this issue.
