# 0011: Read-only and reconnect

**What to build:** Read-only is a Profile setting with a global default (default: off). `/readonly on|off` changes it for the Session. The status bar shows read-only or read-write. After a lost connection dbui reconnects once on the next Statement and never repeats a Statement by itself.

**Blocked by:** 0006 Settings and Profiles (read), 0010 Execution controls

**Status:** ready-for-agent

**Parent:** `docs/prd/mvp-1.0.md`

- [ ] Integration tests: a write fails in read-only mode and works in read-write mode
- [ ] Profile value overrides the global default, and `/readonly` overrides both for the Session
- [ ] Integration test: after the connection is closed, the next Statement triggers one reconnect and a clear info band, or a clear error if the reconnect fails
- [ ] A failed Statement is never run twice
- [ ] Status bar shows the mode
