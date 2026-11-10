# Spike 0001: JLine inline editor (THROWAWAY)

This code answers issue 0001. It is throwaway. Do not reuse it as production code.

Needs Java 25 and Maven. JLine 4.6.0 from Maven Central.

Run by hand in a real terminal (not in an IDE console):

```sh
./spikes/0001-jline-spike/run.sh
```

The script builds on first run (`mvn -q package`).

Try these inputs:

- `Enter` sends. `Ctrl-J` inserts a newline. `/quit` exits.
- `/out 30` prints 30 result bands. `/work` runs a 3 s job with a running timer; only `Ctrl-C` is read.
- `Ctrl-Space` (or `Alt-/`) opens the Completion list. Typing never opens it.
- Rebind newline: `KEY_NEWLINE=$'\033\r' ./spikes/0001-jline-spike/run.sh` (newline becomes `Alt-Enter`).

All keys live in the `KEYS` map at the top of `src/main/java/Spike.java`.
