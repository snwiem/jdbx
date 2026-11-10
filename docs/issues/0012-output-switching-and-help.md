# 0012: Output switching and `/help`

**What to build:** In Interactive mode `/format` and `/orientation` change the output, and a cycle key toggles the Orientation. The status bar shows both. `/help` lists Slash commands and key bindings.

**Blocked by:** 0005 Vertical Orientation, 0010 Execution controls

**Status:** done

**Parent:** `docs/prd/mvp-1.0.md`

- [x] Each command confirms the change with an info band
- [x] The cycle key toggles Orientation and updates the Status bar
- [x] Invalid values give an error band that lists the valid values
- [x] `/help` lists every Slash command and every key binding
- [x] Slash commands are picocli subcommands and expose argument Completion candidates for on-request Completion. Issue 0016 now connects these candidates to the Completion popup
- [x] Interactive mode starts with the Format and Orientation from the command-line options (`--format`, `--orientation`), then from `config.yaml`, then the defaults
- [x] New Results use the current Format, Orientation and formatter options from `config.yaml`; earlier Results keep their rendered text
- [x] Format and Orientation persist immediately in Session metadata and Resume restores them
- [x] Schema Results (`/schema`, `/tables` and all `/describe` tables) use the current Format and Orientation. This closes the criterion moved from issue 0015

## Implementation decisions

- A Session owns one `SlashCommandRegistry` and one picocli command tree. All existing Slash commands use it. Unknown commands still produce an error band.
- Arguments are not shell words. The registry passes the entire argument text to picocli as one literal positional argument. SQL quotes, internal whitespace, `--` and `@file` remain literal. Argument-file expansion is disabled.
- A Result keeps both its data and the text rendered when it entered the Transcript. Shared batch formatters produce the text, including JSON. Terminal width handling stays in the renderer. A null text marks an unformatted adapter Result; an empty formatted Result stays empty.
- Shift-Tab cycles Orientation. Ctrl-O is the documented fallback. Both live in `EditorKeys`, ready for issue 0018. The key action changes and persists Session settings without input echo or history entries, and preserves history navigation and the editor draft.
- Legacy metadata without output settings resumes as `table` / `horizontal`, matching the earlier Transcript behavior. Invalid stored settings fail before a fork. The earlier Session stays unchanged.

## Registry: how to add a command

1. Add one `SlashCommand` in `Session.commands()`.
2. Supply its name and one-line help; use a null argument for commands without arguments.
3. For an argument, use `SlashCommand.argument(label, arity, candidates, converter)`; the converter is optional.
4. Set `background` to true for work that can block; the Session runs it on its existing executor.
5. The action receives validated argument text and returns Message blocks, or calls a Session state action.
6. Help and usage come from picocli; `session.slashCommands().completionCandidates(name)` supplies the Completion source.

## Verification

- Session Integration tests cover switching, all five Formats, invalid values, frozen earlier Results, schema Results, quoted SQL names, persistence, Resume, legacy metadata and history rules.
- Pure registry Unit tests cover generated help, every registered command, key help, converters, missing and extra arguments, repeated parsing, literal argument text and Completion candidates.
- Pilot and `Frame.forTesting` tests cover both cycle keys, busy input, draft/history preservation, Status bar text, JSON display and an empty frozen CSV Result.
- Python `pty` plus `pyte` checked the packaged app against the PostgreSQL from `compose.yaml`. Checks covered CLI > config > defaults, `/help`, JSON, vertical CSV, `/tables`, formatter options, invalid values, both cycle keys, draft/history preservation, restart and Resume, and alternate-screen restoration. Artifacts: `/tmp/dbui-0012-pty-c5u1rffw`; individual CLI overrides: `/tmp/dbui-0012-pty-yoi7mweg`; helper: `/tmp/dbui-0012-pty-check.py`; venv: `/tmp/dbui-0012-pyte`.
- Cross-family read-only reviews used Claude Opus 5.5. The follow-up found no concrete code blockers and confirmed the fixes. The issue update and Completion scope clarification are recorded here.
- Full `./mvnw -q verify` passed: 168 Unit tests and 125 Integration tests, with no failures, errors or skips. The delegated QA run and the host acceptance run both passed. Logs: `/tmp/dbui-0012-final-verify.log` and `/tmp/dbui-0012-accept-verify.log`. The parent also reran registry and terminal Unit tests and checked the XML totals and `git diff --check`.

### TDD evidence and limits

- Registry introduction, `/help`, JSON switching, Orientation switching, Transcript JSON display, Status bar, persistence, invalid stored settings, required arguments and review fixes each had an observed failing check before the corresponding production change.
- The first registry, converter and cycle-action checks failed compilation because their public API did not exist. Later registry tests failed on behavior, including picocli argument-file expansion and missing required arguments.
- The real pseudo-terminal startup check failed with configured `markdown` / `horizontal` despite CLI `csv` / `vertical`. It passed after the `BatchCommand` change.
- Schema-format, additional Format, quoted-name and legacy-metadata checks were added after the shared implementation and passed immediately. They are regression coverage, not claimed as separate red-before-green cycles.
- Existing test assertions did not need parsing/error-text changes. New tests document picocli usage/error text. One new table expectation initially assumed a border; it was corrected to the existing borderless formatter output. No database-free test was renamed to an Integration test.

## Scope and remaining checks

- The Completion popup, Ctrl-Space and Alt-/ wiring are integrated from issue 0016, including registry-based Slash command names and arguments. Configurable keys remain in issue 0018.
- The existing public `SchemaCommandLine` compatibility adapter and its unchanged tests remain. Session dispatch no longer uses that parser.
- Automated pseudo-terminal checks do not replace human judgment of colors, flicker, resizing or how Herdr forwards keys. No unrelated Herdr pane was accessed.
- Non-blocking review notes: one-time Result formatting holds the Session lock, so unusually large Row limits may delay status/cancel calls. Long connection labels may clip output settings in a narrow Status bar. The latter was a review expectation, not a reproduced failure.

## Manual test list

1. Start Interactive mode and run `/help`. Check command usage and the send, newline, history, cancel, Orientation and scrolling keys.
2. Run `/format json`, then `select 7 as id;`. Check JSON text and the Status bar. Earlier Results must stay unchanged.
3. Run `/orientation vertical`, then `/format table` and a two-column Statement. Try Shift-Tab and Ctrl-O, including while browsing history with a draft.
4. Try `/schema`, `/tables` and `/describe <table>` with CSV or vertical Orientation. Try invalid Format and Orientation values.
5. Restart with `--format csv --orientation vertical` while config sets other values. Run a Statement, then test `/resume` restores the earlier output settings.
