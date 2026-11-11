# PRD: dbui MVP 1.0

Status: draft, for review. Local only (not yet published as issues).
Terms follow `GLOSSARY.md`.

## Problem Statement

A developer must run SQL quickly against a remote PostgreSQL database. They reach it through a tunnel (`ssh`, `cf ssh`). Native tools such as `psql` are not always installed and are not easy to install. Existing GUI tools are heavy. A terminal tool that needs only Java is missing. It must stay open for hours, show results readably, help with schema names, and also work in scripts.

## Solution

dbui is a Java terminal tool that talks to PostgreSQL over JDBC.

- **Interactive mode** looks like the pi agent in its fullscreen mode: dbui owns the terminal window. The Transcript scrolls above. A small multi-line editor sits at the bottom between two horizontal rules, and a status bar (one or more lines) sits under it. Editor and status bar stay fixed while the Transcript scrolls. Reference layout: `docs/assets/pi_layout.png`. Everything else is a Slash command.
- **Batch mode** runs Statements from the command line, a file or stdin, and writes Results to stdout.
- Connections come from Profiles or command-line options. Each run is a Session with its own history and log. Sessions can be resumed (forked).
- Tunnel setup stays outside dbui.

## User Stories

### Connecting

1. As a developer, I want to start dbui with `--host`, `--port`, `--db` and `--user`, so that I can connect to my tunnel without a config file.
2. As a developer, I want dbui to connect directly when the options are complete, so that my open-tunnel-and-connect loop is fast.
3. As a developer, I want dbui to ask for missing values in an edit dialog, so that I do not have to restart the tool.
4. As a developer, I want the dialog to be prefilled with the values I passed on the command line, so that I only type what is missing.
5. As a developer, I want to start dbui with a full JDBC URL, so that I can paste a ready connection string.
6. As a developer, I want to save connection settings as named Profiles, so that I do not retype them.
7. As a developer, I want to start dbui with `-p <profile>`, so that I connect to a known target with one option.
8. As a developer, I want command-line options to override Profile fields, so that I can change a tunnel port for one run.
9. As a developer, I want to add, edit, list and remove Profiles inside dbui, so that I do not edit a file by hand.
10. As a developer, I want Profile edits always saved to the profiles file, so that they survive the end of the Session.
11. As a developer, I want `/connect [profile]` to switch the connection inside a Session, so that I do not restart dbui.
12. As a developer, I want a password from `--password`, from the JDBC URL, from the Profile, or from a masked prompt, so that I can use the way that fits my workflow.
13. As a developer, I want a Profile password to accept an `${ENV}` reference, so that secrets managers such as mise or fnox can supply it.
14. As a developer, I want dbui to write the profiles file readable only by me, and to warn if the mode is wider, so that stored passwords stay private.
15. As a developer, I want extra JDBC driver properties in a Profile, so that I can set options such as `sslmode` without new dbui features.
16. As a developer, I want the application name `dbui` set on the connection, so that a database admin can recognise my Session.
17. As a developer, I want an initial schema (search path) per Profile, so that unqualified table names work as I expect.
18. As a developer, I want two instances of dbui to edit Profiles without losing each other's changes, so that parallel work is safe.

### Running statements (interactive)

19. As a developer, I want `Enter` to send my input, so that the editor works like the pi agent.
20. As a developer, I want `Ctrl-J` (configurable) to insert a newline, so that I can write long Statements.
21. As a developer, I want cursor, word and line navigation in the editor, so that I can edit quickly.
22. As a developer, I want pasted multi-line SQL to stay one input, so that a paste does not run line by line.
23. As a developer, I want several Statements in one input to run in order, so that I can paste a small script.
24. As a developer, I want execution to stop at the first error, so that later Statements do not run on a bad state.
25. As a developer, I want semicolons inside strings, comments and `$$` blocks not to split Statements, so that functions and literals work.
26. As a developer, I want each Result shown with its row count and duration, so that I see cost and size at once.
27. As a developer, I want a Row limit (default 1000) that dbui applies and not the SQL, so that I never flood my terminal and my SQL stays unchanged.
28. As a developer, I want a "truncated" notice when the Row limit cut a Result, so that I do not mistake it for the full data.
29. As a developer, I want to change the Row limit with `/limit N`, so that I can read more or fewer rows.
30. As a developer, I want the Row limit default in the application config, so that I set it once.
31. As a developer, I want `Ctrl-C` to cancel a running Statement without closing dbui, so that a mistake does not cost me the Session.
32. As a developer, I want `Ctrl-C` and `Ctrl-D` never to exit dbui, so that only `/quit` ends the Session.
33. As a developer, I want a query timeout in the application config and `/timeout` for the Session, so that runaway queries stop.
34. As a developer, I want the status bar to show a running timer during execution, so that I know the Statement is still active.
35. As a developer, I want the editor locked while a Statement runs, so that behavior is simple and predictable.
36. As a developer, I want writes allowed by default, with Read-only per Profile and a global default, so that I decide per target.
37. As a developer, I want `/readonly on|off` for the Session, so that I can switch protection quickly.
38. As a developer, I want the status bar to show read-only or read-write, so that I know my protection state.
39. As a developer, I want one automatic reconnect after a lost connection, so that a tunnel drop does not force a restart.
40. As a developer, I want Statements never retried automatically, so that a write does not run twice.

