# 0013: Profiles in the editor

**What to build:** In Interactive mode dbui connects directly when the command-line options are complete. If values are missing, the Edit Connection Profile dialog opens prefilled with the given values. `/connect [profile]` and `/profile` (add, edit, list, remove) manage Profiles. Edits are always saved to `profiles.yaml`.

**Blocked by:** 0006 Settings and Profiles (read), 0008 Interactive tracer

**Status:** done

**Parent:** `docs/prd/mvp-1.0.md`

- [x] Unit tests with temporary directories: atomic write, merge of one changed Profile into a freshly read file, mode `600`, warning for a wider mode
- [x] Passwords given at the prompt or on the command line are not written unless the user typed them into the dialog password field
- [x] Two instances that edit different Profiles do not lose each other's change
- [x] `/connect` switches the connection inside one Session and the status bar updates
- [x] A masked prompt asks for a missing password in Interactive mode
- [x] `/resume` (issue 0017) asks for the password in the masked prompt when the resumed Session's connection needs one that cannot be resolved without a prompt

## Implementation decisions

- `/connect` uses a numbered list in an info Message block. `/connect <name|number>` selects a Profile. With no saved Profiles, `/connect` opens a connection dialog.
- The dialog has Name, JDBC URL, Host, Port, Database, User, masked Password, Read-only, Schema and Properties fields. Properties use `key=value;key=value`.
- `Tab` / `Shift-Tab` move between fields and Save. `Enter` moves forward, then saves on Properties or Save. `Ctrl-S` saves anywhere. `Esc` cancels.
- In a connection dialog, `Ctrl-G` connects without saving. `Ctrl-Enter` is an alias where the terminal distinguishes it. Profile add/edit dialogs require Save.
- An empty Read-only field keeps the current Session mode. At startup that mode comes from `config.yaml` (default: off). A Profile with an explicit Read-only value overrides it when selected. Save deliberately persists non-secret command-line overrides. Use Connect without saving for one-off overrides.

## Verification

- Before integration, the feature branch passed `./mvnw -q verify`: 173 Unit tests and 116 Integration tests; no failures, errors or skips. Integration tests use PostgreSQL Testcontainers. The combined verification is recorded below.
- The Profile store tests cover literal `${ENV}`, mode `600`, wider-mode warning, removal, unknown Profiles, concurrent stores preserving different changes, preserved unknown keys in other Profiles, and readers never observing partial YAML during replacement.
- Session tests cover connection replacement and old-connection closure, labels, fresh schema cache, Row limit, timeout, Read-only, add/edit/remove, prompt credentials, Resume retries, and URL/TLS password references through the actual loader and save path. Combined tests also prove current-mode inheritance in both directions when global settings differ.
- Pilot and `Frame.forTesting` tests cover field navigation, save, legacy-terminal Connect, Esc, masked input, navigation without changing credential provenance, small-screen rendering and visible validation/password errors.
- Python pty plus pyte checks passed against the packaged app and an owned PostgreSQL container. Five terminal Sessions covered empty and partial startup, masked fields and prompts, save/restart with `-p`, Profile list/edit/remove, numbered `/connect`, wider-mode warning, Resume prompting and `Ctrl-G`. A separate terminal check covered an environment-referenced port. Terminal bytes and Session files contained no test password. Artifacts: `/tmp/dbui-0013-pty/final`; venv: `/tmp/dbui-0013-pyte`; Maven log: `/tmp/dbui-0013-final-verify.log`.

### Red-before-green record

New public APIs first failed compilation. Behavioral tests then reproduced lost concurrent edits, missing Resume prompts, implicit Read-only changes, broken typed environment references, discarded dialog values, missing prompt retries, stripped URL users, expanded secret references, navigation changing credentials, missing legacy Connect handling and hidden modal errors before their fixes. The packaged startup check first failed with `No connection target`, then passed with the dialog. The concurrent-reader atomicity test and some additional preservation assertions were added as coverage after those behaviors already worked; they were not observed red. Terminal wiring was verified by the packaged-app check, not by a separate Unit test for `InteractiveMode`.

Before integration, three read-only cross-family review passes used Claude Opus 5.5. Their security findings led to regression tests and fixes for typed Profile loading, Read-only inheritance, URL/property reference preservation and global Read-only fallback on connection changes. The first review's Git-clean acceptance check overlapped parent edits and was rejected for that reason; it did not indicate a reviewer modification. Later review checks passed. The final global-default finding was reproduced red and fixed with a PostgreSQL Session test.

