# dbui

A java based terminal ui tool to query databases over jdbc.

## Install and run

dbui needs Java 25 or newer.

1. Build the jar: `./mvnw -DskipTests package`
2. Link the wrapper script onto your `PATH`:
   `ln -s "$PWD/bin/dbui" ~/.local/bin/dbui`
   The script finds the jar in `target/` through the link. You can also copy `bin/dbui` and the jar into one directory (`dbui.jar` next to the script, or in `../lib/`).

The script passes all arguments and stdin to the jar and returns its exit code.

| Variable | Meaning |
|---|---|
| `DBUI_JAR` | Path to the jar. Overrides the lookup. |
| `DBUI_JAVA` | Path to the `java` executable. Default: `$JAVA_HOME/bin/java`, then `java` on `PATH`. |
| `DBUI_OPTS` | Extra JVM options, split at spaces, for example `-Xmx512m`. |

If no Java 25+ is found, the script prints a message and exits with code 127. If the jar is missing, it exits with code 1.

Try it with the Postgres from `compose.yaml`:

```sh
docker compose up -d --wait postgres

# Batch mode: run a statement and print the Result
dbui --host localhost --db dbui --user dbui --password dbui -c 'select 1'

# Interactive mode: no statement given
dbui --host localhost --db dbui --user dbui --password dbui
```

Test the wrapper script with `sh bin/test-dbui.sh`. It is not part of `./mvnw verify`.

## Batch output and memory

Batch mode streams rows and has no default row limit. Horizontal output uses constant memory.

**Warning:** `--orientation vertical` buffers the whole Result in memory in Batch mode, because each output line needs one value of every row. Use `--limit N` to bound it. Vertical Batch output is not the usual use case.

```sh
dbui --host localhost --db dbui --user dbui --password dbui --orientation vertical --limit 100 -c 'select * from big_table'
```

## JSON output for several Results

With `--format json` or `--format json-pretty`, Batch mode writes each Result as its own JSON array. The arrays follow each other directly, with no blank line. This is a stream of concatenated JSON documents. It is not one document and not strict JSON Lines. `jq` and similar tools read it. If you need one valid JSON document, run one Statement per call.

`json` writes one compact object per row. `json-pretty` writes the same data with two-space indent and one key per line. Both ignore the Orientation and stream row by row. In Interactive mode, `/format json-pretty` selects it.

## Orientation

Orientation is `horizontal` (default) or `vertical`. Set it with `--orientation`, or with `orientation` in `config.yaml`.
In Batch mode, `vertical` holds the whole Result in memory. Always add `--limit N` for large Results, as in the example above.

## Save and Dump in Interactive mode

| Slash command | Action |
|---|---|
| `/save <file>` | Write the newest Result held in memory, using the current Format and Orientation. |
| `/dump <file> [sql]` | Run every Statement of `sql`, or of the last submitted SQL input, and write all rows without the Row limit. |

`/save` warns if the held Result was truncated. With no Result, or an empty Result, it shows an info Message block and writes nothing. Later Format and Orientation changes apply to the saved file, not to earlier Transcript text. A Dump does not replace the held Result.

File names are literal paths, relative to the current directory unless absolute. An unquoted name is one token; `/save` rejects trailing text. Quote a name with single or double quotes if it contains spaces. The matching quote cannot occur inside that quoted name. There is no shell expansion. SQL after the file name keeps its quotes, comments and whitespace.

```text
/save "my result.json"
/dump "all results.json" select * from first_table; select * from second_table;
```

A Dump checks **all Statements before running any**. It accepts `SELECT`, query-only `WITH`, `VALUES`, `TABLE` and `SHOW`, including leading comments and parenthesized queries. It rejects writes, writing CTEs, `SELECT INTO`, `EXPLAIN` and other non-query Statements. The conservative word check also rejects unquoted identifiers named `insert`, `update`, `delete`, `merge` or `into`; quote these identifiers. It also rejects row-locking clauses (`FOR UPDATE`, `FOR NO KEY UPDATE`, `FOR SHARE` and `FOR KEY SHARE`).