### Output

41. As a developer, I want the formats `table`, `markdown`, `csv`, `tsv` and `json`, so that I can read results or pass them to tools.
42. As a developer, I want `/format` to change the Format, so that I can switch during a Session.
43. As a developer, I want a horizontal and a vertical Orientation, so that wide Results with few rows stay readable.
44. As a developer, I want `/orientation` and a cycle key to change the Orientation, so that I can switch quickly.
45. As a developer, I want `json` to write one object per row, so that wide Results are easy to read.
46. As a developer, I want NULL, multi-line text and time zone rendering to be configurable, so that I can use my terminal font features.
47. As a developer, I want long text cut with an ellipsis in `table`, so that rows stay on one line.
48. As a developer, I want `csv`, `tsv` and `json` never to cut data, so that machine formats lose nothing.
49. As a developer, I want binary values shown as hex with their length, so that I can recognise them.
50. As a developer, I want `json` and `jsonb` columns embedded as real JSON in the `json` Format, so that output stays valid JSON.
51. As a developer, I want `/save <file>` to write the current Result in the current Format and Orientation, so that I keep a result quickly.
52. As a developer, I want `/dump <file> [sql]` to rerun all Statements of the given SQL, or of my last input, and write all rows, so that I can export beyond the Row limit. With `json` the file is one valid JSON document.
53. As a developer, I want `/dump` to refuse an input with any non-query Statement, so that a repeat does not change data twice.

### Screen and messages

54. As a developer, I want every event shown as a Message block in the Transcript, so that the Transcript is a complete log of my Session.
55. As a developer, I want input echo in gray, info in yellow and errors in red, so that I can tell them apart at a glance.
56. As a developer, I want SQL errors to show the server message, the SQL state and the position, so that I can fix the Statement.
57. As a developer, I want no Java stack trace in the terminal, so that errors stay readable.
58. As a developer, I want stack traces in a log file inside my Session directory, so that parallel Sessions do not mix logs.
59. As a developer, I want `NO_COLOR` respected, so that dbui works in plain terminals.
60. As a developer, I want a status bar with connection, mode, Format, Orientation, Row limit and last Result summary, so that I always know my state.
61. As a developer, I want `/help` to list commands and keys, so that the status bar stays short.

### Schema and Completion

62. As a developer, I want `/schema`, `/tables` and `/describe <table>`, so that I can inspect the database without writing catalog SQL.
63. As a developer, I want schema data read through JDBC metadata, so that dbui needs no database-specific queries in 1.0.
64. As a developer, I want Completion only when I press a key (default `Tab`), so that typing is never slowed or interrupted.
65. As a developer, I want Completion for SQL keywords and for schema, table and column names, so that I type fewer names.
66. As a developer, I want after `FROM` table suggestions and after `table.` column suggestions, so that suggestions are relevant.
67. As a developer, I want a key or command to refresh the schema cache, so that new tables appear.

### Sessions and history

