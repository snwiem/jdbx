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

## Branches and Pull Requests

- **Every change needs a branch.** Never commit to `main`, not even a documentation or build change.
- Branch name: `<prefix>/<N>[-<N>...]-<slug>`. The prefix is `feat` for a feature, `fix` for a bug fix and `chore` for everything else (docs, ci, build, refactor, tests). `<N>` is the GitHub issue number. Examples: `feat/12-save-and-dump`, `fix/15-17-resume-profile`, `chore/build-workflow`. Work without an issue uses `<prefix>/<slug>`. The commit type stays the conventional commit type (`docs`, `ci`, `refactor`, ...); only the branch prefix is limited to these three.
- Work on one branch per GitHub issue, or per set of closely related issues. Group issues on one branch only when they touch the same code and cannot be demoed alone.
- Put the issue number in each commit footer, for example `Refs: #12`. Use `Closes #12` in the commit or pull request that finishes the issue.

### Keep the branch in sync

Others may work on branches in parallel. Never assume that your local copy is current.

1. At the start of work, and again before every push, run `git fetch origin --prune`.
2. Start a new branch from an up-to-date `origin/main`: `git switch -c <branch> origin/main`.
3. If the branch already exists on `origin`, do not create it again. Check it out from `origin` and run `git pull --rebase`. Read what is new (`git log HEAD..origin/<branch>`) before you continue.
4. If `origin/<branch>` has new commits before you push, integrate them first: `git pull --rebase` rebases only your unpushed commits. Run the verification again afterwards.
5. To pick up `main`, merge `origin/main` into the branch. Do not rebase commits that are already pushed.
6. Never force-push. `--force-with-lease` only with the user's explicit approval for that push.

### Verify before every push

- `./mvnw verify` must succeed before **every** push, to a branch as well as to `main`. Run it on the exact commit that you push, and after any rebase or merge. Do not push on a red or an unknown result.
- The workflow `.github/workflows/build.yml` runs `./mvnw verify` on pull requests into `main` (on the merge result with `main`, also from forks) and on every push to `main`. It can also start by hand (`workflow_dispatch`). A branch without a pull request is **not** built. The workflow does not replace the local run. A red check blocks the merge.
- Before a merge, check that `origin/main` is an ancestor of the branch: `git fetch origin --prune && git merge-base --is-ancestor origin/main HEAD`. If not, merge `origin/main` into the branch, run `./mvnw verify` and push again. Without a branch protection rule, GitHub does not block the merge of an out-of-date branch.

### Merge through pull requests

- **Merges to `main` always happen through a GitHub pull request.** Never run `git merge` into `main` locally, and never push to `main`.
- **Every push of a branch has a pull request.** Before you ask the user to approve a push, check: `gh pr list --head <branch> --state open`.
  - **No pull request yet:** prepare the full title and text (skill `to-pr`) and show them in the same message as the push request. One approval covers the push **and** the creation of the pull request. After the approval, push the branch and run `gh pr create`. Do not ask a second time. If the user approves only the push, ask about the text again.
  - **A pull request exists:** the push updates it. Say so in the push request. Show a new text only if the summary changes. Apply it with `gh pr edit` after the approval.
  - A push of a release tag (see Releases) has no pull request.
- Merge with `gh pr merge` only when the check `verify` is green and the user approves. Always **squash** (`--squash`): `main` gets one commit per pull request. The pull request title becomes the commit subject, so write it as a conventional commit (for example `feat: add /history command`). Put `Closes #N` in the pull request body. Delete the branch after the merge, then `git switch main && git pull --ff-only`.
- Do not push, merge or remove worktrees without the user's approval.

### Releases

- A release is a tag `vMAJOR.MINOR.PATCH`, or `vMAJOR.MINOR.PATCH-suffix` for a pre-release (for example `v0.1.0-rc.1`). The tag is the source of truth for the version. The pom on `main` stays at `-SNAPSHOT`.
- Cut a release from an up-to-date `main` with a green build: `git tag -a v0.1.0 -m "jdbx 0.1.0" && git push origin v0.1.0`. Pushing a tag needs the approval of the user.
- The workflow `.github/workflows/release.yml` checks the tag, sets the version in the pom at build time, runs the build workflow, and creates the GitHub release with both archives, `SHA256SUMS` and generated notes. It publishes only when all jobs are green.
- Do not move or reuse a published tag. Fix a bad release with a new version.

### Worktrees

- Use a git worktree per branch when work is delegated or runs in parallel. Create it through the `subagent` tool (`worktree: { branch }`). A single sequential branch may use a plain checkout.

## Subagents

- Use the `subagent` tool whenever work is independent, parallel, long-running or noisy (spikes, research, reviews, one issue per worker). Load the `pi-herdr-agents` skill first.
- Read-only scouts and reviewers use a plain pane. Every writer that runs in parallel gets its own worktree and branch (see above).
- Give the worker the GitHub issue number (it reads the issue with `gh issue view <N>`), the relevant ADRs, `GLOSSARY.md` and the rules in this file. Do not paste whole documents into the task.
- Tell the worker to test first, commit, report the commit SHA, and not to push, merge, switch branches or remove the worktree. Workers never create or edit GitHub issues, comments or pull requests (see Issues).
- Set an acceptance check (`accept`) for the worker when a command can prove the result, for example `./mvnw -q verify`.
- The parent agent reviews the result, integrates it and reports to the user. Do not poll a worker. Results arrive as messages.
- Throwaway spike code lives on a branch named `chore/spike-<slug>` that is never merged, or outside the repository. It is never reused as production code. Record spike findings in the ADR of the decision, or in the GitHub issue when no decision follows.

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