Dump refuses to run when the connection reports `standard_conforming_strings = off`, because its SQL scanner assumes standard strings. Set `standard_conforming_strings = on` before using `/dump`. Every accepted Dump Statement runs in its own READ ONLY transaction, even in a read-write Session, and rolls back afterward. The Session mode stays unchanged. Finish an open user transaction with `COMMIT` or `ROLLBACK` before `/dump`. This is a guard against mistakes, not a security boundary: functions that affect other connections or non-transactional state can still have side effects (ADR 0007).

Both commands write a temporary file in the target directory, sync it to disk, and replace the target atomically only on success. A failure or cancellation before publication removes the temporary file and keeps an existing target unchanged. A crash can leave `.dbui-*.tmp` files and `.dbui-columns-*` spool directories in the target directory. The directory must exist and support atomic replacement; a directory cannot be the target file. The completed file and spool files have mode `600` on POSIX systems; replacement does not preserve old permissions and replaces a target symlink rather than following it. Timeout and Ctrl-C apply to Dump execution, including later cursor fetches; success reports the total row count and file name.

With `json` or `json-pretty`, `/save` writes one Result array. `/dump` writes **one JSON document with an outer array of Result arrays**, including for one Result. Other Formats separate Results with one blank line. Several CSV, TSV or Markdown Results in one file are separate tables, not one table.

Dump streams horizontal Results; horizontal `table` samples 1000 rows for column widths. Vertical Dump transposes through temporary column files, not memory. It needs extra disk space proportional to the Result and one open file per column. A single very large cell can still need large memory. Vertical Batch output keeps its existing memory limit described above.

The Transcript retains the newest 200 Message blocks. Earlier retained Results keep frozen text, but only the newest Result keeps row data for `/save`. The held Result survives even if its block leaves the Transcript; an empty Result replaces it. History and Resume remain independent of this retention rule. The default Dump input is the last ordinary SQL submission in this running Session, even if it failed; Slash commands and explicit Dump SQL do not replace it. Resume does not load SQL or Result rows from the earlier Session.

## Configurable editor keys

Editor keys come from `key-bindings` in the hand-edited `config.yaml`. dbui never writes this file.
The config directory is `--config-dir <directory>`, then `DBUI_CONFIG_DIR`, then `$XDG_CONFIG_HOME/dbui`, then `~/.config/dbui`.
Omitted actions keep these defaults:

```yaml
key-bindings:
  newline: ctrl-j
  complete: tab
  complete-alternate: alt-/
  orientation-cycle: shift-tab
  orientation-fallback: ctrl-o
```

For example, set only `newline: alt-n` under `key-bindings` to use Alt-N for newlines.
`complete-alternate` and `orientation-fallback` are configurable fallback keys, not extra actions.
`Ctrl-Space` is no longer a default; use `complete: ctrl-space` if your terminal supports it.
`/help` shows the bindings that apply to the current Session. The Status bar's newline hint also uses the configured key.

Key names are case-insensitive, with surrounding whitespace ignored. Supported names are:

- `tab` and `enter`.
- `shift-tab` and `shift-enter`.
- `ctrl-` or `alt-` followed by one letter `a`–`z`, `/`, `space`, `tab` or `enter`, for example `ctrl-j`, `alt-/`, `ctrl-space`.

Combined modifiers, function keys and unmodified printable characters are not supported.
Tab and Enter match Shift exactly: plain Tab does not also consume Shift-Tab.
Character bindings allow layout-required Shift, for example Alt+Shift+7 for Alt-/ on a German keyboard.
Enter always sends input. `enter`, `ctrl-c`, `ctrl-d`, `ctrl-r` and `ctrl-w` are reserved for send, cancel/clear, ignore, history search and delete previous word.
Every configured binding must be different, including unchanged defaults and fallback keys.
To reuse a default key for another action, also change the action that currently uses it.

