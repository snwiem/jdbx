# 0011: Read-only and reconnect

**What to build:** Read-only is a Profile setting with a global default (default: off). `/readonly on|off` changes it for the Session. The status bar shows read-only or read-write. After a lost connection dbui reconnects once on the next Statement and never repeats a Statement by itself.

**Blocked by:** 0006 Settings and Profiles (read), 0010 Execution controls

**Status:** done

**Parent:** `docs/prd/mvp-1.0.md`

- [x] Integration tests: a write fails in read-only mode and works in read-write mode
- [x] Profile value overrides the global default, and `/readonly` overrides both for the Session
- [x] Integration test: after the connection is closed, the next Statement triggers one reconnect and a clear info band, or a clear error if the reconnect fails
- [x] A failed Statement is never run twice
- [x] Status bar shows the mode
- [x] Read-only is enforced by the server, not only flagged: with autocommit on, `Connection.setReadOnly(true)` alone does not stop writes in the PostgreSQL driver (finding of issue 0006). Use the driver's read-only mode setting or equivalent, and prove it with a test that a write fails

## Integration with issue 0012

- `/readonly [on|off]` uses the per-Session Slash command registry, including help, validation, Completion candidates and background execution. There is no separate dispatch hook.
- SQL and schema work reconnect on the Statement thread. `/resume` can run without first reconnecting the old target, so an unavailable target does not prevent recovery to another Session.
- The Status bar retains Format and Orientation alongside mode, Row limit, timer and Result summary. Completion integration adds a loading note without hiding mode; a one-line Status bar puts mode first.
- `/connect` keeps current Read-only unless the selected Profile explicitly sets it. Reconnect keeps the mode, while Resume restores the stored mode. Newly opened connections and reused Resume connections verify the effective server mode. A server-forced Read-only connection rejects a requested read-write mode instead of displaying a false status.
- A disconnected `/readonly on|off` attempts one reconnect with the requested mode. Settings change only when the server confirms the replacement. This permits `/readonly on` after failover to a Read-only server; rejected changes keep the earlier mode.
- Startup, `/connect`, reconnect and Resume use one checked opener and one replacement path. Timeout and Row limit remain Statement-scoped. Connection replacement also rebuilds the Completion cache and updates metadata and label.
- The original Read-only and reconnect assertions remain unchanged. The merged registry path first failed five Read-only Integration tests with unknown-command errors; registration made them pass. The added help/candidate check is regression coverage and passed immediately.