68. As a developer, I want each dbui run to be its own Session with its own history, so that parallel instances do not mix.
69. As a developer, I want `Up`, `Down` and `Ctrl-R` to use the history of my Session, so that I can repeat Statements.
70. As a developer, I want `/resume` to list earlier Sessions with connection, start time and last Statement, so that I can pick the right one.
71. As a developer, I want `/resume` to fork the chosen Session, so that two instances can resume the same Session safely.
72. As a developer, I want Resume to restore Profile values, settings and history but not the password, so that secrets are not stored in Session files.
73. As a developer, I want old Sessions cleaned up beyond a configured count, so that the state directory does not grow forever.
74. As a developer, I want history writing switchable, and lines that start with a space not saved, so that I can keep secrets out of files.

### Batch mode

75. As a developer, I want `-c "<sql>"` (repeatable), `-f <file>` and stdin as inputs, so that I can use dbui in scripts.
76. As a developer, I want batch Results on stdout only, so that pipes stay clean.
77. As a developer, I want errors and notices on stderr and a non-zero exit code on failure, so that scripts can react.
78. As a developer, I want `--format` and `--orientation` in batch mode, so that I get the same output options as in the editor.
79. As a developer, I want batch mode to stream rows without a Row limit unless I pass `--limit N`, so that large exports work.
80. As a developer, I want batch mode to fail with a clear error when connection values are missing, so that scripts never hang on a prompt.
81. As a developer, I want a wrapper script `dbui`, so that I do not type `java -jar`.

### Configuration

82. As a developer, I want hand-edited settings in `config.yaml` that dbui never writes, so that my comments stay intact.
83. As a developer, I want Profiles in `profiles.yaml`, so that I can encrypt or manage that file separately.
84. As a developer, I want key bindings configurable in `config.yaml`, so that I can adapt dbui to my terminal.
85. As a developer, I want the editor and status bar to stay fixed while I scroll the Transcript with the mouse wheel and the keyboard, so that I never lose the prompt.
86. As a developer, I want the screen to redraw correctly when the window is resized or a multiplexer pane is split or unsplit, so that the layout never breaks.
87. As a developer, I want to select and copy text from the Transcript, so that I can reuse Results.

### Copying Results

88. As a developer, I want `/copy` to send the held query Result in the current Format and Orientation to the terminal clipboard, so that I can paste it without saving a file. OSC 52 support is required. Text above 64 KB of formatted UTF-8 bytes is refused with a hint to use `/save`; truncated Results name the Row limit.

## Implementation Decisions

**Technology**
- Java 25. Spring Boot, stripped down: no web server, no connection pool, auto-configuration disabled where possible, lazy beans. Used for dependency injection and configuration binding (YAML, `${ENV}` placeholders). Startup time is not a goal.
- TamboUI (with the JLine backend) for the fullscreen layout, Transcript, editor, status bar and key handling (ADR 0006). Spring Shell is not used. History and Completion logic are our own code.
- picocli for command-line parsing and for Slash commands (each Slash command is a subcommand).
- PostgreSQL JDBC driver only. One plain JDBC connection per Session, autocommit on, no transaction handling.
- Single executable jar plus a thin wrapper script. Other distribution forms are out of scope.

**Modules (proposed)**
- *Session*: owns connection, settings, history, log. Takes an input (Statement or Slash command) and produces Message blocks and Results. No terminal knowledge. This is the central module.
- *Statement splitter*: splits input at `;`, aware of quotes, comments and dollar quoting.
- *Executor*: runs Statements, applies Row limit, timeout and cancel, reconnects once.
- *Result formatter registry*: one formatter per Format, each supporting both Orientations except `json`. Shared by Interactive and Batch mode.
- *Message block renderer*: kinds input echo, result, info, error. Each block is a full-width band with a background color, one padded line above and below, and no border. Optional footer line inside a band (for example duration). Colors configurable.
- *Schema provider*: reads schemas, tables, columns and keys through JDBC metadata and caches them.
- *Completion engine*: keyword plus schema-name completion from nearby text. No full SQL parsing.
- *Profile store*: reads and writes `profiles.yaml` atomically. Before saving, it rereads the file and applies only the one changed Profile. File mode `600`.
- *Settings*: reads `config.yaml`. Defaults: Row limit 1000, Format `table`, Orientation horizontal, Read-only off, timeout none.
- *Session store*: one directory per Session in the state directory, holding the history, the log file and the Session metadata (Profile values and settings, no password). Resume copies a Session directory into a new one. A configured count limits old Sessions.
- *Terminal glue*: thin TamboUI adapter (multi-line editor widget, Transcript viewport, key bindings, status bar drawing, locked editor during execution). Contains no logic that can live elsewhere.

