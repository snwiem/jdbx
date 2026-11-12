# 0038: `/history` command

**What to build:** `/history` lists the input history of the Session with numbers. `/history <#>` loads one entry into the editor. `/history clear` empties the history. Ctrl-R finds an entry only if the user remembers part of it; a list lets the user look it up. Change request from the user.

**Blocked by:** none

**Status:** in review (manual check open)

**Parent:** `docs/prd/mvp-1.0.md` (Sessions and history, stories 68 to 74)

## Check result (feasibility)

- `Session.history()` returns the entries of `HistoryStore`, oldest first. Resume copies them into the new Session.
- `HistoryStore` does not store a blank entry, an entry that starts with a space, `/resume`, `/quit`, an input with Profile credentials, or a repeat of the last entry.
- The editor is cleared when the user presses Enter, so `/history <#>` never replaces a draft: the editor is empty at that moment.
- `Session.Update` has only Message blocks and an `ended` flag. Loading text into the editor needs a new field in the Update (editor text), which the terminal layer applies with the cursor at the start (as `setEditor(Completion.Edit)` does).
- `InteractiveMode` hands `session.history()` to the screen after the Session changed. `/history clear` must reach the screen the same way, so that Up and Ctrl-R no longer find cleared entries. Checked: `InteractiveMode.refresh()` hands `session.history()` to the screen after every Update, so no extra code was needed.

## Decision (agreed with the user)

- `/history` lists all entries, Statements and Slash commands. A filter may come later (`config.yaml` or Slash command). Not in this issue.
- Number 1 is the oldest entry. The numbers do not change while the Session runs. The newest entry is at the bottom.
- Each entry is one row: the number and the first line. If more lines follow, ` …` ends the row. A long first line is cut by the `max-width` setting. `/history <#>` loads the full text.
- The list is one Info block of plain text, not a Result. `/format` and `/orientation` do not change it.
- `/history <#>` puts the whole entry into the editor and puts the cursor at the start of the text. It does not run the entry. It adds no Message block, except for an error.
- `/history` and `/history <#>` and `/history clear` are not stored in the history, like `/resume` and `/quit`.
- `/history clear` removes all entries of this Session, in memory and in the `history` file. The next stored input gets number 1. It needs no confirmation. Other Sessions stay unchanged, also the Session that was resumed from. It shows an Info block `History cleared (N entries).`
- Edge cases:
  - Not a whole number of 1 or more, or an unknown word: error `Usage: /history [<number>|clear]`.
  - A number above the entry count: error `No history entry 99. The history has 12 entries.`
  - Empty history: Info block `The history is empty.` (also for `/history clear`).
  - History disabled: Info block that says the history is off.
- Completion for the argument offers `clear`.
- `/help` and Completion get the command from the shared registry.
- Use the terms of `GLOSSARY.md`. Add the term **History** there if it is missing.

## Proposed test seams (confirm before work)

1. Plain Unit test on the history command logic (a small class that formats the list and picks an entry from a list of strings). It catches numbering, first-line cut, ` …` marker, `max-width`, all edge cases and error texts. It misses Session wiring.
2. Session seam in an integration test or a Session test with a temporary state directory: `/history`, `/history <#>`, `/history clear`, not stored in the history, the Update carries the editor text, the `history` file is empty after clear. It misses terminal behavior.
3. Terminal glue, thin: a unit test that the screen puts the loaded text in the editor with the cursor at the start, and that Up and Ctrl-R no longer find cleared entries. Real terminal behavior is a manual check.

## Acceptance criteria

- [x] Unit test: the list numbers entries from 1 (oldest) and shows the first line, with ` …` for a multi-line entry
- [x] Unit test: a long first line is cut by `max-width`
- [x] Unit test: the error and info texts for a bad argument, a number out of range, an empty history and a disabled history
- [x] Session test: `/history <#>` returns the full entry as editor text and no Message block
- [x] Session test: `/history`, `/history <#>` and `/history clear` are not stored in the history
- [x] Session test: `/history clear` empties the history and the file, numbering restarts at 1, a resumed-from Session is unchanged
- [x] Unit test: the loaded text is in the editor and the cursor is at the start
- [x] Unit test: after `/history clear`, Up and Ctrl-R find nothing
- [x] `/help` lists `/history`; Completion offers `clear`
- [x] `GLOSSARY.md` and `README.md` name the command
- [ ] Manual check in a real terminal

## Findings

- Tests: `HistoryCommandTest` (logic, no Session), `HistoryCommandSessionTest` (Session without a database: a Session with a context and no connection still stores the history), and two cases in `InteractiveScreenTest`.
- Placement: the load of the editor text is `InteractiveScreen.editorText`, called from `InteractiveMode` when `Update.editorText()` is set.
- The input echo of `/history <number>` still appears in the Transcript, like for every Slash command. Only the result is empty.
