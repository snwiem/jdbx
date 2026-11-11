# 0025: Resume must not keep a connection with other Profile settings

**What to build:** `ResumeConnection.plan` returns `Keep` when host, port, database and user match. It ignores the saved Profile and settings such as `currentSchema`. Example: connect with Profile A, switch to Profile B (same database and user, other schema), then Resume A. The B connection stays, so the search path and schema Completion are wrong while the Session metadata says A. Found by code review (copilot-review.md, finding 3).

**Blocked by:** none

**Status:** done

**Parent:** `docs/prd/mvp-1.0.md`

- [x] Unit test: same endpoint and user, but a different Profile name, gives Reconnect (or an explicit state restore), not Keep
- [x] Unit test: same endpoint, same Profile, different `currentSchema` gives Reconnect
- [x] Existing same-target, same-Profile Keep case still passes
- [x] Integration test: after Resume the schema Completion and `current_schema()` match the saved Profile

## Decision

Resume keeps the open connection (`Keep`) only when all of these match the saved Session:

1. host, port, database and user;
2. the Profile name (both `null` counts as equal);
3. the credential-free JDBC URL, which includes non-secret query parameters such as `currentSchema` in the URL;
4. if the saved Profile still loads: its resolved non-secret connection properties (for example `currentSchema` from the Profile `schema` field) equal those of the open connection. The password is ignored. A missing or unloadable Profile does not block `Keep`; the existing fallback rules apply.

Any difference gives `Reconnect` (or `Prompt` when no password is available). The saved Session stores the URL and Profile name but not the properties, so rule 4 compares against the current Profile definition. A Profile edited after the Session was saved therefore reconnects with the new definition.
