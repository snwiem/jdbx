# 0001: JLine spike

**What to build:** A throwaway prototype and a short findings note that prove or disprove that JLine can deliver the inline editor of ADR 0001: `Enter` sends, `Ctrl-J` inserts a newline, word and line navigation, a status bar that stays at the bottom while output scrolls, bracketed paste, a locked editor while work runs, and a hook for on-demand Completion.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

**Parent:** `docs/prd/mvp-1.0.md`

- [ ] Each capability above is tried and marked works / works with caveat / does not work in the findings note
- [ ] The findings note lists the editor and status bar layout that the real implementation will use (see `docs/assets/pi_layout.png`)
- [ ] If a capability does not work, the note proposes an alternative and ADR 0001 is updated
- [ ] The prototype code is clearly marked throwaway and is not reused as production code
