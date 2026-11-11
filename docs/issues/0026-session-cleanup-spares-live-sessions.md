# 0026: Session cleanup must not delete a running Session

**What to build:** `SessionStore.cleanup` removes the oldest Session directories, whether or not another dbui process still uses them. Several Sessions may run at the same time (PRD). A new Session can delete the history, metadata and log of an older, running Session. Cleanup must only remove inactive Sessions. Found by code review (copilot-review.md, finding 4).

**Blocked by:** none

**Status:** open

**Parent:** `docs/prd/mvp-1.0.md`

- [ ] Decide in this issue how to detect a live Session (for example a lock file held with `FileChannel.tryLock`, released by the OS when the process ends) and write the decision in a `## Decision` section or ADR
- [ ] Unit test: an old Session with a live lock is not removed, even when the count limit is exceeded
- [ ] Unit test: an old Session without a live lock (crashed process, stale file) is removed
- [ ] Retention count still applies to inactive Sessions; the current Session is never removed
- [ ] Works on Linux and on WSL
