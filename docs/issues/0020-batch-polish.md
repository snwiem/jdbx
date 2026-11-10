# 0020: Batch polish

**What to build:** Small fixes found while merging issues 0003, 0004 and 0006, so that Batch mode behaves consistently with the settings file.

**Blocked by:** None (can start immediately)

**Status:** done

**Parent:** `docs/prd/mvp-1.0.md`

- [x] Batch mode with no `-c`, no `-f` and an empty stdin fails with a clear error and a non-zero exit code. Today it does nothing and exits 0
- [x] The Format default comes from `config.yaml` (`format`) when `--format` is not given. The command-line option wins. (Orientation default: issue 0005.)
- [x] The Format options (NULL marker, multi-line marker, maximum width, time zone) can be set in `config.yaml` (`null-marker`, `multiline-marker`, `max-width`, `timezone`) and reach the formatters in Batch mode. Invalid values give clear errors
- [x] A plain `timestamp` (without time zone) is shown as stored and is not shifted when a non-default time zone is configured. Add unit and integration tests for `date`, `time`, `timestamp` and `timestamptz`
- [x] The row limit from `config.yaml` stays unused in Batch mode (streaming), and `--limit N` is available as the PRD says
- [x] `--limit N` writes at most N rows per Result and prints `truncated at N rows` on stderr when a Result was cut

## Notes

- Batch mode still reads all rows of one Result into memory before it writes them (`Result` holds a list). True streaming is a follow-up: it needs a lazy row source in `Result` and the formatters.
- Interactive mode (`Session`) still uses `getObject` and defaults for Format options.
