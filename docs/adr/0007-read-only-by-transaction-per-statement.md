---
status: accepted
---
# Enforce Read-only with a READ ONLY transaction per Statement

`Connection.setReadOnly(true)` alone does not stop writes in the PostgreSQL driver while autocommit is on (finding of issue 0006). The driver property `readOnlyMode=always` makes the driver run `SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY`, but the user can lift that with `SET default_transaction_read_only = off`, `SET SESSION CHARACTERISTICS ... READ WRITE` or `BEGIN READ WRITE` (proved in `ReadOnlyIT` before the fix).

dbui therefore sets `readOnlyMode=always` on every connection **and** runs each Statement in its own explicit `READ ONLY` transaction (autocommit off, rollback afterwards) in Interactive mode and Batch mode. An explicit `BEGIN READ ONLY` ignores `default_transaction_read_only`.

## What it stops

`INSERT`, `UPDATE`, `DELETE`, `TRUNCATE`, DDL, writing CTEs and `SET default_transaction_read_only`, `SET SESSION CHARACTERISTICS`, `BEGIN READ WRITE` followed by a write. Each fails with SQLSTATE 25006.

## What it does not stop

- It is a guard against mistakes, not a security boundary. A database role with enough rights can still change data through other channels. Use a database role without write rights where that matters.
- `SET TRANSACTION READ WRITE` as the first command of a transaction is accepted by the server. It only affects the one-Statement transaction that holds it, so nothing can be written.
- Functions that are `VOLATILE` and write through a separate connection (for example `dblink`) are not stopped.
- Statements that cannot run in a transaction block (`VACUUM`, `CREATE DATABASE`) fail in read-only mode.
- User transactions (`BEGIN ... COMMIT` over several inputs) do not span inputs in read-only mode.