## Integration with issues 0011, 0012 and 0016

- `/connect` and `/profile` are entries in the per-Session Slash command registry. Both have help, argument labels and background execution. `/connect` Completion reads current Profile names lazily; `/profile` offers `list`, `add`, `edit`, `remove`. There are no separate dispatch hooks.
- `/connect` keeps current Read-only unless the selected Profile says `true` or `false`. Reconnect preserves it. Resume restores the earlier settings, not the selected Profile's current Read-only value. This intentionally supersedes the feature branch's global-default fallback after a connection switch.
- `Session.openConnection` prepares all newly opened Interactive connections, including direct startup, `/connect`, reconnect and Resume. It validates execution settings, enforces the driver mechanism from ADR 0007 and verifies the server mode. JDBC `currentSchema` properties apply the initial search_path. Row limit and timeout are Statement-scoped and read from the same settings on every execution.
- `replaceConnection` installs the prepared connection, updates label and Session metadata, rebuilds the schema provider for schema commands and Completion, clears connection-lost state and Result summary, and closes the old connection. Resume validates and prepares before forking; a reused connection also receives the server-mode check.
- Modal input has priority over Completion and editor keys. Opening a modal discards pending Completion and its popup. Shift-Tab moves between dialog fields; it cycles Orientation only in the editor. Enter never sends editor input while a popup or modal is open. `/help` has one combined binding list, including dialog and password keys.
- Prompt and dialog passwords never enter `submit`, input echo, history or metadata. Inline credential options/assignments and URLs in `/connect` or `/profile` are rejected before echo or history. Plain Profile names such as `api-token` remain usable. Credential-shaped names containing a URL or password assignment must be renamed or selected by number; this is the conservative safety trade-off.

### Cross-feature test evidence

- The required Profile Read-only → failed write → `/readonly off` → successful write → Resume-settings test first failed because `/connect` was not registered. It passed after registry integration.
- The reconnect test first failed on stale Completion data. It proves Format, Orientation and Read-only survive `pg_terminate_backend` and Completion uses the replacement connection.
- The Slash Completion test first returned no candidates for `/for`; it now returns `/format`, and `/format ` returns all Format names.
- Additional failing checks reproduced Read-only reset on `/connect`, inline credential history leaks (including `pass=` and `PGPASSWORD=`), disconnected Completion failure, stale popup after a modal, overbroad credential-name filtering and unchecked server mode during same-connection Resume. The startup factory API check was compile-red.
- A review finding led to a test that reproduced a failed read-write reconnect to a newly Read-only server. `/readonly on` now opens the replacement in the requested mode and persists it only after verification. A rejected `/readonly off` reconnect leaves the earlier mode and Not connected state unchanged.
- Dynamic Profile candidates, reverse Read-only inheritance, prompted connection recovery after failed reconnect, credentials retained only in memory, and Resume prompting after failed reconnect passed immediately as regression coverage. They are not claimed as separate red-before-green cycles.
- Existing feature assertions were not weakened. Profile and Resume test inputs now ask `show transaction_read_only`, rather than the session default, because ADR 0007 guards each Statement with an explicit transaction. One old Profile test setup explicitly switches mode before selecting an unset Profile; the original assertions remain. Separate cross-feature cases distinguish current mode from global defaults in both directions.

### Final combined verification

- Full `./mvnw -q verify` passed after all review fixes: **233 Unit tests and 181 Integration tests**, with no failures, errors or skips. Log: `/tmp/dbui-merge-final-verify.log`. The independent full test-goal run also passed: `/tmp/dbui-merge-final-tests.log`.
- `FeatureIntegrationIT` contains 18 cross-feature cases, including all three requested scenarios. `StatusBarTextTest` covers the combined normal and one-line text with mode, output settings, Row limit, timer, summary, label and loading note.
- Read-only cross-family review used Claude Opus 5.5. The final follow-up found no remaining blockers. The first scout's Git-clean acceptance overlapped parent edits and was rejected; it did not indicate scout modifications. Later review checks did not depend on a clean shared checkout.
- Delegated real Python pty plus pyte QA passed all eight combined check groups across four terminal Sessions. Artifacts: `/tmp/dbui-merge-pty-20261110-1839-qa/`.
- After the final rebuild, the parent reran the complete real pseudo-terminal script. It passed across four terminal Sessions against a separate owned PostgreSQL container. Artifacts and report: `/tmp/dbui-merge-parent-pty-UoZ5w8/`; venv: `/tmp/dbui-0013-pyte` (outside the repo). It checked empty startup and modal keys, masking, Connect without saving, Profile save/mode 600, `/connect`, server Read-only and `/readonly off`, NUL Ctrl-Space and Alt-/, every Format candidate, Enter without send, `/help`, both Orientation keys, backend termination and reconnect, Completion after reconnect, Resume settings and masked prompt, CLI precedence, `/quit` and alternate-screen restoration. The owned container and app processes were removed.
- Server-forced Read-only at startup, the failover-mode override, rejected mode changes, inline credential aliases, dynamic candidates and disconnected recovery have Unit/Integration coverage. They were not all separate pseudo-terminal checks. Human colors, flicker, resizing and Herdr key forwarding remain judgment checks. No unrelated Herdr pane was accessed.

