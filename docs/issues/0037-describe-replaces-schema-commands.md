# 0037: `/describe` replaces `/tables` and `/schema`

**What to build:** `/describe` becomes the only command to look at schemas, tables and views. `/tables` and `/schema` are removed. Change request from the user: one command that does the same job is easier to remember.

**Blocked by:** 0035 (merged to `main`; this issue changes its `/tables` Completion branch)

**Status:** open

**Parent:** `docs/prd/mvp-1.0.md`; replaces parts of issue 0015

## Decision (agreed with the user)

| Input | Result |
|---|---|
| `/describe` (no argument, or only spaces) | One table with the columns `name` and `type`. Rows: the non-system schemas (type `schema`), then the tables and views of the current schema (type `table` or `view`). |
| `/describe <schema>` | The tables and views of that schema, with `name`, `type` and `remarks` (as `/tables` shows today). |
| `/describe <schema>.` (trailing dot) | Same as `/describe <schema>`. It is the way to list a schema when its name is also a table name. |
| `/describe <table>` | The table description, as today (columns, keys, indexes). |
| `/describe <schema>.<table>` | The same for a table in another schema. |
| `/describe <name>` where `<name>` is a schema and also a table of the current schema | An error: the name is both a schema and a table. It says to use `<name>.<name>` for the table and `<name>.` for the schema. |
| `/describe <name>` where `<name>` is neither | `Unknown table: <name>` as today. Also when it is a schema name only, it lists the schema. |

- Table output keeps the same columns as today (user decision).
- System schemas (`pg_catalog`, `information_schema`, `pg_toast`) are not in the overview and not in Completion. A typed name such as `/describe pg_catalog` still lists the schema.
- Remove `/tables`, `/schema` and the candidate `all`. `/refresh` stays.
- Remove the `/tables` branch from `SchemaSource` (issue 0035). `/describe` Completion stays: tables, views and schemas; the popup already shows the kind. After `schema.` it offers that schema's tables, so selection from the schema stays possible.
- Update `/help`, `README.md`, issue 0015 (a short note that 0037 replaced `/tables` and `/schema`) and the PRD.
- Use the terms of `GLOSSARY.md`.

## Acceptance criteria

- [ ] Unit test: `/describe` with no argument or only spaces lists schemas, tables and views with `name` and `type`
- [ ] Unit test: `/describe <schema>` lists that schema's tables and views with remarks
- [ ] Unit test: `/describe <schema>.` lists the schema's tables
- [ ] Unit test: a name that is both a schema and a table of the current schema gives the error; `<name>.<name>` describes the table; `<name>.` lists the schema
- [ ] Unit test: an unknown name gives `Unknown table: <name>`
- [ ] Unit test: `/tables` and `/schema` are unknown Slash commands; `/describe` Completion no longer shows `all`
- [ ] Integration test at the Session seam with a small test schema
- [ ] `/help` and `README.md` list `/describe` and not the removed commands
- [ ] Manual check in a real terminal
