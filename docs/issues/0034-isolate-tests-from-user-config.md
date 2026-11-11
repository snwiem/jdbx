# 0034: Isolate integration tests from the user's config directory

**What to build:** Integration tests (at least `BatchEntryPointIT`) read the real config directory (`$XDG_CONFIG_HOME/dbui` or `~/.config/dbui`). A hand-edited `config.yaml` with an invalid `key-bindings` map made 24 Batch tests fail on 2026-11-11, although the code was correct. Tests must not depend on the developer's own `config.yaml` or `profiles.yaml`. Found while merging issue 0018.

**Blocked by:** none

**Status:** open

**Parent:** `docs/prd/mvp-1.0.md`

## Findings

- Reproduce: put `key-bindings:\n  ewline: alt-n` into `~/.config/dbui/config.yaml`, then run `./mvnw -q verify`. `BatchEntryPointIT` fails with `Error: config.yaml: unknown 'key-bindings.ewline'`.
- Workaround: `DBUI_CONFIG_DIR=/tmp/empty ./mvnw -q verify` passes.
- Check all tests and the wrapper test `bin/test-dbui.sh` for the same dependence (config directory, state directory, profiles, `DBUI_*` environment variables).

## Acceptance criteria

- [ ] Every test that starts dbui (Batch or Session) uses an explicit, temporary config directory and state directory (`--config-dir`, or `DBUI_CONFIG_DIR` set by the build for the test JVM)
- [ ] Failsafe and Surefire set `DBUI_CONFIG_DIR` and the state directory to a directory under `target/` so no test reads or writes the home directory
- [ ] Test: with an invalid `config.yaml` in the real config directory, the suite still passes (a build check or a documented manual check)
- [ ] `bin/test-dbui.sh` does the same