An invalid name, unknown action or conflict rejects the **whole key map**, not just one entry.
Interactive mode shows a startup error Message block naming the action and key, and uses all default bindings.
That error stays at the top of the Transcript for the Session; it does not disappear after connecting or running input.
Other valid settings still apply. Batch mode instead reports the config error on stderr and exits before connecting.
Fix the file and restart to apply changes.

Dialog keys and popup acceptance keys do not change: Tab or Enter accepts an open Completion candidate without sending input.
Shift-Enter inserts a newline without sending when the terminal distinguishes it, unless it is configured for another action.
This differs from the behavior before configurable keys, when Shift-Enter also sent input.

Some terminals intercept modified keys or send the same bytes for different keys.
Without Kitty keyboard support, the legacy aliases are `ctrl-h` = Backspace (byte 8), `ctrl-i` = Tab (9), `ctrl-j` = LF (10, normally parsed as Enter), and `ctrl-m` = Enter (13).
Configured Ctrl-H, Ctrl-I and Ctrl-M cannot be distinguished from those fixed keys on legacy terminals.
**Ctrl-J exception:** dbui preserves a bare LF as Ctrl-J outside bracketed paste, so the default newline key still works if the terminal sends LF.
If the terminal sends Enter instead, Ctrl-J or Ctrl-M can send input rather than run the configured action.
Use a distinct key, Kitty keyboard support, or bracketed paste for multi-line input on legacy terminals. Bracketed paste never sends input by itself.

## Help

`/help` shows a short overview: every Slash command name under its group, and five essential keys (as configured). `/help commands` lists all commands with their descriptions, under group headings. `/help keys` lists all key bindings under group headings, then notes about legacy terminals. `/help <word>` shows the commands and key rows whose name or description contains the word, for example `/help history` or `/help ctrl-r`. The word is plain text and ignores case; a leading `/` is allowed. Tab after `/help ` completes `commands`, `keys` and the command names.

## Copy and paste

`Ctrl-Shift-C` and `Ctrl-Shift-V` are keys of the terminal, not of dbui, and dbui cannot change them. Most terminals use them for copy and paste (on macOS usually `Cmd-C` and `Cmd-V`).

- **Paste:** dbui turns on bracketed paste, so a paste arrives as one block. A paste never sends the input, also when it contains line breaks. Press Enter to send.
- **Select and copy:** dbui captures the mouse for scrolling the Transcript, so a plain mouse drag does not select text. Hold **Shift** while you drag to let the terminal select (Windows Terminal documents this for applications that use the mouse; iTerm2 uses **Option**). Then press `Ctrl-Shift-C`. Inside a multiplexer such as tmux or Herdr, selection can differ; check its own documentation.
- **Copy a Result:** `/copy` sends the held query Result to the clipboard with OSC 52, see below. `/save <file>` writes it to a file.

These terminal behaviors are not checked by dbui's tests. `/help keys` lists them under **Copy and paste**.

## Cell width

In the `table` Format, a cell that is longer than `max-width` characters is cut and ends with `…`. The default is 60. Set `max-width` in `config.yaml`; `0` means no cut. Other Formats do not cut.

`/width <n|off>` changes the maximum cell width in the running Session. `/width 0` and `/width off` both mean no cut. The new value applies to Results that you run afterwards; earlier Results in the Transcript keep their text. `/width` is a Session setting: it does not change `config.yaml`, and Resume uses the `config.yaml` value.

## History

`/history` lists the History of the Session: one row per entry with its number (1 is the oldest) and its first line. An entry with more lines ends with ` …`. A long first line is cut by `max-width`. The list is an info Message block, so `/format` and `/orientation` do not change it.

`/history <number>` puts that entry into the editor, with the cursor at the start. It does not run the entry. `/history clear` removes all entries of this Session, in memory and in the Session's `history` file; other Sessions stay unchanged. `/history`, `/history <number>` and `/history clear` are not stored in the History.

