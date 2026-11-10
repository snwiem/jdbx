# 0017: Session store, history and `/resume`

**What to build:** Each Session has its own directory with history, log and Session metadata (Profile values and settings, no password). `Up`, `Down` and `Ctrl-R` use the history of the current Session. `/resume` lists earlier Sessions with connection, start time and last Statement, and forks the chosen one into a new Session.

**Blocked by:** 0006 Settings and Profiles (read), 0008 Interactive tracer

**Status:** ready-for-agent

**Parent:** `docs/prd/mvp-1.0.md`

- [ ] Unit tests with temporary directories: create, fork, cleanup by configured count
- [ ] Two instances can resume the same Session and do not affect each other or the original
- [ ] A resumed Session restores Profile values, settings and history, and asks for the password again
- [ ] History can be switched off and lines that start with a space are not saved
- [ ] The Session directory also holds the log file from ticket 0009
