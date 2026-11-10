# 0023: Profile names with spaces

**What to build:** A Profile whose name contains spaces can be selected with `/connect`, `/profile edit` and `/profile remove`. Before, the quoted name `"Test 3"` and the list number `2` were rejected as unknown Profiles (see `docs/assets/profile_with_whitespace.png`).

**Blocked by:** None (found in manual testing of issue 0013)

**Status:** done

**Parent:** `docs/prd/mvp-1.0.md`

- [x] A name in double quotes selects that Profile (`/profile edit "Test 3"`). A quoted text is always a name, also a number
- [x] An unquoted name with spaces works when such a Profile exists (`/profile edit Test 3`)
- [x] A whole number selects the Profile at that position of the last `/profile list` (or the current list), for `/connect`, `/profile edit` and `/profile remove`. A Profile with exactly that number as its name wins
- [x] A missing closing quote, an unknown name and an unknown number give a clear error band with a hint how to quote
- [x] Argument completion offers names with spaces in quotes
- [x] The usage text and `/help` mention the number and the quoting rule
- [x] Unit tests for the name resolution and an integration test through the Session
