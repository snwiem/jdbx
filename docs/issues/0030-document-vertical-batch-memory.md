# 0030: Document the memory use of vertical Batch output

**What to build:** Vertical Orientation in Batch mode holds the whole Result in memory, because each output line needs one value of every row. Batch mode has no default row limit, so `--limit N` is the only bound. The decision is to keep this behaviour (no required `--limit`, no spooling) and to document it clearly. Vertical Batch output is not the usual use case. Found by code review (copilot-review.md, finding 1); decided with the user.

**Blocked by:** none

**Status:** open

**Parent:** `docs/prd/mvp-1.0.md`

- [ ] The `--orientation` option help text says that `vertical` buffers the whole Result in Batch mode, and names `--limit` as the bound
- [ ] `README.md` has the same warning in the Batch and Orientation sections, with an example using `--limit`
- [ ] Issue 0021 `## Decision` stays as is; this issue only adds the user-facing text
- [ ] No behaviour change; existing tests still pass (including `batchModeHasNoRowLimitByDefaultEvenWithRowLimitInSettings`)
