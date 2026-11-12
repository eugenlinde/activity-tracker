# Architecture

The one living description of what runs where. Updated in the same change
as any infra or factory change. If this file and reality disagree, that is
a bug.

Last updated: 2026-11-12 (repo and CI — nothing is deployed yet).

## What exists today

The app itself still runs only on the developer's laptop. There is no
server. The code lives on GitHub and every pull request is checked there
automatically.

| Piece | What it is | Where it runs |
|---|---|---|
| Backend | Spring Boot app (Gradle), REST API under `/api`, JWT auth | laptop, `./gradlew bootRun`, port 8080 |
| Database | Postgres 17, schema managed by Liquibase | laptop, `docker-compose.yml` |
| Media files | Uploaded images on the local filesystem | laptop, `storage/` (git-ignored) |
| Frontend | React + Vite + TypeScript in `frontend/` | laptop, Vite dev server, proxies `/api` to the backend |
| LLM calls | OpenRouter API, used by the CSV import (client exists, import not finished) | called from the backend |
| Tests | JUnit suite (115 tests); database tests start their own Postgres via Testcontainers — image `postgres:latest`, not pinned to 17 like the dev database (to be pinned, see backlog) | laptop, `./gradlew test`, and CI |
| Code | Private GitHub repo `eugenlinde/activity-tracker`, default branch `main` | GitHub |
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

**Proven on 2026-11-12:** a PR with a deliberately failing test turned
`backend` red ("116 tests completed, 1 failed"), and a PR with a deliberate
type error turned `frontend` red. Both were closed without merging. Not
proven: a lint finding turning `frontend` red.

**What protects `main`:** only the rule that changes arrive by PR. GitHub
does not physically block a direct push or a merge with red checks,
because branch protection on a private repo needs a paid plan (checked
2026-11-12: the API answered "Upgrade to GitHub Pro or make this
repository public"). The planned safeguard is in F2: the deploy step will
run only after both checks pass, so a bad push cannot reach prod.

**What CI costs:** nothing in money. A free GitHub account gets a monthly
allowance of Actions minutes for private repos; each PR push uses roughly
three of them. A newer push to the same PR cancels the older run; runs
on `main` are never cancelled, so every merged commit gets verified. The
Gradle cache uses the plain GitHub cache (`cache-provider: basic`), not the
action's default commercial caching service.
Usage is visible under the account's billing settings. If the allowance
ever runs out, the fallback is running the checks on our own server.

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
