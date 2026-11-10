# 0013: Profiles in the editor

**What to build:** In Interactive mode dbui connects directly when the command-line options are complete. If values are missing, the Edit Connection Profile dialog opens prefilled with the given values. `/connect [profile]` and `/profile` (add, edit, list, remove) manage Profiles. Edits are always saved to `profiles.yaml`.

**Blocked by:** 0006 Settings and Profiles (read), 0008 Interactive tracer

**Status:** ready-for-agent

**Parent:** `docs/prd/mvp-1.0.md`

- [ ] Unit tests with temporary directories: atomic write, merge of one changed Profile into a freshly read file, mode `600`, warning for a wider mode
- [ ] Passwords given at the prompt or on the command line are not written unless the user typed them into the dialog password field
- [ ] Two instances that edit different Profiles do not lose each other's change
- [ ] `/connect` switches the connection inside one Session and the status bar updates
- [ ] A masked prompt asks for a missing password in Interactive mode
