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

## Branches and Worktrees

- Work on one branch per issue, or per set of closely related issues. Never commit feature work directly to `main`.
- Branch name: `<type>/<NNNN>[-<NNNN>...]-<slug>`, where `<type>` is the conventional commit type and `<NNNN>` is the issue number. Examples: `feat/0001-jline-spike`, `feat/0003-0005-batch-input-and-orientation`.
- Group issues on one branch only when they touch the same code and cannot be demoed alone. Otherwise use one branch per issue.
- Start each branch from the committed `HEAD` of `main`, after the issue's blockers are merged.
- Use a git worktree per branch when work is delegated or runs in parallel. Create it through the `subagent` tool (`worktree: { branch }`). A single sequential branch may use a plain checkout.
- Put the issue number in each commit footer, for example `Refs: issue 0001`.
- Update the issue file in the same branch: tick finished acceptance criteria and set `Status:` to `done` when all are ticked.
- Merge to `main` only after review and with the user's approval. Do not push, merge or remove worktrees without that approval.

## Subagents

- Use the `subagent` tool whenever work is independent, parallel, long-running or noisy (spikes, research, reviews, one issue per worker). Load the `pi-herdr-agents` skill first.
- Read-only scouts and reviewers use a plain pane. Every writer that runs in parallel gets its own worktree and branch (see above).
- Give the worker the issue file path, the relevant ADRs, `GLOSSARY.md` and the rules in this file. Do not paste whole documents into the task.
- Tell the worker to test first, commit, report the commit SHA, and not to push, merge, switch branches or remove the worktree.
- Set an acceptance check (`accept`) for the worker when a command can prove the result, for example `./mvnw -q verify`.
- The parent agent reviews the result, integrates it and reports to the user. Do not poll a worker. Results arrive as messages.
- Throwaway spike code goes in `spikes/<NNNN>-<slug>/` and is never reused as production code. Spike findings go into the issue file under a `## Findings` heading.

## Documentation Structure

- `docs/prd/`: product requirement documents, one file per PRD (for example `mvp-1.0.md`).
- `docs/adr/`: architecture decision records, numbered `0001-slug.md`.
- `docs/issues/`: local issue files sliced from a PRD, named `NNNN-slug.md` (four digits, dependency order). They exist before or instead of publishing to GitHub.
- Put new requirements, decisions and issues in the matching directory. Do not add files directly under `docs/`.
