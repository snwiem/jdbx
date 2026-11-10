# 0016: Completion on request

**What to build:** `Ctrl-Space` (configurable) opens Completion with SQL keywords and schema, table and column names. Completion never opens by itself. Suggestions come from the text near the cursor: after `FROM` or `JOIN` tables, after `table.` columns. No full SQL parsing.

**Blocked by:** 0019 TamboUI spike, 0015 Schema commands

**Status:** done

**Parent:** `docs/prd/mvp-1.0.md`

- [x] Unit tests for the Completion engine without a terminal
- [x] Nothing opens while the user types
- [x] Quoted identifiers and mixed case are handled
- [x] Completion works from the cache and does not query the database on each key press
- [x] A few Pilot tests for the key binding

## Behavior

- **Keys:** `Ctrl-Space` opens Completion. Some terminals do not send it, so `Alt-/` is the documented fallback. Both live in `EditorKeys` (`complete`, `completeAlternate`) for issue 0018.
- **Popup:** at most 8 rows, drawn over the bottom of the Transcript, just above the editor. Each row shows the name and its kind. `▲` and `▼` mark more rows. `Up` and `Down` select (they wrap), `Tab` or the send key accepts, `Esc` closes. Any other key closes the popup and then works as usual. The send key does not send while the popup is open.
- **Result of a request:** no candidates give the note `no completions`. One candidate is inserted. Several candidates insert their longest common prefix first (if it is longer than the typed word) and open the popup.
- **Loading:** the request runs on the Statement thread, so it never overlaps a running Statement. The first request loads the schema cache (a few metadata queries). Until then the Status bar shows a yellow `loading schema…`; the popup opens when the data is ready. A key press during loading cancels the request. `/refresh` clears the cache, so the next request loads it again. A request after the cache is loaded sends no query to the database, including the current schema (`SchemaProvider.currentSchemaCached`).
- **Context rules** (nearby text of the Statement under the cursor, no parsing): after `FROM`, `JOIN`, `INTO`, `UPDATE`, `TABLE` or a `,` in a `FROM` list: tables, views and schemas. After `schema.` there: tables of the schema. After `name.` elsewhere: columns of an alias or table in the Statement, or of a table in the current schema, else tables of a schema. After `SELECT`, `WHERE`, `ON`, `GROUP BY`, `ORDER BY`, `SET` and similar: columns of the tables in `FROM` plus keywords; without such tables, tables plus keywords. Elsewhere: keywords. Unqualified table names mean the current schema only.
- **Identifiers:** unquoted text matches without regard to case (and folds to lower case when it names a table); names that need quotes (mixed case, spaces, reserved words) are inserted quoted. Inside a quoted name the whole quoted name is replaced. Keywords follow the typed case (`sel` gives `select`, `SEL` and an empty word give `SELECT`).
- **Extension:** `CandidateSource` returns candidates for a `CompletionContext`. `SlashCommandSource` reads names and argument candidates from the Session registry (issue 0012). The context reports `Position.SLASH` and the SQL sources stay silent. `/for` completes to `/format`; `/format ` offers all five Formats. Replacement preserves leading whitespace and text after the word.
## Integration with issues 0011 and 0012

- One `EditorKeys` map contains send, newline, both Orientation keys and both Completion keys. `/help` documents Completion and popup keys. Shift-Tab closes the popup and cycles Orientation; only plain Tab accepts. Enter accepts without sending.
- Connection replacement resets the shared schema provider for both schema commands and Completion. Reconnect preserves Read-only, Format and Orientation.
- `StatusBarText.lines` combines connection label, Format, Orientation, mode, Row limit, timer, last Result and Completion notes. Notes no longer hide mode or execution state. A one-line Status bar puts mode first.
- Full `./mvnw -q verify` passed after this merge: 217 Unit tests and 155 Integration tests, without failures, errors or skips.
- TDD: the cross-feature Slash Completion test first returned no candidates; the reconnect test first returned stale Completion data. Both pass after integration. The combined Status bar API had a compile-red check. The Shift-Tab popup test failed behaviorally before its fix. Additional Slash-source Unit tests passed immediately and are regression coverage.

## Known limits

- The popup does not filter while the user types (a key closes it; request again). Cursor positions count UTF-16 characters. Candidates for `INSERT INTO t (` column lists and for subquery aliases are not offered.
