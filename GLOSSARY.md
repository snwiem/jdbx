# dbui

A terminal tool to run SQL statements against a database over JDBC and read the results. It stays open for long sessions, or runs once in batch mode.

## Modes

**Interactive mode**:
The mode where dbui stays open, shows a fixed editor and a Status bar and scrolls a Transcript, and runs Statements and Slash commands you enter.
_Avoid_: shell, REPL, TUI

**Batch mode**:
The mode where dbui runs Statements from the command line, a file or stdin, writes the Results to stdout, and exits.
_Avoid_: script mode, one-shot mode

## Connecting

**Profile**:
A named, saved set of connection settings (URL or host, port, database, user, password, read-only, driver properties). It lives in the profiles file.
_Avoid_: connection, bookmark, favorite

**Read-only**:
A setting of a Profile (with a global default) that stops the Session from changing data.
_Avoid_: safe mode, write protection

## Sessions

**Session**:
One run of dbui with its own connection, settings, input history and log. Several Sessions can run in parallel.
_Avoid_: instance, window

**Resume**:
Starting a new Session as a copy (fork) of an earlier Session: same Profile values, settings and input history. The earlier Session stays unchanged.
_Avoid_: continue, reattach, restore

## Input

**Statement**:
One SQL statement, ended by `;` or by the end of the input.
_Avoid_: query, command

**Slash command**:
An input that starts with `/` and controls dbui itself (for example `/format`, `/connect`). It is not sent to the database.
_Avoid_: meta command, backslash command

**Completion**:
A list of suggested keywords and schema names that opens only when the user asks for it.
_Avoid_: autocomplete, suggestions

## Output

**Result**:
The rows and columns that the database returns for one Statement.
_Avoid_: result set, output

**Row limit**:
The maximum number of rows dbui reads for one Result in Interactive mode. dbui applies it, not the SQL.
_Avoid_: max rows, LIMIT clause

**Format**:
How a Result is written: `table`, `markdown`, `csv`, `tsv` or `json`.
_Avoid_: output type, mode

**Orientation**:
Whether a Result is laid out horizontally (one row per line) or vertically (one column per line). It applies to `table`, `markdown`, `csv` and `tsv`, not to `json`.
_Avoid_: layout, transpose, view

**Save**:
Writing the Result that is currently held in memory to a file.
_Avoid_: export

**Dump**:
Running a Statement again and writing all its rows to a file, without the Row limit.
_Avoid_: export, backup

## Screen

**Message block**:
A full-width entry in the Transcript with its own background color and padding, and no border. Its kind is input echo (gray), result, info (yellow) or error (red). The Transcript is the log of the Session.
_Avoid_: notification, alert, log line

**Transcript**:
The scrollable area above the editor that holds the Message blocks of a Session. dbui scrolls it, while the editor and the Status bar stay fixed.
_Avoid_: scrollback, output pane, log

**Status bar**:
The area under the editor (one or more lines) that shows the current connection, read-only state, Format, Orientation, Row limit and last Result summary.
_Avoid_: footer, header
