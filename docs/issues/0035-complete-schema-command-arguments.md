# 0035: Complete schema command arguments

**What to build:** Completion suggests table, view and schema names after `/describe` and schema names after `/tables`. Today Tab shows nothing there. Found while testing by hand (bug report from the user).

**Blocked by:** none

**Status:** done

**Parent:** `docs/prd/mvp-1.0.md` (Completion of table names and Slash command arguments)

## Cause

- `Session.schemaCommand(...)` gives `/describe` and `/tables` a static, empty candidate list.
- For input that starts with `/`, `CompletionContext` sets the position to `SLASH`. `SchemaSource` returns nothing for `SLASH`.
- `/connect` works because its argument candidates come from a live supplier.

## Decision (proposed, confirm before work)

- `SchemaSource` answers in the `SLASH` position, chosen by the command name. It reads only the cached `SchemaModel`. No new JDBC code.
- `/describe <word>`: tables and views of the default schema, plus schema names.
- `/describe <schema>.<word>`: tables and views of that schema. Only the part after the dot is replaced.
- `/tables <word>`: schema names.
- Quoted names work: a name that needs quotes is inserted quoted, as in SQL Completion.
- `/describe` and `/tables` read their argument as one raw text, so a quoted name may hold spaces.
- `/schema all` keeps its static candidate.

## Acceptance criteria

- [x] Unit test: `/describe us` suggests tables and views of the default schema and schemas that start with `us`
- [x] Unit test: `/describe public.us` suggests tables of `public` and replaces only `us`
- [x] Unit test: `/tables pu` suggests schemas only
- [x] Unit test: a name that needs quotes is inserted quoted
- [x] Unit test: `/describe` after a second word, or an unknown command, suggests nothing
- [x] Integration test: Tab after `/describe ` in a Session lists the tables of the test schema
- [x] `/help` text stays correct
- [x] Manual check in a real terminal that Tab completes after `/describe` (done by the user)
