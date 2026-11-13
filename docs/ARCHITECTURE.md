# Architecture

The one living description of what runs where. Updated in the same change
as any infra or factory change. If this file and reality disagree, that is
a bug.

Last updated: 2026-11-12 (public repo, protected `main` — nothing is deployed yet).

## What exists today

The app itself still runs only on the developer's laptop. There is no
server. The code lives on GitHub and every pull request is checked there
automatically.

| Piece | What it is | Where it runs |
|---|---|---|
| Backend | Spring Boot app (Gradle), REST API under `/api`, JWT auth | laptop, `./gradlew bootRun`, port 8080 |
| Database | Postgres 17, schema managed by Liquibase | laptop, `docker-compose.yml` |
| Media files | Uploaded images on the local filesystem | laptop, `storage/` (git-ignored) |
| Frontend | React + Vite + TypeScript in `frontend/`, Ant Design components with the prototype's dark theme | laptop, Vite dev server, proxies `/api` to the backend |
| LLM calls | OpenRouter API, used by the CSV import (client exists, import not finished) | called from the backend |
| Tests | JUnit suite (115 tests); database tests start their own Postgres via Testcontainers — image `postgres:latest`, not pinned to 17 like the dev database (to be pinned, see backlog) | laptop, `./gradlew test`, and CI |
| Code | Public GitHub repo `eugenlinde/activity-tracker` (MIT licence), default branch `main`, protected | GitHub |
| CI | `.github/workflows/ci.yml` — jobs `backend` and `frontend`, on every pull request and every push to `main` | GitHub Actions runners (GitHub's machines) |

## Path of a change

1. A change is made on a branch, never on `main`.
2. The branch is pushed and a pull request (PR) is opened against `main`.
3. GitHub starts two checks on fresh machines of its own:
   - **`backend`** installs Java 26, runs `./gradlew build`: compiles the
     app and runs the whole test suite. Database tests start a real
     Postgres in Docker on that machine (Testcontainers), which is why the
     runner needs Docker. Takes about two minutes.
   - **`frontend`** installs Node 22, runs `yarn install`, `yarn lint`,
     `yarn build` (the build includes the TypeScript type check). Takes
     under a minute. The lint step is mostly advisory today: only one
     rule (`react/rules-of-hooks`) is an error; everything else is a
     warning and does not fail the check. There are 5 known warnings.
     Making warnings fail the check is a backlog item.
4. Each check ends green or red on the PR page.
5. The developer merges the PR. Nothing deploys yet — that is F2.

**A red check means** the change broke something the checks look at: a
failing test or a compile error (`backend`), a type error or a
hooks-rule violation (`frontend`). Open the check's log from the PR page.
When `backend` fails because of tests, the full test report is attached to
the run as the artifact `backend-test-report` (kept 7 days). When it fails
earlier, at compile time, there is no report — the log has the error.

**What protects `main`:** GitHub itself (branch protection, on since the
repo became public on 2026-11-12):

- Nobody can push to `main` directly — not even the owner. Changes arrive
  only through a pull request.
- A pull request can be merged only when both `backend` and `frontend`
  are green, and only if its branch is up to date with `main` (GitHub
  offers an "Update branch" button when it is not).
- No review approval is required (there is one developer), force pushes
  and deleting `main` are off.

To change these rules: repository Settings → Branches. Turning protection
off is the emergency exit if a rule ever blocks something urgent; it should
be turned back on right after.

**Proven on 2026-11-12:**

- A direct push to `main` was rejected by GitHub ("GH006: Protected branch
  update failed").
- A pull request with a deliberately failing test turned `backend` red
  ("116 tests completed, 1 failed"), and
  GitHub reported the pull request as blocked from merging. It was closed
  without merging.
- A deliberate type error turned `frontend` red (checked before the repo
  was re-created with clean history; the workflow has not changed since).
- Not proven: a lint finding turning `frontend` red.

**What is public:** everything in the repo and its history, issues, pull
requests, and CI logs. Secrets are never in any of those — they will live
in GitHub Secrets and on the server (F2). Session notes from before the
pivot are kept privately by the developer and are not in the repo.

**What CI costs:** nothing. GitHub Actions is free for public
repositories. A newer push to the same PR cancels the older run; runs on
`main` are never cancelled, so every merged commit gets verified. The
Gradle cache uses the plain GitHub cache (`cache-provider: basic`), not the
action's default commercial caching service.

## Where secrets live today

- Database credentials: hard-coded local-only values in
  `application.properties` and `docker-compose.yml`. Fine for a laptop,
  not for a server.
- JWT signing secret: environment variable `JWT_SECRET` on the laptop. The
  test suite uses its own fixed value from the `test` profile; that value
  is in git history and must never be used for a real server.
- OpenRouter key, base URL, model: environment variables
  (`OPENROUTER_API_KEY`, `OPENROUTER_BASE_URL`, `OPENROUTER_MODEL`). The app
  refuses to start if they are blank.

## Target (not built yet)

Filled in phase by phase as each piece becomes real — see
`.claude/ROADMAP.md`. Planned shape: GitHub repo with CI → images in GHCR →
one VPS running Docker Compose (Caddy for HTTPS, app, Postgres; prod and
one staging slot) → nightly off-box backups → error tracking and uptime
check that open GitHub issues → agents in GitHub Actions that turn issues
into pull requests.

Sections to be written when they become true:

- Path of a request (browser → Caddy → app → Postgres)
- The rest of the path of a change (issue → spec → … → staging → prod)
- Backups and how to restore
- How to stop the factory
- What it costs per month
