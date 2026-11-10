# AGENTS.md

## Version Control

- use git
- use gh for accessing github.com
- use conventional commits
- commit earily and often
- combine commits to logical units. don't just commit every change

## Development Process

- Work test-first (TDD). Load the `do-tdd` skill before writing production code.
- Agree the test seams with the user before writing a test.
- Unit tests are named `*Test`. They need no database.
- Integration tests are named `*IT`. They use Testcontainers with PostgreSQL. A container runtime is always available.
- Keep terminal (JLine) glue thin. Put logic in plain classes and test those.
- Use the terms of `GLOSSARY.md` in code, tests, commits and docs.
- Requirements live in `docs/`.
