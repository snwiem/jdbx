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

An empty Read-only field follows the global default in `config.yaml`. Driver properties use `key=value;key=value`.
Save writes the dialog values, including non-secret command-line overrides. Use Connect without saving for one-off overrides.
Passwords from command-line options, command-line URLs or the masked prompt are not saved.
Only a password typed into the dialog Password field may become a new saved password.
Existing Profile password references such as `${PG_PASSWORD}` stay literal.

Profiles live under the `profiles:` root in `profiles.yaml`, separate from `config.yaml`.
Use `--config-dir <directory>` to select a temporary config directory for testing.
Writes merge one Profile into fresh content under a file lock, then atomically replace the file with mode `600`.
A yellow info Message block warns about wider existing permissions. YAML comments do not survive a save.
