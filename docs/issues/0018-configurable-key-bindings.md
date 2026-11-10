# 0018: Configurable key bindings

**What to build:** Keys for newline (default `Ctrl-J`), Completion (default `Ctrl-Space`) and the Orientation cycle (default `Shift-Tab`) can be changed in `config.yaml`.

**Blocked by:** 0008 Interactive tracer

**Status:** ready-for-agent

**Parent:** `docs/prd/mvp-1.0.md`

- [ ] Unit tests for key binding parsing and defaults
- [ ] An invalid key name gives a clear error band at start and defaults apply
- [ ] `/help` shows the current bindings
- [ ] A Pilot test shows a changed newline key works
