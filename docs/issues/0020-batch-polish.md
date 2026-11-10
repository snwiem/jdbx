# 0020: Batch polish

**What to build:** Small fixes found while merging issues 0003, 0004 and 0006, so that Batch mode behaves consistently with the settings file.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

**Parent:** `docs/prd/mvp-1.0.md`

- [x] Batch mode with no `-c`, no `-f` and an empty stdin fails with a clear error and a non-zero exit code. Today it does nothing and exits 0
- [ ] The Format default comes from `config.yaml` (`format`) when `--format` is not given. The command-line option wins. Same for the Orientation once issue 0005 exists
- [ ] The Format options (NULL marker, multi-line marker, maximum width, time zone) can be set in `config.yaml` and reach the formatters
- [ ] A plain `timestamp` (without time zone) is shown as stored and is not shifted when a non-default time zone is configured. Add unit and integration tests for `date`, `time`, `timestamp` and `timestamptz`
- [ ] The row limit from `config.yaml` stays unused in Batch mode (streaming), and `--limit N` is available as the PRD says
