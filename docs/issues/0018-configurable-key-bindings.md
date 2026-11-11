# 0018: Configurable key bindings

**What to build:** Keys for newline (default `Ctrl-J`), Completion (default `Tab`, fallback `Alt-/`) and the Orientation cycle (default `Shift-Tab`, fallback `Ctrl-O`) can be changed in `config.yaml`.

**Blocked by:** 0008 Interactive tracer

**Status:** ready-for-manual-check

**Parent:** `docs/prd/mvp-1.0.md`

- [x] Unit tests for key binding parsing and defaults
- [x] An invalid key name gives a clear error band at start and defaults apply
- [x] `/help` shows the current bindings; the Status bar shows the current newline key
- [x] A Pilot test shows a changed newline key works
- [ ] Manual terminal check: changed newline, Completion and Orientation keys work in Herdr, including their fallbacks
- [ ] Manual terminal check: an invalid or conflicting map shows a readable startup error band and all default keys work

## Decision

- Use a `key-bindings` map with five action names: `newline`, `complete`, `complete-alternate`, `orientation-cycle`, `orientation-fallback`. Missing actions retain defaults. Enter remains the fixed send key.
- Key names are case-insensitive and trimmed. Supported syntax: `tab`, `enter`, `shift-tab`, `shift-enter`; or `ctrl-`/`alt-` plus a letter `a`–`z`, `/`, `space`, `tab` or `enter`. Examples: `ctrl-j`, `alt-/`, `ctrl-space`. Combined modifiers, function keys and unmodified printable characters are not supported.
- Reject duplicate keys across the resolved map, including defaults and fallbacks. Also reserve `enter`, `ctrl-c`, `ctrl-d`, `ctrl-r`, `ctrl-w` for fixed editor actions. Error messages name the config field, invalid key or conflicting actions. Reject unknown action names and non-text values.
- Reject the whole invalid map. Interactive startup shows the error as an error Message block and applies all default bindings, preserving other settings. Batch startup fails before connecting. The plain parser and strict config loader throw `ConfigException`; the Interactive loader reports key-map errors through a callback for the screen to display.
- Keep key configuration outside Resume metadata. Output changes and Resume's execution settings preserve the key map from the current config file. Dialog keys and Completion acceptance keys stay fixed. Exact Shift matching for Tab and Enter excludes Shift-Tab from the default Completion request, but permits an explicitly configured Shift-Tab request when Orientation is rebound. Character bindings retain KeyTrigger matching so layout-required Shift does not block them.

Defaults follow issue 0031: Completion is Tab with Alt-/ as fallback. Ctrl-Space is not a default, but can be configured. Shift-Enter remains the editor widget's newline fallback unless bound to another action. `/help` omits that fallback hint when it is rebound.

## Verification

`./mvnw -q verify` passed after the review follow-up: 457 Unit tests and 246 Integration tests, without failures, errors or skips. New key-binding tests need no database. No human terminal checks were performed.

A scripted pseudo-terminal check ran the packaged jar in three disconnected Sessions. It checked the configured newline and Ctrl-Space Completion keys, current `/help`, invalid-name/conflict startup error bands, default newline recovery, and clean `/quit` with alternate-screen exit. Artifacts: `/tmp/dbui-0018-pty-b4_55xxy`. This does not replace a real-keyboard check in Herdr.

Observed red before green:

- `KeyBindingTest.parsesKeyNamesWithoutATerminal` — compile-red for the parsed key/modifier boundary (eight examples).
- `ConfigTest.editorBindingsUseTabForCompletionAndKeepLegacyFallbacks` — compile-red for the new settings boundary.
- `ConfigTest.keyBindingsOverrideDefaultsAndSurviveOutputChangesAndResume` — returned the default key instead of the configured key.
- `ConfigTest.invalidKeyNamesIdentifyTheConfigFieldAndValue` — all six invalid names initially passed validation.
- `ConfigTest.conflictingBindingsIdentifyBothActionsAndTheKey` — all seven conflicts initially passed validation.
- `ConfigTest.malformedBindingConfigurationGivesAConfigError` — map shape, unknown/null actions and null values failed with missing/wrong errors. The numeric/list-value variants passed immediately and are regression guards.
- `ConfigTest.interactiveStartupRejectsAnInvalidMapAndReportsItWhileUsingAllDefaultBindings` — compile-red for the recovery boundary.
- `InteractiveScreenTest.configuredNewlineKeyWorksThroughThePilot` — sent `ab` instead of `a\nb`.
- `InteractiveScreenTest.startupConfigErrorsRemainErrorBandsWhenTheSessionRefreshes` — compile-red for the startup-error display boundary.
- `InteractiveScreenTest.statusBarShowsTheConfiguredNewlineKey` — showed a fixed Ctrl-J hint instead of Alt-N; found during the packaged-terminal check.
- `SlashCommandRegistryTest.helpShowsConfiguredEditorBindingsBeforeConnecting` — showed constants instead of configured keys through Session `/help`.
- `SlashCommandRegistryTest.helpDoesNotDescribeShiftEnterAsANewlineWhenItRequestsCompletion` — retained a contradictory fixed newline hint.

