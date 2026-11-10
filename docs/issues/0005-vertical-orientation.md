# 0005: Vertical Orientation

**What to build:** `--orientation horizontal|vertical` switches the layout of `table`, `markdown`, `csv` and `tsv`. Vertical puts the column names on the left and one column per result row. `json` ignores Orientation.

**Blocked by:** 0004 Formats

**Status:** ready-for-agent

**Parent:** `docs/prd/mvp-1.0.md`

- [ ] Unit tests for vertical layout in all four Formats, including empty Results and one-row Results
- [ ] `json` output is identical for both Orientations
- [ ] Default is horizontal, and the default can be set in the settings
- [ ] `--orientation` appears in `--help`
