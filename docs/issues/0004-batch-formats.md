# 0004: Formats

**What to build:** `--format table|markdown|csv|tsv|json` selects the output Format in batch mode. Value rendering follows the PRD: NULL, dates in the JVM time zone, plain numbers, binary as hex with length, `json`/`jsonb` columns embedded as real JSON, multi-line text marker, long text cut with an ellipsis in `table` only.

**Blocked by:** 0002 Batch tracer

**Status:** done

**Parent:** `docs/prd/mvp-1.0.md`

- [x] Unit tests for each Format with a Result that has NULL, long text, multi-line text, binary, date and JSON values
- [x] `csv`, `tsv` and `json` never cut data
- [x] `json` writes one object per row
- [x] NULL marker, multi-line marker and time zone are configurable through settings (defaults as in the PRD)
  - Satisfied as `FormatOptions` values with PRD defaults. Reading them from settings is issue 0006.
- [x] `--format` appears in `--help`
