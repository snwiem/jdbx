# 0039: `/help` overview and topics

**What to build:** `/help` shows a short overview. `/help commands` and `/help keys` show everything, under group headings. `/help <word>` filters commands and key rows. `/help <Tab>` completes. Today `/help` is one flat block of 17 commands and about 28 key rows, which is hard to scan. Change request from the user.

**Blocked by:** none

**Status:** done

**Parent:** `docs/prd/mvp-1.0.md` (`/help`, issue 0012)

## Check result (feasibility)

- `SlashCommandRegistry.help(KeyBindings)` builds one text from the registered commands and a fixed key text block. Registered commands appear automatically; a new command must not vanish from the help.
- The key rows are a fixed text. The configured keys (newline, Orientation, Completion) are filled in with `formatted(...)`. For topics and a filter the rows need a structure: group, key text, description.
- Completion for `/describe` shows the pattern: the argument candidates come from the registry (`completionCandidates`) and `CompletionContext`.
- Existing tests expect that `registry.help()` contains every command and many key names. They must keep passing, or change with a reason.

## Decision (agreed with the user)

| Input | Result |
|---|---|
| `/help` | The overview (below). |
| `/help commands` | All commands under their group headings, with synopsis and description. |
| `/help keys` | All key bindings under their group headings, then the legacy terminal notes. |
| `/help <word>` | The commands and key rows whose name or description contains the word. Matching ignores case and is a plain substring, not a pattern. A leading `/` is allowed: `/help /describe`. Headings with no match are left out. |
| `/help <word>`, no match | Info block: `No help for "<word>". Try /help commands or /help keys.` |

- Groups are headings only. They are not arguments.
- `/help <Tab>` offers `commands`, `keys` and the command names (without `/`). It does not offer key names.
- The group of a command is stored once, with the command (`SlashCommand`). A command without a group goes to the group `Other`, so a newly registered command still appears.
- Command groups:
  - Query and results: `/format`, `/orientation`, `/width`, `/limit`, `/timeout`, `/save`, `/copy`, `/dump`
  - Schema: `/describe`, `/refresh`
  - Connection: `/connect`, `/profile`, `/readonly`
  - History and Sessions: `/history`, `/resume`
  - General: `/help`, `/quit`
- Key groups: Editing, History, Completion, Results and screen, Dialogs. The legacy terminal notes stay after the keys in `/help keys`.
- Overview draft (the keys come from the configured bindings):

```
dbui help. Use /help commands, /help keys, or /help <word>.

Query and results   /format /orientation /width /limit /timeout /save /copy /dump
Schema              /describe /refresh
Connection          /connect /profile /readonly
History, Sessions   /history /resume
General             /help /quit

Essential keys
Enter       Send input
Alt-N       Insert a newline
Tab         Request Completion
Ctrl-R      Search history
Ctrl-C      Cancel a running Statement, or clear the input
```

- The help is one Info block, as today. It does not depend on `/format` or `/orientation`.
- Use the terms of `GLOSSARY.md`.

## Proposed test seams (confirm before work)

1. Plain Unit tests on a new help catalog class (commands with groups, key rows from `KeyBindings`): overview, `commands`, `keys`, filter by name and by description, the `/` prefix, no match, headings left out, a command without a group, configured keys in the text. It misses Session wiring and Completion.
2. Session seam (no database): `/help`, `/help keys`, `/help history` through `submit`; `/help <Tab>` candidates through `Session.complete`.
3. Existing `SlashCommandRegistryTest`: keep or adapt the help assertions, with a reason for each change.

## Acceptance criteria

- [x] Unit test: `/help` shows the overview with every command name under its group and the 5 essential keys
- [x] Unit test: `/help commands` lists all commands under group headings with description
- [x] Unit test: `/help keys` lists all key rows under group headings, then the legacy notes
- [x] Unit test: `/help <word>` matches name and description, ignores case, accepts a leading `/`, and leaves out empty headings
- [x] Unit test: no match gives the Info text
- [x] Unit test: a command with no group appears under `Other`; configured keys appear in the text
- [x] Session test: `/help`, `/help keys` and `/help history` through `submit`
- [x] Completion test: `/help <Tab>` offers `commands`, `keys` and the command names, not key names
- [x] `README.md` documents `/help`
- [x] Manual check in a real terminal (done by the user)

## Findings

- New plain class `HelpCatalog` builds all texts. `SlashCommandRegistry.help(keys)` still returns the whole help (`HelpCatalog.full`), so older callers keep working.
- `SlashCommand` got a `group` field. The five-argument constructor puts a command in `Other`. `Session` assigns the groups in one map (`COMMAND_GROUPS`). A test checks that no Session command is in `Other`.
- Tests: `HelpCatalogTest` (unit), `HelpCommandSessionTest` (Session without a database, including Completion). Several older tests asked plain `/help` for synopsis lines or key rows. They now ask `/help commands` or `/help keys`, because the overview shows only names and five keys.
- A filter with a very common word matches many rows. `/help history` also finds the `Password Enter` row ("never store it in history"), because the filter matches descriptions.
- The overview draft showed `Alt-N`; the default newline key is `Ctrl-J`. The overview uses the configured key.
- The group width in the overview is the longest group name plus two spaces.
- Added after the first manual check (user request): the key group **Copy and paste** in `/help keys` (Shift + mouse drag, `Ctrl-Shift-C`, `Ctrl-Shift-V`, `/copy`) and a README section. These keys belong to the terminal. dbui captures the mouse, so a drag needs Shift (Windows Terminal) or Option (iTerm2) to select. Checked against the Windows Terminal selection documentation and the iTerm2 documentation; the GNOME Terminal and Konsole claims, and the multiplexer behavior, are not verified. No dbui test can check terminal behavior; the user tests it by hand.
