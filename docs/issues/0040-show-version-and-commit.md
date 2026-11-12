# 0040: Show the version and the git commit

**What to build:** dbui shows its full version: the project version from `pom.xml` and the short git commit hash, for example `dbui 0.0.1-SNAPSHOT (f9a81f5)`. It appears in the first line of `/help` and in `dbui --version`. A commit does not always change the project version, so the version alone does not identify a build. Change request from the user.

**Blocked by:** none

**Status:** done

**Parent:** `docs/prd/mvp-1.0.md` (`/help`, issue 0039; the Batch command line)

## Check result (feasibility)

- The project version is `0.0.1-SNAPSHOT` in `pom.xml`. dbui reads it nowhere today.
- `BatchCommand` has `mixinStandardHelpOptions = true`, so picocli already offers `--version`. No version text is set.
- There are no git tags and no resources for build information.
- The jar timestamps are fixed (reproducible build). A build time would break this, so it is not shown.
- `git-commit-id-maven-plugin` is in the local Maven cache (9.0.1, 9.2.0, 10.0.0). The DATEV Artifactory mirrors Maven Central. Check that the build resolves it.
- `/help` is built by `HelpCatalog` (issue 0039). Its first line is `dbui help. Use /help commands, /help keys, or /help <word>.`

## Decision (agreed with the user)

- Use `git-commit-id-maven-plugin` (goal `revision`, in an early phase so that tests and the IDE find the file). It writes `git.properties` into `target/classes`. Keep only: project version (`git.build.version`), short commit id (`git.commit.id.abbrev`) and the dirty flag (`git.dirty`). No build time, no branch, no user name or e-mail.
- Do not fail the build when there is no git directory or no repository information.
- A plain class `BuildInfo` reads `git.properties` from the classpath. It has no Spring, no terminal.
- Text formats:
  - `0.0.1-SNAPSHOT (f9a81f5)`
  - uncommitted changes: `0.0.1-SNAPSHOT (f9a81f5-dirty)`
  - no commit information: `0.0.1-SNAPSHOT (unknown)`
  - no file at all: `unknown version`
- `/help` starts with `dbui <version text>. Use /help commands, /help keys, or /help <word>.` The other help topics stay unchanged.
- `dbui --version` prints `dbui <version text>` and exits with 0, before it connects to a database.
- The Session log and `session.yaml` do not get the version in this issue.
- Use the terms of `GLOSSARY.md`.

## Decision (implementation)

- A missing or blank project version gives `unknown version`, even if a commit is present. A missing commit never gets a dirty suffix.
- Use the Spring Boot-managed plugin version 9.2.0. In this worktree the default JGit provider read the common repository and reported `git.dirty=false` while `git status --porcelain` showed changes. Native git alone also resolved the common repository (verbose output named the main checkout). Use native git with `dotGitDirectory=${project.basedir}/.`: the native provider runs in its parent, which keeps it in the current worktree. A build check then matched the commit and dirty flag against native git in this worktree.
- Activate that native-git setting only when `.git` exists. With the forced directory in a Git-free copy, the plugin failed on `git rev-parse HEAD` (exit 128), despite the two failure flags being false. Without `.git`, use the default locator instead: it generates only `git.build.version`, so source archives still build and show an unknown commit.

## Proposed test seams (agreed before work)

1. Plain Unit tests on `BuildInfo`: properties to text (clean, dirty, no commit, no file, blank values). It misses the real build.
2. `HelpCatalogTest`: the first line of the overview contains the version text. Other topics do not change. It misses the Session wiring.
3. The CLI: `--version` through picocli prints the text and exits with 0 without a connection. It misses the real jar.
4. A check in the real build: in a test run, `BuildInfo.current()` finds the generated `git.properties`, and its version equals the Maven project version (passed as a system property from `pom.xml`). It catches a plugin that does not run or does not write the file.

## Acceptance criteria

- [x] Unit test: clean, dirty, no-commit and no-file texts
- [x] Unit test: `/help` overview starts with `dbui <version text>.`
- [x] Test: `dbui --version` prints `dbui <version text>` and needs no connection
- [x] Test: the generated `git.properties` is found and has the project version
- [x] `./mvnw -q verify` passes with the plugin; a build without git information still works
- [x] `README.md` documents the version output
- [x] Manual check (done by the user): `dbui --version` and `/help` from the built jar, also with an uncommitted change

## Findings

- `BuildInfoTest`: observed red before green for all seven tests (clean, dirty, no commit, no file, blank commit, blank project version, generated project version). The real-build test also failed with `unknown version` before adding the plugin, not only with missing methods.
- `HelpCatalogTest`: observed red for the adapted overview expectation before adding the injected version text. Only its first line changes; the remaining overview and other topic expectations stay intact.
- `BatchCommandVersionTest`: observed red (empty stdout) before adding the picocli version provider. The test passes a JDBC URL with no suitable driver, so it cannot need a successful connection.
- The existing Session overview assertion now expects the current build text instead of `dbui help`. Its updated assertion passed at once: it is a wiring regression guard, not a new red-before-green test.
- `./mvnw -q verify` passed: 527 unit tests and 256 integration tests, no failures, errors or skips. The first run exceeded the command's 120-second limit; the rerun with a longer limit passed.
- Packaged a fresh copy containing `pom.xml`, the Maven wrapper and `src`, but neither `.git` nor `target`. The seven `BuildInfoTest` cases and the help/CLI test suites passed in it. The generated file contained only `git.build.version=0.0.1-SNAPSHOT`.

## Manual check

1. Build from the clean branch with `./mvnw -DskipTests package`, then run `bin/dbui --version`. Expect the project version and the short commit of this branch, without `-dirty`, and exit code 0 without connection options.
2. Start Interactive mode from the same jar and run `/help`. Its first line must show the same version, followed by the help-topic hint. Check `/help commands` and `/help keys` for unchanged content.
3. Make a harmless uncommitted change to a tracked file, rebuild, and repeat both checks. Expect `-dirty` inside the parentheses. Restore the change afterward.
