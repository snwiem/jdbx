# 0024: Stream Batch queries that start with a comment

**What to build:** `StreamingRead.isQuery` must skip leading SQL comments (`-- ...` and `/* ... */`) and whitespace before it checks the first keyword. Today `-- export\nSELECT ...` is not seen as a query, so autocommit stays on and the driver buffers the whole Result. With no default Batch row limit this can exhaust memory. Found by code review (copilot-review.md, finding 2). Fixes a gap in issue 0021.

**Blocked by:** none

**Status:** done

**Parent:** `docs/prd/mvp-1.0.md`

- [x] Unit test: `isQuery` is true for a Statement with leading line comments, block comments, and mixed whitespace before `select`, `with`, `values`, `table`, `show`
- [x] Unit test: a comment-only Statement, or a comment before `vacuum`, is not a query
- [x] Integration test: a multi-million-row `SELECT` with a leading comment runs under the low heap limit used for issue 0021
- [x] The Known limits note in issue 0021 stays true