## Copy a Result in Interactive mode

`/copy` sends the newest held query Result to the terminal clipboard, with no argument. It uses the current Format, Orientation and value settings, exactly as `/save` would write the Result. It does not copy screen padding, input, info or error Message blocks. Schema Results are not query Results for `/copy`; use `/save` for those.

The fixed limit is **64 KB (65,536 bytes) of formatted UTF-8 text, before base64 encoding**. Exactly 64 KB is allowed. Larger text is refused without sending anything; use `/save <file>` instead. A truncated Result copies only the held rows, and the info Message block names the original Row limit, even if `/limit` changed afterward.

With no Result or an empty Result, `/copy` shows an info Message block and changes no clipboard. A successful Statement that returns no Result also disables `/copy` until another query Result arrives; it does not change `/save` retention. Info, errors and Dump do not replace the held Result.

Copy uses **OSC 52 through the TamboUI backend only**. The terminal and any multiplexer, including Herdr, must allow OSC 52 clipboard writes. No external clipboard tool or fallback is used. The success Message block says the rows were **sent to the terminal clipboard**: dbui cannot confirm that the terminal accepted them. If paste does not work, check terminal permissions or use `/save`.

```text
select 7 as id;
/format csv
/copy
```

## Profiles in Interactive mode

Start `dbui` in a terminal without complete connection options to open **Edit Connection Profile**.
The dialog keeps the values you gave. Complete options, including an available password, connect directly.
Batch mode still fails when required values are missing; it never opens a dialog or prompt.

| Slash command | Action |
|---|---|
| `/connect [name\|number]` | List Profiles, or switch to one. With no saved Profiles, open the dialog. |
| `/profile list` | Show Profile names and targets without passwords. |
| `/profile add` | Open a new Profile dialog. |
| `/profile edit <name>` | Edit and save an existing Profile. Keep its name unchanged. |
| `/profile remove <name>` | Ask for confirmation. Press `y` to remove it. |

Dialog keys:

- `Tab` / `Shift-Tab`: move between fields and Save.
- `Enter`: move to the next field; on Properties or Save, save the Profile.
- `Ctrl-S`: save from any field.
- `Ctrl-G`: connect without saving from a connection dialog. `Ctrl-Enter` is an alias on terminals that distinguish it.
- `Esc`: cancel. A missing password opens a separate masked prompt; `Enter` connects and `Esc` cancels it.

An empty Read-only field keeps the current Session mode. At startup this comes from `config.yaml` (default: off).
A Profile with explicit `read-only: true` or `false` overrides the current mode when you connect.
`/readonly on|off` changes the mode for the Session; reconnect keeps it and Resume restores the earlier settings.
Driver properties use `key=value;key=value`.
Save writes the dialog values, including non-secret command-line overrides. Use Connect without saving for one-off overrides.
Passwords from command-line options, command-line URLs or the masked prompt are not saved.
Only a password typed into the dialog Password field may become a new saved password.
Existing Profile password references such as `${PG_PASSWORD}` stay literal.
Slash commands do not accept inline credentials. Credential-like `/connect` and `/profile` inputs are rejected before echo or history.
Names such as `api-token` work normally. Credential-shaped names containing a URL or password assignment must be renamed or selected by number.

Use `/help` for the combined Slash command and key list. By default, `Tab` requests Completion and `Alt-/` is the fallback.
Completion includes Slash command names and registry argument values. Enter accepts an open Completion candidate without sending input.
By default, Shift-Tab cycles Orientation in the editor. It always moves between fields while the Profile dialog is open.

Profiles live under the `profiles:` root in `profiles.yaml`, separate from `config.yaml`.
Use `--config-dir <directory>` to select a temporary config directory for testing.
Writes merge one Profile into fresh content under a file lock, then atomically replace the file with mode `600`.
A yellow info Message block warns about wider existing permissions. YAML comments do not survive a save.
