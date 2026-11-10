# 0008: Interactive tracer

**What to build:** Starting `dbui` with complete connection values opens Interactive mode in the fullscreen layout of ADR 0006: the Transcript above, a fixed editor between two horizontal rules, and a fixed status bar under it. `Enter` sends the input. The echo of the input shows as a gray band, the Result as a band below it, and the status bar shows the connection. `/quit` ends dbui. The Session module and the Message block renderer exist.

**Blocked by:** 0002 Batch tracer, 0019 TamboUI spike

**Status:** ready-for-agent

**Parent:** `docs/prd/mvp-1.0.md`

- [ ] Layout follows `docs/assets/pi_layout.png` (full-width bands with padding, no borders)
- [ ] `*IT` tests at the Session seam: input in, Message blocks and Results out, against PostgreSQL
- [ ] Unit tests for the Message block renderer (input echo, result kinds)
- [ ] A few Pilot (TamboUI test tool) tests for `Enter` sends and `Ctrl-J` newline
- [ ] Only `/quit` ends the Session. `Ctrl-C` at the prompt clears the input, `Ctrl-D` does nothing destructive
- [ ] The Transcript scrolls with wheel and keys while editor and status bar stay fixed
- [ ] `Up` and `Down` and cursor, word and line navigation work in the editor
