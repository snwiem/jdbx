# 0029: Password mask must not show the length

**What to build:** The Profile dialog and the password prompt show one `*` per typed character. This shows the password length. Use a fixed-width mask, or no echo. Found by code review (copilot-review.md, finding 7; low priority).

**Blocked by:** none

**Status:** open

**Parent:** `docs/prd/mvp-1.0.md`

- [ ] Unit test: passwords of different lengths give the same rendered mask (the old test in `ConnectionModalTest` expects 12 stars and changes)
- [ ] Cursor and editing (backspace) still work
- [ ] The real password never appears in the rendered output
