# 0025: Resume must not keep a connection with other Profile settings

**What to build:** `ResumeConnection.plan` returns `Keep` when host, port, database and user match. It ignores the saved Profile and settings such as `currentSchema`. Example: connect with Profile A, switch to Profile B (same database and user, other schema), then Resume A. The B connection stays, so the search path and schema Completion are wrong while the Session metadata says A. Found by code review (copilot-review.md, finding 3).

**Blocked by:** none

**Status:** open

**Parent:** `docs/prd/mvp-1.0.md`

- [ ] Unit test: same endpoint and user, but a different Profile name, gives Reconnect (or an explicit state restore), not Keep
- [ ] Unit test: same endpoint, same Profile, different `currentSchema` gives Reconnect
- [ ] Existing same-target, same-Profile Keep case still passes
- [ ] Integration test: after Resume the schema Completion and `current_schema()` match the saved Profile
