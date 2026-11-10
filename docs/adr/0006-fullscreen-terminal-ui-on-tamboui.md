---
status: accepted
---
# Build the interactive mode fullscreen on TamboUI

Interactive mode owns the whole terminal window (alternate screen) like the pi agent in its default fullscreen mode. The editor and the status bar stay fixed, and the app scrolls its own transcript. It is built on TamboUI, with JLine only as the terminal backend, and picocli for Slash commands. This supersedes ADR 0001. The spike of issue 0001 showed that an inline editor cannot stay fixed while the user scrolls, because the terminal owns the scrollback. After a taller resize (for example when a multiplexer pane is unsplit) it also leaves an empty gap between editor and status bar. Both problems disappear when the app owns the viewport.

## Considered Options

- **Inline on JLine** (ADR 0001): native scrollback and copy, but the editor scrolls away and the resize gap stays.
- **Fullscreen on plain JLine** (`Display`): same behavior, but we would write layout, diff rendering, table and scroll widgets ourselves.
- **Fullscreen on TamboUI**: chosen. It provides layout, diff rendering, table, scrollbar, mouse events and a test tool (Pilot).

## Consequences

- Native scrollback and plain mouse selection are lost. The app must handle wheel scrolling, selection and copy, or rely on `Shift`-drag with mouse capture on. Issue 0019 checked mouse capture and OSC 52 at byte level; native `Shift`-drag and OSC 52 in Herdr need a human check (see its Findings).
- TamboUI 0.7.0 has a multi-line `TextArea`. We add send and newline keys and the height logic. Spike 0019: legacy key parsing cannot tell `Ctrl-J` from `Enter`; use the Kitty protocol (active in Herdr) or a thin backend wrapper.
- TamboUI is young (0.7.0, "experimental"). The UI stays behind a thin boundary so plain JLine remains a fallback.
- The Session log in the terminal disappears on exit. Whether to print the transcript on exit is open (issue 0019).
- Spike 0019 result: all capabilities work or have small fixes, including Herdr split and unsplit. Decision stays: TamboUI.
