# 0031: Tab requests Completion

**What to build:** Tab requests Completion in the editor, instead of Ctrl-Space. Ctrl-Space is awkward to press, and some terminals do not send it (hence the `Alt-/` fallback). Tab is the usual key for completion in shells. Found while testing by hand (change request from the user).

**Blocked by:** none (the default key map is in `EditorKeys`; issue 0018 makes it configurable)

**Status:** done

**Parent:** `docs/prd/mvp-1.0.md` (user story 64: Completion only on a key; the default key changes from `Ctrl-Space` to `Tab`)

## Check result (feasibility)

A plain switch works. Facts found in the code:

- The default is one line in `EditorKeys`: `complete = KeyTrigger.ctrl(' ')`. `InteractiveScreen.handle` already routes the key through `keys.complete()`, so no new code path is needed.
- Tab already has other jobs, and none of them conflict:
  - Popup open: plain Tab accepts a candidate. `handleCompletionKey` runs before the request check, so Tab first requests Completion and, while the popup is open, accepts. This is the usual shell-like flow.
  - Shift-Tab cycles Orientation. The Completion key must match plain Tab only (no Shift, Ctrl or Alt). A test must prove that Shift-Tab still cycles Orientation.
  - Profile dialog: the modal handles keys first, so Tab still moves between fields there.
- Bracketed paste is on (`InteractiveMode`), so a pasted Tab arrives as a `PasteEvent` and does not request Completion.
- Cost: Tab no longer inserts a tab character in the editor. A Statement typed by hand rarely needs one. This is the accepted trade-off.

## Decision (proposed, confirm before work)

- `complete` default: `Tab`. `completeAlternate` stays `Alt-/`. `Ctrl-Space` is no longer a default.
- Docs and text change from `Ctrl-Space` to `Tab`: `/help` (`SlashCommandRegistry`), `README.md`, the `InteractiveScreen` comments, `GLOSSARY.md` if it names the key, issue 0016 and 0018 (a short note), PRD story 64.
- Completion still never opens by itself.

## Findings

`KeyTrigger.key(TAB)` ignores Shift unless the trigger requires it, so it also matches Shift-Tab. `EditorKeys.requestsCompletion` excludes Shift-Tab; a unit test proves it.

## Acceptance criteria

- [x] Unit test: plain Tab in the editor requests Completion (candidates or popup as before with Ctrl-Space)
- [x] Unit test: Shift-Tab still cycles Orientation and does not request Completion
- [x] Unit test: with the popup open, Tab accepts the candidate and does not request again
- [x] Unit test: a pasted Tab (`PasteEvent`) does not request Completion
- [x] Unit test: Tab in the Profile dialog still moves between fields
- [x] `/help` and `README.md` name Tab as the Completion key and `Alt-/` as the fallback
- [x] Manual check in a real terminal (Herdr) that Tab completes (done by the user)
