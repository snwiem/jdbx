---
status: accepted
---
# Store each Session as a directory and resume by fork

Several dbui instances run in parallel, so each Session gets its own directory with history, log and Session metadata. `/resume` copies an earlier Session into a new one (fork) and never appends to the old one. This avoids lock files and conflicts when two instances resume the same Session. A global history file and a global log were rejected because parallel instances would mix or overwrite them.

## Consequences

Session directories accumulate, so a configured count removes the oldest. Session metadata never contains the password.
