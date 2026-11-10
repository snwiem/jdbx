---
status: accepted
---
# Read schema information only through JDBC `DatabaseMetaData`

Schema commands and Completion use `DatabaseMetaData` (schemas, tables, views, columns, keys). PostgreSQL catalog queries would give more detail (triggers, enums, partial indexes, view definitions) but tie the code to PostgreSQL. dbui is a tool for quick statements, not for deep database inspection, so the standard API is enough.

## Consequences

Deep inspection features are out of scope. Calls on very large schemas may be slow, so metadata is cached and refreshed on request. Support for other databases stays cheap.
