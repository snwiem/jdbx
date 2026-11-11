# 0034: Isolate integration tests from the user's config directory

**What to build:** Integration tests (at least `BatchEntryPointIT`) read the real config directory (`$XDG_CONFIG_HOME/dbui` or `~/.config/dbui`). A hand-edited `config.yaml` with an invalid `key-bindings` map made 24 Batch tests fail on 2026-11-11, although the code was correct. Tests must not depend on the developer's own `config.yaml` or `profiles.yaml`. Found while merging issue 0018.

**Blocked by:** none

**Status:** done

**Parent:** `docs/prd/mvp-1.0.md`

## Findings

- Original failure: a caller `config.yaml` containing `key-bindings:\n  ewline: alt-n` made `BatchEntryPointIT` fail with `Error: config.yaml: unknown 'key-bindings.ewline'`. Use the temporary-directory reproduction below; do not modify the developer's config.
- Original workaround: setting `DBUI_CONFIG_DIR` to an empty temporary directory made `./mvnw -q verify` pass.
- Check all tests and the wrapper test `bin/test-dbui.sh` for the same dependence (config directory, state directory, profiles, `DBUI_*` environment variables).

## Acceptance criteria

- [x] Every test that starts dbui (Batch or Session) uses an explicit, temporary config directory and state directory (`--config-dir`, or `DBUI_CONFIG_DIR` set by the build for the test JVM)
- [x] Failsafe and Surefire set `DBUI_CONFIG_DIR` and the state directory to a directory under `target/` so no test reads or writes the home directory
- [x] Test: with an invalid `config.yaml` in the real config directory, the suite still passes (a build check or a documented manual check). Proved with a temporary caller config directory instead of changing the developer's real files; see evidence below.
- [x] `bin/test-dbui.sh` does the same

## Decision

- Isolate at the build boundary, without production changes. Surefire and Failsafe set `DBUI_CONFIG_DIR`, `DBUI_STATE_DIR`, `XDG_CONFIG_HOME`, `XDG_STATE_HOME`, `HOME` and Java's `user.home` under `target/test-environment/`. `mvn clean` removes these defaults. Tests that need custom files keep their own JUnit temporary directories.
- Config resolution remains `--config-dir`, `DBUI_CONFIG_DIR`, `$XDG_CONFIG_HOME/dbui`, then `~/.config/dbui` (README, “Configurable editor keys”). State resolution is `DBUI_STATE_DIR`, `$XDG_STATE_HOME/dbui`, then `~/.local/state/dbui` (`SessionDirectory.stateBase`). Session history, logs, metadata and Session locks live below that state base. `profiles.yaml` and `.profiles.lock` live in the config directory (`ProfileStore`).
- Audited all four `ProcessBuilder` sites: two Batch subprocesses, the low-heap Save/Dump subprocess, and the Session lock-holder subprocess. None clears or replaces its inherited environment, so Maven's isolated config/state variables reach each child. Session tests already pass explicit temporary state bases; Profile tests pass explicit temporary config directories. No subprocess-specific change is needed.
- The standalone wrapper tests set the same environment paths below their existing `mktemp` directory, including `HOME`. They already clear caller `JAVA_HOME`, `DBUI_JAVA`, `DBUI_JAR` and `DBUI_OPTS` before testing those overrides. Their Java stubs now record config/state/home variables for an isolation assertion.
- Use a deliberately invalid config in a temporary caller directory for the build check. Never edit or inspect the developer's real config, Profiles or state for this check. No terminal behavior changes, so no human terminal check is required.

## Red/green evidence

Observed on 2026-11-11. Logs are in `/tmp/dbui-0034-red-m3qkAJ/` for this run.

1. **Batch red, before the build change.** Created `/tmp/dbui-0034-red-m3qkAJ/xdg/dbui/config.yaml` containing `key-bindings:\n  ewline: alt-n\n`. Ran:

   ```sh
   env -u DBUI_CONFIG_DIR XDG_CONFIG_HOME="$probe/xdg" \
     ./mvnw -q verify -Dit.test=BatchEntryPointIT
   ```

   Here `probe=/tmp/dbui-0034-red-m3qkAJ`. Exit **1**; **24 failures in 38 Batch tests**. stderr included `config.yaml: unknown 'key-bindings.ewline'`. Log: `red.log`.

2. **Batch green, after the build change.** Repeated the identical command with the same invalid caller config. Exit **0**; all 38 Batch tests passed, including child JVM checks. Log: `green.log`.

3. **Wrapper red/green.** Added the stub environment assertion before adding isolation. With caller `DBUI_CONFIG_DIR`, `DBUI_STATE_DIR`, `XDG_CONFIG_HOME` and `XDG_STATE_HOME` pointing into the probe directory, `sh bin/test-dbui.sh` exited **1**: `11 passed, 1 failed` (`config and state are isolated from the caller`). After exporting isolated directories, it exited **0**: `12 passed, 0 failed`. The green run also supplied invalid caller `DBUI_JAVA`, `DBUI_JAR` and `DBUI_OPTS`. Logs: `wrapper-red.log`, `wrapper-green.log`.

4. **Full hostile-environment build.** `./mvnw -q verify` passed with `DBUI_CONFIG_DIR` pointing directly at the invalid config, hostile state/XDG paths and invalid wrapper settings. Exit **0**. Log: `hostile-verify.log`.

5. **Acceptance build.** With `XDG_CONFIG_HOME="$probe/xdg"`, `DBUI_STATE_DIR="$probe/caller-state"` and `XDG_STATE_HOME="$probe/caller-xdg-state"` exported for this run only:

   ```sh
   env -u DBUI_CONFIG_DIR ./mvnw -q verify
   ```

   Exit **0**: **479 Unit tests and 251 Integration tests**, no failures, errors or skips. Neither caller state path was created. Log: `acceptance-verify.log`.

`TestEnvironmentTest.defaultConfigAndStateUseBuildDirectoriesInsteadOfTheCallersHome` is an additional build-path regression guard. It passed on its first run; it did **not** supply red-before-green evidence. The existing Batch tests and the new wrapper assertion supplied that evidence.
