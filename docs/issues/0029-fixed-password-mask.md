# 0029: Password mask must not show the length

**What to build:** The Profile dialog and the password prompt show one `*` per typed character. This shows the password length. Use a fixed-width mask, or no echo. Found by code review (copilot-review.md, finding 7; low priority).

**Blocked by:** none

**Status:** done

**Parent:** `docs/prd/mvp-1.0.md`

- [x] Unit test: passwords of different lengths give the same rendered mask (the old test in `ConnectionModalTest` expects 12 stars and changes)
- [x] Cursor and editing (backspace) still work
- [x] The real password never appears in the rendered output

## Decision

A non-empty password field shows a constant mask of 8 `*` (`DialogRenderer.MASK`), whatever the length. An empty field shows nothing. The cursor sits at the end of the mask. Dialog and password prompt share this rule. Tests: `ConnectionModalTest`, `ProfileDialogScreenTest`.
