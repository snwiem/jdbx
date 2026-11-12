# 0036: `/width` command

**What to build:** `/width <n|off>` changes the maximum cell width of the `table` Format in the running Session. Today `max-width` is set only in `config.yaml`, so a user who wants to read a whole text value must edit the file and restart. Change request from the user.

**Blocked by:** none

**Status:** open

**Parent:** `docs/prd/mvp-1.0.md` (Format options; issue 0020 added `max-width` to `config.yaml`)

## Check result (feasibility)

- `ValueRenderer.cut` cuts a cell to `maxWidth` characters and appends `…`. `0` means no cut. It applies only to the `table` Format; `markdown` does not cut.
- The default is `Settings.DEFAULT_MAX_WIDTH` (60).
- `/limit` and `/timeout` show the pattern: a `SlashCommand` in `Session.commands()`, a method in `ExecutionControls`, and a `Settings.withX(...)` copy.

## Decision (proposed, confirm before work)

- `/width <n|off>`: `n` is a whole number of 0 or more. `0` and `off` are the same: no cut (`maxWidth` 0). Other input gives an error band with the usage text, as `/limit` does.
- The command changes the Settings for new Results only. It does not reformat earlier Results in the Transcript, as with `/format`.
- Success shows an info block, for example `Maximum cell width: 40` or `Maximum cell width: off` (also for `/width 0`).
- It is a Session setting and does not change `config.yaml`. Resume uses the value from `config.yaml`, not the earlier Session's value. This keeps `SessionMetadata` unchanged.
- No new "cut" marker. The `…` stays the only sign of a cut. Decided by the user.
- No `/show` command for single values. Decided by the user.
- `/help` and Completion get the command from the shared registry. Completion offers `off`.
- `README.md` and the `max-width` text in the docs name `/width`.

## Acceptance criteria

- [ ] Unit test: a number sets `maxWidth`; `0` and `off` both set 0; a negative value, text or a missing argument gives an error and keeps the setting
- [ ] Unit test: after `/width off` a long text value shows in full in `table`; after `/width 10` it is cut with `…`
- [ ] Integration test at the Session seam: `/width` changes the next Result only
- [ ] `/help` lists `/width`; Completion offers `off`
- [ ] `README.md` documents `/width`
- [ ] Manual check in a real terminal
