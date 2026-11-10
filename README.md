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
