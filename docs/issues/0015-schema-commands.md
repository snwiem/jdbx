# 0015: Schema commands

**What to build:** `/schema`, `/tables` and `/describe <table>` show schemas, tables and views, and the columns and keys of one table. Data comes only from JDBC `DatabaseMetaData` (ADR 0005). Metadata is cached and can be refreshed.

**Blocked by:** 0008 Interactive tracer

**Status:** done

**Parent:** `docs/prd/mvp-1.0.md`

- [x] Integration tests with a small test schema
- [x] `/describe` shows column name, type, nullable, default, primary and foreign keys
- [x] A refresh command or key reloads the cache
- [x] Output uses the Transcript's Result rendering. Rendering with the current Format and Orientation moves to issue 0012

Note: `/tables` and `/describe` use the connection's current schema, not the full `search_path`. Other schemas need `schema.table`.
- [x] Large schemas do not block the editor for long. Loading shows a running indicator

Note (issue 0037): `/schema` and `/tables` were removed. `/describe` covers them.
