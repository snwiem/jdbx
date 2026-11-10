# 0003: Statement splitter and multiple inputs

**What to build:** Batch mode accepts Statements from `-c` (repeatable), `-f <file>` and stdin. One input with several Statements runs them in order. The first error stops the run.

**Blocked by:** 0002 Batch tracer

**Status:** done

**Parent:** `docs/prd/mvp-1.0.md`

- [x] Unit tests for the splitter: `;` inside strings, quoted identifiers, line and block comments, and `$$` blocks does not split
- [x] Results of several Statements are written one after another on stdout
- [x] An error is written to stderr, later Statements do not run, and the exit code is non-zero
- [x] Stdin is read as input when it is not a terminal and no other input is given
- [x] `-f -` reads stdin
