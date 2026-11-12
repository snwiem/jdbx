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
- Keep terminal (TamboUI) glue thin. Put logic in plain classes and test those.
- Use the terms of `GLOSSARY.md` in code, tests, commits and docs.

## Branches and Worktrees

- Work on one branch per GitHub issue, or per set of closely related issues. Never commit feature work directly to `main`.
- Branch name: `<type>/<N>[-<N>...]-<slug>`, where `<type>` is the conventional commit type and `<N>` is the GitHub issue number. Examples: `feat/12-save-and-dump`, `fix/15-17-resume-profile`. Work without an issue uses `<type>/<slug>`.
- Group issues on one branch only when they touch the same code and cannot be demoed alone. Otherwise use one branch per issue.
- Start each branch from the committed `HEAD` of `main`, after the issue's blockers are merged.
- Use a git worktree per branch when work is delegated or runs in parallel. Create it through the `subagent` tool (`worktree: { branch }`). A single sequential branch may use a plain checkout.
- Put the issue number in each commit footer, for example `Refs: #12`. Use `Closes #12` in the commit or pull request that finishes the issue.
- Merge to `main` only after review and with the user's approval. Do not push, merge or remove worktrees without that approval.

## Subagents

- Use the `subagent` tool whenever work is independent, parallel, long-running or noisy (spikes, research, reviews, one issue per worker). Load the `pi-herdr-agents` skill first.
- Read-only scouts and reviewers use a plain pane. Every writer that runs in parallel gets its own worktree and branch (see above).
- Give the worker the GitHub issue number (it reads the issue with `gh issue view <N>`), the relevant ADRs, `GLOSSARY.md` and the rules in this file. Do not paste whole documents into the task.
- Tell the worker to test first, commit, report the commit SHA, and not to push, merge, switch branches or remove the worktree. Workers never create or edit GitHub issues, comments or pull requests (see Issues).
- Set an acceptance check (`accept`) for the worker when a command can prove the result, for example `./mvnw -q verify`.
- The parent agent reviews the result, integrates it and reports to the user. Do not poll a worker. Results arrive as messages.
- Throwaway spike code lives on a branch named `spike/<slug>` that is never merged, or outside the repository. It is never reused as production code. Record spike findings in the ADR of the decision, or in the GitHub issue when no decision follows.

## Documentation Structure

- `docs/prd/`: product requirement documents, one file per PRD (for example `mvp-1.0.md`).
- `docs/adr/`: architecture decision records, numbered `0001-slug.md`.
- Issues live on GitHub only. There are no local issue files and no `spikes/` directory. Local issues `0001` to `0040` and the spike code were deleted. They are in git at commit `e4ef411` (for example `git show e4ef411:docs/issues/0001-jline-spike.md`).
- Put new requirements and decisions in the matching directory. Do not add files directly under `docs/`.

## Naming

- The application is called `jdbx` (ADR 0008). It was called `dbui` before.
- ADRs 0001 to 0007 and the PRD keep the old name `dbui`. They record history. Do not rename it there.
- ADRs and the PRD may mention "issue 00NN". These are the deleted local issues (see Documentation Structure), not GitHub issues.

## Public Repository

- https://github.com/snwiem/jdbx is **public**. Everything in the repository, in its history, in issues, comments and pull requests is visible to everyone. Treat each text as published.
- Never write secrets or personal data into the repository, a commit message or GitHub: passwords, tokens, keys, real email addresses, internal host names, URLs or paths of other organisations, customer data, logs or terminal output that contain such data.
- Do not mention the user's employer, work equipment or work environment anywhere.
- Commits use the noreply author `5032664+snwiem@users.noreply.github.com`. Environment variables beat git config. A tool config may set another author email. Before the first commit in a session run `eval "$(mise env -s bash)"`. After every commit check `git log -1 --format='%ae|%ce'`. Before every push check `git log --all --format=%ae | sort -u`: only the noreply address may appear.
- Never push, force-push, rewrite published history, delete a published issue or change repository settings without the user's approval.

## Issues

- Track work as GitHub issues in `snwiem/jdbx` with `gh`. The issues are public.
- **Before** you create or edit an issue, a comment, a pull request or a release note, show the user the full title and text and wait for approval. Do not publish a draft to see how it looks. A deletion does not remove copies, caches or notifications.
- Write only what a stranger may read: problem, expected behaviour, steps to reproduce, acceptance criteria. Use the terms of `GLOSSARY.md`. Do not paste logs unless you checked every line.
- Anything confidential, environment-specific or personal does **not** go into a repository issue. Put it in the private project below, or tell the user.
- When in doubt, ask. Subagent workers must not create or edit issues. They report to the parent agent, who asks the user.

### Private project

- **Private Issues**: https://github.com/users/snwiem/projects/9 (project number 9, owner `snwiem`, visibility Private).
- Use it for confidential issues, to-do items about the user's environment, and drafts that must not be public.
- Add an item as a **draft issue**, without a repository: `gh project item-create 9 --owner snwiem --title "<title>" --body "<text>"`. A draft belongs only to the project.
- Never convert a draft to a repository issue, never add a public issue's confidential details to it, and never change the project visibility to public.
- `gh` needs the `project` scope for the `snwiem` account. In this directory mise sets `GH_TOKEN` for that account (`mise.local.toml`, not committed). If a command fails with a missing scope, tell the user. Do not work around it.
