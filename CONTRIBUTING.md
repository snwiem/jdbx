# Contributing to jdbx

Thank you for your interest. Contributions are welcome: bug reports, ideas, documentation, code and **reviews**.

This project is mostly written by AI and only partly reviewed by a human (see the notice in the [README](README.md)). A careful review of existing code is as useful as new code.

## Before you start

- Search the [issues](https://github.com/snwiem/jdbx/issues) first. Your topic may exist already.
- For a large change, open an issue first and describe your idea. We can agree on the approach before you write code.
- A small fix (typo, small bug) can go straight to a pull request.

## Report a bug or an idea

Use the issue forms. A good bug report has:

- the steps to reproduce it
- what you expected and what happened
- the output of `jdbx --version`, your Java version, your operating system and your database

**Never paste passwords, tokens or customer data.** This repository is public. Remove secrets from logs and screenshots first.

To report a security problem, follow [SECURITY.md](SECURITY.md). Do not open a public issue.

## Set up

You need:

- Java 25
- Git
- a container runtime (Docker or Podman). The integration tests start PostgreSQL with Testcontainers.

```sh
git clone https://github.com/snwiem/jdbx.git
cd jdbx
./mvnw verify
```

A green `./mvnw verify` means your setup works. On Windows, use `mvnw.cmd`.

## Learn the project

- [README.md](README.md): how to install and use jdbx.
- [GLOSSARY.md](GLOSSARY.md): the words of the project. Use them in code, tests, commits and docs.
- [docs/adr/](docs/adr/): architecture decisions. Read the ones for the area you change.
- [docs/prd/](docs/prd/): product requirements.

## How we work

- **Test first.** Write a failing test, then the code that makes it pass.
- Unit tests are named `*Test`. They need no database.
- Integration tests are named `*IT`. They use Testcontainers with PostgreSQL.
- Keep the terminal (TamboUI) code thin. Put logic in plain classes and test those.
- A change of behaviour needs a test. A bug fix needs a test that fails without the fix.

## Branches and commits

- Make a branch for every change. Never work on `main`.
- Branch name: `<prefix>/<N>-<slug>`. The prefix is `feat` (feature), `fix` (bug fix) or `chore` (everything else). `<N>` is the issue number. Example: `fix/15-resume-profile`.
- Use [conventional commits](https://www.conventionalcommits.org), for example `feat: add /history command`.
- Put the issue number in the commit footer: `Refs: #15`.
- Do not commit secrets or personal data. Use a noreply email address if you do not want to show your own.

## Pull requests

1. Update your branch with the latest `main`: `git fetch origin && git merge origin/main`.
2. Run `./mvnw verify`. It must pass before every push.
3. Push your branch and open a pull request into `main`. Fill in the template. Write `Closes #N` in the description.
4. Wait for the check `verify`. It must be green before a merge.
5. Answer review comments with new commits. Do not force-push after a review has started.

The maintainer squash-merges the pull request, so the pull request title becomes the commit message. Write the title as a conventional commit.

This is a hobby project. A review may take some days. Please be patient.

## AI-assisted contributions

You may use AI tools. Please:

- say so in the pull request
- read and understand every line you submit. You are responsible for it
- run the tests yourself

The file [AGENTS.md](AGENTS.md) holds the rules for AI agents that work in this repository.

## Licence

jdbx uses the [Apache License 2.0](LICENSE). When you submit a contribution, you agree to license it under the same terms. There is no sign-off (DCO) or contributor agreement.

## Be respectful

Be kind and patient. Criticise code and ideas, never people. If someone treats you badly, mention the maintainer (@snwiem) in the thread, or use the GitHub function "Report content".