Passed immediately (regression guards, not claimed as red-green):

- `BatchCommandKeyBindingsTest.invalidBindingsFailAtStartupBeforeConnecting` (two cases).
- `InteractiveScreenTest.changedNewlineDoesNotKeepCtrlJAsAnUndocumentedNewline`.
- `InteractiveScreenTest.supportedKeyNamesProduceANewline` (13 syntax/event examples).
- `InteractiveScreenCompletionTest.configuredCompletionAndOrientationKeysReplaceTheDefaults`.
- `InteractiveScreenCompletionTest.configuredShiftTabCanRequestCompletionWhenOrientationUsesAnotherKey`.

## Review follow-up

1. **Fixed — layout-required Shift:** character bindings retain KeyTrigger matching. Real TamboUI EventParser tests reproduce German Alt+Shift+7 (`ESC[55:47;4u`) and Ctrl+Shift+7, plus legacy and US Kitty slash events. Tab/Enter still match Shift exactly; legacy and Kitty Shift-Tab do not request Completion by default.
2. **Fixed — legacy guidance:** README and `/help` name Ctrl-H/Backspace, Ctrl-I/Tab, Ctrl-J/LF and Ctrl-M/Enter, warn about accidental send, and retain the bracketed-paste alternative. The review's Ctrl-J premise has an existing exception: InteractiveBackend preserves bare LF as Ctrl-J outside paste. Parser tests pin all four raw aliases and their distinct Kitty events; the existing backend test covers that exception. No backend behavior changed.
3. **Fixed — Ctrl-W:** reserve it for delete previous word. Invalid-map recovery still applies all defaults.
4. **Fixed — startup errors:** README now states that startup key-map errors stay at the top of the Transcript for the Session. This remains intentional, not a transient error.
5. **Fixed — consistent construction:** screen constructors take KeyBindings, not independently supplied EditorKeys. EditorKeys and the Status bar derive from the same Settings key map, including NO_COLOR tests.
6. **Fixed — test setup and imports:** add the explicit `KeyBindings.from(Map)` overload and `Settings.withKeyBindings`. UI tests use in-memory maps instead of temporary config files. Replace inline fully qualified class names with imports in the touched key-configuration code and tests.
7. **Fixed — Shift-Enter observation:** Pilot confirms that Shift-Enter now inserts a newline without sending; Enter then sends the whole multi-line input. Keep that behavior, unless Shift-Enter is explicitly rebound. README notes the change from the earlier send behavior; no further behavior change was needed.

New tests and observed red/green:

- `EditorKeysTest.slashBindingsMatchTerminalEvents` — German Alt-/ and Ctrl-/ failed before the fix, then passed. Legacy and US Kitty cases passed immediately (regression guards).
- `ConfigTest.ctrlWIsReservedForDeletingThePreviousWord` — accepted Ctrl-W before the fix, then rejected it with the named conflict.
- `InteractiveScreenTest.customBindingsAndStatusBarUseTheSameNewlineKeyWithoutColor` — showed Ctrl-J instead of Alt-N before the fix, then passed through the shared KeyBindings constructor.
- `SlashCommandRegistryTest.helpExplainsLegacyControlAliasesAndBracketedPaste` — failed on missing aliases and paste advice, then passed.
- `EditorKeysTest.shiftTabDoesNotRequestCompletionByDefault` (two events), `EditorKeysTest.legacyControlAliasesAreNotDistinctWithoutKitty` (four cases), and `InteractiveScreenTest.shiftEnterInsertsANewlineWithoutSendingThroughThePilot` — passed immediately; regression guards, not claimed as red-green.

Full verification passed; log: `/tmp/dbui-0018-review-verify.log`. Human checks remain unticked. In addition to the checks below, test German-layout Alt-/ on a real keyboard in Herdr and confirm the Shift-Enter newline behavior.

## Run and check by hand

Use `--config-dir <temporary directory>` so existing Profiles and settings stay unchanged. `config.yaml` is never written by dbui.

1. Start with `key-bindings: {newline: alt-n, complete: ctrl-space, complete-alternate: alt-c, orientation-cycle: alt-o, orientation-fallback: ctrl-t}`. Confirm `/help` shows those keys. Use Alt-N for a multi-line Statement, both Completion keys, and both Orientation keys. Tab/Enter must still accept an open Completion candidate without sending input.
2. Open a Profile dialog. Check that Tab/Shift-Tab still navigate fields and Enter still handles the dialog, not the editor.
3. Restart with `key-bindings: {newline: not-a-key}`, then with `key-bindings: {newline: tab}`. Cancel a startup connection dialog if it covers the Transcript. Check the error band names the bad key or both conflicting actions. Confirm Ctrl-J, Tab/Alt-/ and Shift-Tab/Ctrl-O all use defaults.

Some terminal combinations are indistinguishable or intercepted (for example Ctrl-I/Tab and Alt-Tab). Real-keyboard behavior remains a manual check; parsing cannot resolve terminal byte aliases.