**Behavior decisions**
- Connection precedence: command-line options override Profile fields. Direct connect when values are complete. Otherwise the edit dialog opens prefilled.
- Password order: `--password` or JDBC URL, then Profile (with `${ENV}`), then masked prompt. Batch mode never prompts. `.pgpass` is not used. A Profile has no timeout field.
- Row limit is applied through the JDBC statement, never by rewriting SQL.
- `/save` writes the Result held in memory. `/dump` reruns all Statements of the given SQL or of the last input and streams all rows, and accepts only inputs where every Statement is a query (including `WITH ... SELECT`). It writes to a temporary file and renames it on success.
- `/copy` takes no argument. It formats the held query Result like `/save` and sends OSC 52 through the terminal backend, with no external tools or new dependencies. The limit is 65,536 formatted UTF-8 bytes before base64 encoding. No Result, an empty Result, or a last successful Statement without a Result produces info and no clipboard write. Feedback reports delivery to the terminal, not confirmed clipboard acceptance.
- `Ctrl-C` cancels a running Statement. It never exits dbui. `Ctrl-D` does not exit either. Only `/quit` exits.
- Errors in Interactive mode: no stack trace on the terminal. Stack traces go to the log file in the Session directory.
- Value rendering: NULL as `NULL` in `table`, empty in `csv` and `tsv`, `null` in `json`. ISO dates in the JVM time zone by default. Plain numbers. Long text cut with an ellipsis in `table`. Newlines shown as a marker in `table`. Binary as hex with length. All of this is configurable and will be tuned after the prototype.
- Batch mode: data on stdout, messages on stderr, stop at first error with non-zero exit code, no Row limit unless `--limit`.

**Prototype first**
The JLine spike (issue 0001) is done. Its result led to ADR 0006. A second spike (issue 0019) must confirm that TamboUI supports all of: `Enter` to send, `Ctrl-J` newline, word and line navigation, a status bar that survives scrolling output, bracketed paste, on-demand Completion and a locked editor during execution.

## Testing Decisions

- A good test checks external behavior at a seam, never internals. It reads like a specification.
- Work is test-first (TDD), see `AGENTS.md`. Unit tests are `*Test`. Integration tests are `*IT` and use Testcontainers with PostgreSQL.
- **Agreed seams** (accepted in refinement; may be adjusted during the build):
  1. **Session input to Message blocks and Results**, against a real PostgreSQL (`*IT`). Covers execution, Row limit, cancel, timeout, read-only, reconnect, `/save`, `/dump`, schema commands and Resume. This is the main seam.
  2. **Result formatter**: pure unit tests (`*Test`) for every Format and Orientation, and value rendering.
  3. **Statement splitter**: pure unit tests.
  4. **Batch entry point** (arguments, stdin, stdout, stderr, exit code) against PostgreSQL (`*IT`).
  5. **Terminal glue**: a few tests with the TamboUI test tool (Pilot) for key bindings (`Enter` sends, `Ctrl-J` newline, `Ctrl-C` cancels). Decision logic lives outside the glue and is tested at seam 1.
- `/copy`: plain Unit tests at `CopyCommand.execute` and the `ClipboardSink` boundary check exact OSC 52 bytes, Format/Orientation, absent Results, the UTF-8 byte limit and truncation feedback. Session tests cover registry dispatch and held query Result eligibility. Actual paste in Herdr on WSL, a plain terminal and Windows Terminal is a human check.
- Profile store and Settings: unit tests with temporary directories (round trip, atomic write, change merge, file mode).
- Prior art: none. The project has only a generated context test.

## Out of Scope

- Type-ahead during a running Statement, and parallel queries.
- A full-screen result viewer or pager.
- Alias-aware Completion and full SQL parsing.
- TOML config, OS keyring, `.pgpass`.
- Databases other than PostgreSQL.
- GraalVM native image and other distribution forms.
- Deep schema inspection (triggers, enums, view definitions, partial indexes).
- Opening the tunnel.
- Transaction management beyond what the user types as SQL.

## Further Notes

- Open items for later: whether the Transcript is printed to the normal terminal on exit (decide after issue 0019), exact key binding defaults, exact band colors and status bar lines, state directory layout details.
- Layout of the Session directory is an implementation detail. It holds at least the history, the log and the Session metadata.
