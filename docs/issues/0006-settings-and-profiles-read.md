# 0006: Settings and Profiles (read)

**What to build:** dbui reads `config.yaml` (settings) and `profiles.yaml` (Profiles) from the config directory. `-p <profile>` selects a Profile. `--url` accepts a JDBC URL. Command-line options override Profile fields. The password comes from `--password` or the URL, then the Profile (with `${ENV}` references), and never from `.pgpass`.

**Blocked by:** 0002 Batch tracer

**Status:** ready-for-agent

**Parent:** `docs/prd/mvp-1.0.md`

- [ ] Unit tests with temporary directories for loading settings and Profiles, including `${ENV}` resolution
- [ ] Profile fields: url or host/port/database, user, password, read-only, schema (initial search path), driver properties
- [ ] Override order is tested (options over Profile over defaults)
- [ ] Settings keys: Row limit default 1000, timeout default none, Format default `table`, Orientation default horizontal, read-only default off
- [ ] An integration test connects through a Profile
- [ ] A missing password in batch mode fails with a clear error and never prompts
