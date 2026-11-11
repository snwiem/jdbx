# 0026: Session cleanup must not delete a running Session

**What to build:** `SessionStore.cleanup` removes the oldest Session directories, whether or not another dbui process still uses them. Several Sessions may run at the same time (PRD). A new Session can delete the history, metadata and log of an older, running Session. Cleanup must only remove inactive Sessions. Found by code review (copilot-review.md, finding 4).

**Blocked by:** none

**Status:** done

**Parent:** `docs/prd/mvp-1.0.md`

- [x] Decide in this issue how to detect a live Session (for example a lock file held with `FileChannel.tryLock`, released by the OS when the process ends) and write the decision in a `## Decision` section or ADR
- [x] Unit test: an old Session with a live lock is not removed, even when the count limit is exceeded
- [x] Unit test: an old Session without a live lock (crashed process, stale file) is removed
- [x] Retention count still applies to inactive Sessions; the current Session is never removed
- [x] Works on Linux and on WSL

## Decision

A Session is live while it holds an operating system lock on `session.lock` in its Session directory.

- `SessionDirectory.create` creates `session.lock` and takes an exclusive `FileChannel.tryLock` on it. The channel stays open until `SessionDirectory.release()`. `Session.close()` calls it, and Resume calls it on the directory that the fork replaces. The OS also releases the lock when the process ends or crashes, so a stale `session.lock` does not keep a directory alive.
- `SessionStore.cleanup` skips a directory when `SessionDirectory.isLocked` is true. Live Sessions do not count against the retention count `keep`: it applies to inactive Sessions (the current Session still counts as one, as before). A directory without `session.lock` (Session of an older version) counts as inactive.
- `isLocked` answers for lock files that this JVM holds from a static set, without opening them. This covers `OverlappingFileLockException` (same JVM counts as live) and avoids probe channels on a file that the JVM has locked: `FileLock` documents that on some systems closing any channel on a file drops all locks of the JVM on it. The probe with `tryLock` is used for files of other processes only. I could not reproduce the drop on this JDK and kernel (the guard test stays green without the set), so the set is a precaution.
- When the lock state cannot be determined (`tryLock` throws `IOException`, for example on a file system without lock support), the directory counts as live: keeping a directory is the safe error. If `create` cannot take the lock for the same reason, the Session runs without one.
- Not changed: the probe and the delete are not atomic. A directory cannot become live in between, because every Session creates a new directory with a unique id. Resume only reads the history of the source directory.

Verified on Linux (WSL2, ext4) with a real second process (a child JVM that holds the lock, then is killed). A state directory on a Windows mount (`/mnt/c`, DrvFs) was not tested; if locks fail there, the directory counts as live and is never cleaned up.
