# 0032: `json-pretty` Format

**What to build:** A new Format `json-pretty` writes a Result as fully indented JSON (each key on its own line). The Format `json` keeps its compact layout, one object per row. Chosen over a settings option, so that the layout is visible in the Format name, in `/format`, in `--format` and in the Status bar. Decided with the user while discussing issue 0027.

**Blocked by:** 0027 (shares the multi-Result rules); relates to 0014 (`/save` and `/dump` accept every Format)

**Status:** open

**Parent:** `docs/prd/mvp-1.0.md`

## Decision

- Format id: `json-pretty`. `Format.id()` today uses the lower-cased enum name, so `JSON_PRETTY` would give `json_pretty`. `id()` must return `json-pretty`; `parse` accepts only the hyphen form.
- Like `json`, it ignores the Orientation and streams row by row. Indent: two spaces. Values are rendered as in `json` (same `ValueRenderer`); only the layout differs.
- An empty Result is `[]`.
- Several Results in Batch: same rule as `json` (documents follow each other, no blank line). `/dump` with `json-pretty` writes one outer array of the pretty arrays; nesting indentation must stay valid and consistent.
- `/format`, `--format`, the error text for an unknown Format, Slash Completion of Format names, `/help`, the Status bar and `README.md` list `json-pretty`.
- `GLOSSARY.md` Format entry lists it.

## Acceptance criteria

- [ ] Unit test: `json-pretty` output for a Result with several columns and rows equals a known literal, and parses as the same data as `json`
- [ ] Unit test: empty Result gives `[]`; `null`, strings with quotes and control characters, numbers and booleans are rendered like `json`
- [ ] Unit test: `Format.parse("json-pretty")` works and `json_pretty` is rejected with the list of Formats in the message
- [ ] Integration test: Batch `--format json-pretty` streams a large Result under the small-heap limit
- [ ] `/format json-pretty` works in Interactive mode, and Slash Completion offers it
- [ ] Docs, `GLOSSARY.md`, `--help` and `/help` list the new Format
