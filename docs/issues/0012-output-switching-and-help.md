# 0012: Output switching and `/help`

**What to build:** In Interactive mode `/format` and `/orientation` change the output, and a cycle key toggles the Orientation. The status bar shows both. `/help` lists Slash commands and key bindings.

**Blocked by:** 0005 Vertical Orientation, 0010 Execution controls

**Status:** ready-for-agent

**Parent:** `docs/prd/mvp-1.0.md`

- [ ] Each command confirms the change with an info band
- [ ] The cycle key toggles Orientation and updates the status bar
- [ ] Invalid values give an error band that lists the valid values
- [ ] `/help` lists every Slash command and every key binding
- [ ] Slash commands are picocli subcommands and show completion for their arguments on request
- [ ] Interactive mode starts with the Format and Orientation from the command-line options (`--format`, `--orientation`), then from `config.yaml`, then the defaults. Today the Transcript always uses the `table` formatter and ignores them (finding from manual testing of issue 0008)
- [ ] A Result in the Transcript is rendered with the current Format and Orientation, and the formatter options from `config.yaml` apply
