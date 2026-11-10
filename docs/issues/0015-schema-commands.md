# 0015: Schema commands

**What to build:** `/schema`, `/tables` and `/describe <table>` show schemas, tables and views, and the columns and keys of one table. Data comes only from JDBC `DatabaseMetaData` (ADR 0005). Metadata is cached and can be refreshed.

**Blocked by:** 0008 Interactive tracer

**Status:** done

**Parent:** `docs/prd/mvp-1.0.md`

- [x] Integration tests with a small test schema
- [x] `/describe` shows column name, type, nullable, default, primary and foreign keys
- [x] A refresh command or key reloads the cache
- [x] Output uses the current Format and Orientation
- [x] Large schemas do not block the editor for long. Loading shows a running indicator
