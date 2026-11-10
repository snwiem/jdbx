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
- An empty Read-only field inherits the global `config.yaml` default, not the previous connection's effective value. Save deliberately persists non-secret command-line overrides. Use Connect without saving for one-off overrides.

## Verification

- `./mvnw -q verify` passed: 173 Unit tests and 116 Integration tests; no failures, errors or skips. Integration tests use PostgreSQL Testcontainers.
- The Profile store tests cover literal `${ENV}`, mode `600`, wider-mode warning, removal, unknown Profiles, concurrent stores preserving different changes, preserved unknown keys in other Profiles, and readers never observing partial YAML during replacement.
- Session tests cover connection replacement and old-connection closure, labels, fresh schema cache, Row limit, timeout, JDBC Read-only, global Read-only fallback after a Profile override, add/edit/remove, prompt credentials, Resume retries, and URL/TLS password references through the actual loader and save path.
- Pilot and `Frame.forTesting` tests cover field navigation, save, legacy-terminal Connect, Esc, masked input, navigation without changing credential provenance, small-screen rendering and visible validation/password errors.
- Python pty plus pyte checks passed against the packaged app and an owned PostgreSQL container. Five terminal Sessions covered empty and partial startup, masked fields and prompts, save/restart with `-p`, Profile list/edit/remove, numbered `/connect`, wider-mode warning, Resume prompting and `Ctrl-G`. A separate terminal check covered an environment-referenced port. Terminal bytes and Session files contained no test password. Artifacts: `/tmp/dbui-0013-pty/final`; venv: `/tmp/dbui-0013-pyte`; Maven log: `/tmp/dbui-0013-final-verify.log`.

### Red-before-green record

New public APIs first failed compilation. Behavioral tests then reproduced lost concurrent edits, missing Resume prompts, implicit Read-only changes, broken typed environment references, discarded dialog values, missing prompt retries, stripped URL users, expanded secret references, navigation changing credentials, missing legacy Connect handling and hidden modal errors before their fixes. The packaged startup check first failed with `No connection target`, then passed with the dialog. The concurrent-reader atomicity test and some additional preservation assertions were added as coverage after those behaviors already worked; they were not observed red. Terminal wiring was verified by the packaged-app check, not by a separate Unit test for `InteractiveMode`.

Three read-only cross-family review passes used Claude Opus 5.5. Their security findings led to regression tests and fixes for typed Profile loading, Read-only inheritance, URL/property reference preservation and global Read-only fallback on connection changes. The first review's Git-clean acceptance check overlapped parent edits and was rejected for that reason; it did not indicate a reviewer modification. Later review checks passed. The final global-default finding was reproduced red and fixed with a PostgreSQL Session test.

## Known limits

- SnakeYAML does not retain YAML comments. Unknown keys in other Profiles and at the root are retained. The edited Profile is replaced as a unit.
- Host, port, database, user, schema and Read-only environment references resolve before editing and save as literal values. Password, URL and driver-property references remain literal. An unset non-password environment variable can prevent editing; repair it in the file or environment. Removal does not need the values resolved.
- Profile renaming is not supported in edit. Use add and remove. Concurrent changes to the same Profile are last-writer-wins; the no-lost-change guarantee covers different Profiles. An edit can recreate a Profile another Session removed.
- Connection cancellation remains outside this issue. A failed direct startup or quitting before connecting can leave a Session directory without metadata. After canceling disconnected startup, schema commands give an unfriendly error, and `/resume` needs a connection first.
- The Properties field is not masked. Use environment references for TLS secrets. Database credentials belong in the dedicated User and Password fields; existing `properties.user` / `properties.password` entries are not retained by dialog saves.

## Manual test list

1. Use a temporary config directory and temporary `XDG_STATE_HOME`. Start `dbui --config-dir "$config_dir"` without connection options. Fill the dialog and verify Tab, Shift-Tab, masked Password and Esc.
2. Save a named Profile, run a Statement, exit with `/quit`, then restart with `dbui --config-dir "$config_dir" -p <name>`. Check `stat -c '%a' "$config_dir/profiles.yaml"` reports `600`. Set mode `644`, restart, check the yellow warning, and save again to restore `600`.
3. Run `/connect`, `/connect <name>` and `/connect <number>`. Check the Status bar target. Run `/profile list`, `/profile edit <name>` and `/profile remove <name>`. Cancel removal once, then confirm with `y`.
4. Remove a Profile's password, connect to it, and answer the masked prompt. Check the prompt password is absent from `profiles.yaml`, history and `session.yaml`. Use `/resume` for an earlier target whose password is unavailable and check the same prompt and successful fork.
5. Start with partial options and a one-off override. Use `Ctrl-G` to connect without saving and confirm the Profile file is unchanged. Check visual colors, focus visibility, resize behavior and flicker in the human terminal; the automated checks do not replace that judgment.