## Known limits

- SnakeYAML does not retain YAML comments. Unknown keys in other Profiles and at the root are retained. The edited Profile is replaced as a unit.
- Host, port, database, user, schema and Read-only environment references resolve before editing and save as literal values. Password, URL and driver-property references remain literal. An unset non-password environment variable can prevent editing; repair it in the file or environment. Removal does not need the values resolved.
- Profile renaming is not supported in edit. Use add and remove. Concurrent changes to the same Profile are last-writer-wins; the no-lost-change guarantee covers different Profiles. An edit can recreate a Profile another Session removed.
- Connection cancellation remains outside this issue. A failed direct startup or quitting before connecting can leave a Session directory without metadata. After canceling disconnected startup, schema commands and `/readonly on|off` report Not connected. Completion still offers keywords and Slash commands. `/resume` needs a Session context, created by the first successful connection; a later failed reconnect does not prevent Resume or its password prompt.
- The Properties field is not masked. Use environment references for TLS secrets. Database credentials belong in the dedicated User and Password fields; existing `properties.user` / `properties.password` entries are not retained by dialog saves.

## Manual test list

1. Use a temporary config directory and temporary `XDG_STATE_HOME`. Start `dbui --config-dir "$config_dir"` without connection options. Fill the dialog and verify Tab, Shift-Tab, masked Password and Esc.
2. Save a named Profile, run a Statement, exit with `/quit`, then restart with `dbui --config-dir "$config_dir" -p <name>`. Check `stat -c '%a' "$config_dir/profiles.yaml"` reports `600`. Set mode `644`, restart, check the yellow warning, and save again to restore `600`.
3. Run `/connect`, `/connect <name>` and `/connect <number>`. Check the Status bar target. Run `/profile list`, `/profile edit <name>` and `/profile remove <name>`. Cancel removal once, then confirm with `y`.
4. Remove a Profile's password, connect to it, and answer the masked prompt. Check the prompt password is absent from `profiles.yaml`, history and `session.yaml`. Use `/resume` for an earlier target whose password is unavailable and check the same prompt and successful fork.
5. Start with partial options and a one-off override. Use `Ctrl-G` to connect without saving and confirm the Profile file is unchanged. Check visual colors, focus visibility, resize behavior and flicker in the human terminal; the automated checks do not replace that judgment.

### Combined manual checks

1. Start without connection options. Use the dialog, Tab, Shift-Tab and masked Password. Save once and use Connect without saving once.
2. `/connect` to a Profile with `read-only: true`. Try a write, then `/readonly off` and try again. Confirm the label and mode in the Status bar.
3. Type `/for`, request Ctrl-Space Completion and try Alt-/. Request `/format ` Completion. Enter must accept without sending. Run `/help` and inspect all commands and editor/modal keys.
4. Set Format, Orientation and Read-only. Kill that backend with `pg_terminate_backend`. The first Statement must fail without replay; the next must reconnect once. Confirm settings, label and SQL schema Completion.
5. Use `/resume` for an earlier Session. Check the masked prompt when needed, restored settings, history and a new Session id. Judge colors, flicker, resizing and Herdr key forwarding yourself.

Fix (found in manual testing): `/connect`, `/profile edit` and `/profile remove` accept a quoted name (`"Test 3"`), an unquoted name with spaces, or the number from `/profile list`. An exact name wins over a number. A quoted number is always a name. Tracked in `docs/issues/0023-profile-names-with-spaces.md`.
