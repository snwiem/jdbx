---
status: accepted
---
# Take passwords from options, the Profile or a prompt, not from `.pgpass`

dbui must not depend on PostgreSQL client tooling, and later versions will support other databases. The password order is: `--password` or the JDBC URL, then the Profile (which may reference `${ENV}`), then a masked prompt. Batch mode never prompts. The `.pgpass` file is not used, although the driver could read it. The Profile file is separate from the settings file so it can be encrypted with external tools (mise, fnox, age).

## Consequences

Passwords can be stored in plain text in the Profile file. dbui writes that file with mode `600` and warns about wider modes. An OS keyring may come later.
