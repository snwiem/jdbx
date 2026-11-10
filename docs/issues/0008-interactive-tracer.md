# 0008: Interactive tracer

**What to build:** Starting `dbui` with complete connection values opens Interactive mode in the fullscreen layout of ADR 0006: the Transcript above, a fixed editor between two horizontal rules, and a fixed status bar under it. `Enter` sends the input. The echo of the input shows as a gray band, the Result as a band below it, and the status bar shows the connection. `/quit` ends dbui. The Session module and the Message block renderer exist.

**Blocked by:** 0002 Batch tracer, 0019 TamboUI spike

**Status:** done

**Parent:** `docs/prd/mvp-1.0.md`

- [x] Layout follows `docs/assets/pi_layout.png` (full-width bands with padding, no borders)
- [x] `*IT` tests at the Session seam: input in, Message blocks and Results out, against PostgreSQL
- [x] Unit tests for the Message block renderer (input echo, result kinds)
- [x] A few Pilot (TamboUI test tool) tests for `Enter` sends and `Ctrl-J` newline
- [x] Only `/quit` ends the Session. `Ctrl-C` at the prompt clears the input, `Ctrl-D` does nothing destructive
- [x] The Transcript scrolls with wheel and keys while editor and status bar stay fixed
- [x] `Up` and `Down` and cursor, word and line navigation work in the editor

## Implementation

- `com.datev.dbui.session.Session` owns one autocommit JDBC connection and runs Statements on a single background executor. Its public boundary accepts input and returns Message blocks, Results and the ended state. It has no TamboUI dependency.
- `com.datev.dbui.tui.MessageBlockRenderer` reuses `TableFormatter`. Every Message block has horizontal and vertical padding and no border. The Transcript caches wrapped rows and provides scrolling independently of the editor.
- `InteractiveScreen` adapts TamboUI's multi-line `TextArea`, editor keys and viewport. `InteractiveMode` owns terminal resources and transfers completed Session updates to the terminal thread. The editor ignores input while a Statement runs; cancellation belongs to issue 0010.
- TamboUI is pinned to 0.7.0. Its JLine 3 backend resolves JLine 3.30.17. `InteractiveBackend` preserves bare LF as Ctrl-J outside bracketed paste and also accepts Kitty keyboard events. Console logging is detached while Interactive mode owns the screen, then restored.
- The existing connection options stay on `BatchCommand`. With `-c`, Batch mode is unchanged. Without `-c`, it starts Interactive mode. When issue 0003 adds `-f` and other batch inputs, its mode selection must also account for them.

## Verification (2026-11-10)

- `./mvnw -q verify` passes: 5 Session integration tests, 4 existing batch integration tests, 5 Message block renderer tests, 6 terminal adapter tests and 3 existing formatter tests. PostgreSQL integration tests use Testcontainers through Podman.
- `Frame.forTesting(Buffer)` verifies bands, padding, colors, viewport clipping, fixed editor and Status bar, PageUp/PageDown and wheel scrolling. It covers 60×20, 40×10 and 100×40 layouts, plus very small viewports. Pilot verifies Enter, Ctrl-J, Ctrl-C/Ctrl-D, paste, and navigation across lines and words.
- The packaged jar ran in a real Python pseudo-terminal with `pyte`, against a disposable PostgreSQL 17 container. Checks passed for SQL Results, recovery after a SQL error, bare LF and Kitty Ctrl-J, Ctrl-C/Ctrl-D, CRLF bracketed paste, PageUp and wheel events, and resizes to 40×10, 30×8, 100×40 and 100×24.
- `/quit` returned exit code 0. Captured bytes show alternate screen exit, bracketed paste off, mouse capture off and Kitty mode pop. Terminal local flags returned to their original values. Missing connection options returned exit code 2, empty stdout and an error on stderr, without opening Interactive mode.
- A temporary Java agent emitted JUL warnings every 100 ms. Captured warnings appeared before and after Interactive mode, but none appeared while the alternate screen was active. Runtime checks also found and fixed an absent redraw after `runLater` and a duplicate backend close. Temporary tools, virtual environment and captures stayed under `/tmp`, not in the repository.

The structural layout and terminal bytes are verified. A human must still judge the final look and feel on a real keyboard and display; automated checks do not establish that it feels good.

## Run and check by hand

Requires Java 25 and a container runtime. From the project root:

```sh
podman compose up -d postgres
./mvnw -q verify
java --enable-native-access=ALL-UNNAMED -jar target/dbui-0.0.1-SNAPSHOT.jar \
  --host localhost --port 5432 --db dbui --user dbui --password dbui
```

If the Compose provider is Docker, use `docker compose up -d postgres` instead.

1. Send `select 1 as id, 'alice' as name;` with Enter. Check the gray input echo and the padded horizontal Result. Confirm the connection in the Status bar.
2. Use Ctrl-J to write a multi-line Statement. Try Up/Down, Left/Right, Ctrl-Left/Right, Home/End, Backspace and Delete. Paste multi-line SQL and confirm that it waits for Enter.
3. Send `select generate_series(1, 200) as id;`. Scroll with the wheel and PageUp/PageDown. Check the scrollbar and fixed editor and Status bar. Resize the terminal; repeat inside Herdr with split and unsplit.
4. Check colors, padding, typing response and flicker against `docs/assets/pi_layout.png`. Check native selection with Shift-drag if needed; application copy is not part of this issue.
5. At the prompt, type text and press Ctrl-C: it must clear. Press Ctrl-D: dbui must stay open. Send an invalid Statement, then a valid one. Finally send `/quit` and confirm a clean terminal prompt.

No `/format`, `/limit`, Profiles, Completion, Session store, Statement splitter or execution cancellation is implemented here. Those belong to later or parallel issues.
