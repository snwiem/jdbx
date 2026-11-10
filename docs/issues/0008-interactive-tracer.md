# 0008: Interactive tracer

**What to build:** Starting `dbui` with complete connection values opens Interactive mode: an editor between two horizontal rules, a status bar under it, and the scrollback above. `Enter` sends the input. The echo of the input shows as a gray band, the Result as a band below it, and the status bar shows the connection. `/quit` ends dbui. The Session module and the Message block renderer exist.

**Blocked by:** 0001 JLine spike, 0002 Batch tracer

**Status:** ready-for-agent

**Parent:** `docs/prd/mvp-1.0.md`

- [ ] Layout follows `docs/assets/pi_layout.png` (full-width bands with padding, no borders)
- [ ] `*IT` tests at the Session seam: input in, Message blocks and Results out, against PostgreSQL
- [ ] Unit tests for the Message block renderer (input echo, result kinds)
- [ ] A few virtual-terminal tests for `Enter` sends and `Ctrl-J` newline
- [ ] Only `/quit` ends the Session. `Ctrl-C` at the prompt clears the input, `Ctrl-D` does nothing destructive
- [ ] `Up` and `Down` and cursor, word and line navigation work in the editor
