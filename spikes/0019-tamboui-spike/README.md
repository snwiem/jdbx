# Spike 0019: fullscreen UI on TamboUI (THROWAWAY)

Throwaway code for issue 0019. Do not reuse it as production code. Findings: `docs/issues/0019-tamboui-spike.md`.

Run by hand (Java 25; the first run builds with `../../mvnw`):

    ./spikes/0019-tamboui-spike/run.sh

Options:

- `./run.sh --dump` prints the Transcript to the normal screen after `/quit`.
- `KEYS=legacy ./run.sh` does not push the Kitty keyboard protocol.
- `NOFIX=1 ./run.sh` removes the Ctrl-J fix (then `Ctrl-J` sends, like `Enter`).

Commands: `/quit`, `/work` (3 s job, `Ctrl-C` cancels), `/out N`, `/copy` (OSC 52), `/crash`.
Keys: `Enter` sends. `Ctrl-J`, `Alt-Enter`, `Shift-Enter` add a newline. `PageUp`/`PageDown` and the wheel scroll. `Ctrl-Y` copies the last Result. The status bar shows the last key it received.

Files: `Session` and `Transcript` are plain logic. `Spike` is the TamboUI glue. `PilotCheck` tries the TamboUI test tool:

    java --enable-native-access=ALL-UNNAMED -cp "target/classes:target/lib/*" PilotCheck
