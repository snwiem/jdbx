---
status: accepted
---
# Rename the application from dbui to jdbx

The application started as `dbui`. We rename it to `jdbx`. The new name says that this is a Java tool built on JDBC, and it keeps the database purpose and the interactive character visible. The rename changes the artifact, the main class, the package, the docs, `GLOSSARY.md` and the launcher. This ADR records the decision only. A separate plan and issue cover the work.

## Where we come from

`dbui` was a working name. It stands for "database UI". It was short and easy to type, so we kept it through the first 40 issues, the PRD, the ADRs and the glossary.

## Why change the name

- `dbui` does not show that the tool is a Java application (Spring Boot, see ADR 0003) or that it is built on the JDBC abstraction. JDBC is the core idea of the tool.
- `dbui` suggests a graphical UI. The tool is a terminal UI.
- The name is still cheap to change. The tool is new and has no external users. The cost grows with every issue and every release.

## Requirements for the new name

1. Short, and fast to type in a console.
2. Reflects the Java platform.
3. States the database interface purpose.
4. Does not stress the PostgreSQL relation. Other databases may follow.
5. Can hint at the terminal UI and interactivity.

## Considered Options

We brainstormed 12 names, then ranked the favourites. The user ranking was `jdbx`, `jsql`, `jdx`, `dbj`, `dbsh`.

| Name | Idea | Result |
|---|---|---|
| `jdbx` | **J**ava + **DB** + e**X**ecute/e**X**plore | **Chosen.** |
| `jsql` | Java + SQL | Rejected. Many known projects use it (JSqlParser, jSQL Injection and others). Likely to be confused. |
| `jdx` | Java + data e**X**plorer | Rejected. `jdx` is the GitHub name of the author of `mise`, a tool we use. Says "database" only weakly. |
| `dbj` | DB + Java | Rejected. Weak on the interactive character. |
| `dbsh` | DB + shell | Rejected. Loses the Java link. Suggests a plain shell, not a terminal UI. |
| `jdb` | Java DB | Rejected. `jdb` is the Java debugger. |
| `jdbt`, `javaql`, `jdbc-tui`, `jsh`, `jdbi`, `jqx`, `spql` | – | Dropped early. They are longer, unclear about the database, or collide with known tools: `jsh` with JShell, `jdbi` with the JDBI library. |

## Why jdbx

- **Java:** the `j` prefix.
- **JDBC:** it looks and sounds like JDBC. This matches the core idea of the tool, to use the JDBC abstraction.
- **Database:** "db" is visible to people who do not know JDBC.
- **Interactivity:** `x` is a common symbol for "execute" (as in the Linux file mode) and suggests action. It also suggests "explore".
- **No Postgres link** in the name.
- **Short:** four letters, easy to type.

## Collision checks (2026-11-12)

- `jdbx` is not in `PATH` on the development machine.
- On GitHub only a few small repositories use the name. They have 0 stars, and none is a command line tool. One is a "JDBC refactored" library.
- Not verified: Maven Central (query returned nothing), Homebrew (HTTP 503), apt, npm, PyPI. Check them before the first public release.

## Consequences

- All uses of `dbui` change: Maven artifact, main class, Java package, wrapper script and jar name, user config and session directories, environment variables, docs, ADR text and `GLOSSARY.md`.
- Existing user data under the old name needs a decision (migrate or ignore). The rename plan must cover this.
- Old ADRs and issue files keep their history. The plan decides whether text in them changes or a note points to this ADR.
- Git history keeps the old name. Commits that contain `dbui` stay unchanged.
