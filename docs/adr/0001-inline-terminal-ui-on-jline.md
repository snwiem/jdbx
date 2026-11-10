---
status: accepted
---
# Build the interactive mode inline on JLine

Interactive mode renders into the normal terminal scrollback, with a multi-line editor and a status bar at the bottom, like the pi agent. We build it directly on JLine, with picocli for Slash commands. A full-screen TUI was rejected: it removes native scrollback, copy and paste, and needs custom widgets.

## Considered Options

- **TamboUI** (full-screen widgets): its inline mode uses the alternate screen by default, which removes native scrollback.
- **Spring Shell**: its TUI layer is experimental, and its command model would compete with picocli.
- **Plain JLine plus picocli**: chosen. Full control of editor keys, status bar and scrollback.

## Consequences

Features that need panes (a result viewer, a schema tree) cannot be added inline. A temporary full-screen pager may come later. JLine behavior must be confirmed with a spike before the main build.
