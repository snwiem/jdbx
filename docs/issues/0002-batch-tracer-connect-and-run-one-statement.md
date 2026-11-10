# 0002: Batch tracer: connect and run one statement

**What to build:** From the command line, `dbui --host H --port P --db D --user U --password X -c "select 1"` connects to PostgreSQL, runs the Statement and prints the Result as a horizontal `table` on stdout. The project is set up as in ADR 0003 (stripped-down Spring Boot, one plain JDBC connection, no pool, PostgreSQL driver) with picocli for arguments and Testcontainers for integration tests.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

**Parent:** `docs/prd/mvp-1.0.md`

- [ ] `*IT` test against a PostgreSQL Testcontainer runs the call above and checks stdout and exit code 0
- [ ] Missing connection values give a clear error on stderr and a non-zero exit code. Batch mode never prompts
- [ ] The connection sets the application name `dbui`
- [ ] The test seams of the PRD (Batch entry point) are in place and `AGENTS.md` rules are followed
- [ ] Unneeded Spring Boot auto-configuration, web server and connection pool are not on the classpath
