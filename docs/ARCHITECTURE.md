# Architecture

The one living description of what runs where. Updated in the same change
as any infra or factory change. If this file and reality disagree, that is
a bug.

Last updated: 2026-11-13 (deploy and backup scripts written and tested on the laptop — nothing is deployed yet, no server exists).

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
   - **`infra`** runs `shellcheck` (a linter for shell scripts) over the
     server scripts and then their tests (`infra/test/run.sh`). Takes a
     few seconds. It is not yet one of the checks GitHub requires before a
     merge — adding it to the branch protection rule is the developer's
     setting to change.
4. Each check ends green or red on the PR page.
5. The developer merges the PR. Nothing deploys yet: the `images` and
   `deploy` jobs exist in the workflow but are switched off until the
   repository variable `DEPLOY_ENABLED` is set to `true` (see "The deploy
   switch" below).

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

## Built, but not running anywhere yet

Everything needed to run the app on a server exists in `infra/` and was
rehearsed on the laptop on 2026-11-13. No server exists yet.

| Piece | What it is |
|---|---|
| `infra/api.Dockerfile` | The backend as a container image: Java 26 runtime plus the app's jar, running as a non-root user |
| `infra/web.Dockerfile` + `infra/Caddyfile` | Caddy with the built frontend inside. It serves the pages, forwards `/api/*` and `/actuator/health` to the backend, and nothing else |
| `infra/compose.yml` | Starts three containers together — `web`, `api`, `postgres` — and keeps the database, uploaded media and certificates in volumes so they survive restarts and redeploys |
| `infra/env.example` | The names of the settings a server needs. Real values will exist only on the server |

Two changes in the app itself for running in public:

- **Sign-up is by invitation.** `SIGNUP_ALLOWED_EMAILS` lists the addresses
  that may create an account. `*` means anyone (used for local
  development); if the variable is missing, nobody can sign up.
- **Health check.** `GET /actuator/health` answers `{"status":"UP"}`
  without a login. The deploy step will use it to decide whether a new
  version started. No other actuator endpoint is reachable.

**Rehearsal result (2026-11-13):** the three containers started from the
compose file; the health check answered `UP` through Caddy; an uninvited
address was refused and the invited one could sign up; a workout, an
image, a weight and a step count were saved; the image was still there
after the backend container was thrown away and recreated; the backend
ran as a non-root user and logged no SQL. The stack was then removed.

### Server scripts (written 2026-11-13, never run on a real server)

Four scripts and two workflow pieces that turn an empty server into the
running app and keep it backed up. All of them wait for a server.

| Piece | What it does | Why it is needed | When it fails |
|---|---|---|---|
| `infra/provision.sh` | Run once as root on a fresh Ubuntu 24.04. Installs Docker, the firewall and restic; creates the user `deploy`; turns off password logins; opens only ports 22, 80, 443; writes `/opt/tracker/.env` with a freshly generated database password and JWT secret; installs the nightly backup timer | A new server is an open, empty machine. This makes it safe and ready in one repeatable step instead of a list of hand-typed commands | It stops at the first error and can simply be run again — it never overwrites an existing `.env` |
| `infra/deploy.sh <tag>` | Runs on the server. Dumps the database, swaps in the new `compose.yml` if one arrived (keeping the old one aside), pulls the images for `<tag>`, restarts the containers, then asks `https://<site>/actuator/health` every 5 s for up to 2 minutes | One command that either ends with the new version healthy or puts the old one back | Pull fails → the old `compose.yml` is put back, nothing else was changed. Containers do not start, or the new version is not healthy → saves the last 50 lines of the backend log to `/opt/tracker/last-failed-deploy.log` (on the server only — the log may contain users' data and the workflow log is public), switches back to the previous tag and the previous `compose.yml`, restarts, exits with an error so the workflow goes red |
| `images` job in `ci.yml` | After green checks on `main`: builds the jar and the frontend, packs them into the two images, pushes them to GHCR tagged with the commit SHA | The server needs something to pull; the SHA tag means every version can be named and redeployed | Job goes red, `deploy` does not run, the server is untouched |
| `deploy` job in `ci.yml` | Copies the scripts to `/opt/tracker/` and the new `compose.yml` to `/opt/tracker/incoming/` over SSH, then runs `deploy.sh <sha>`. The script's output goes to `/opt/tracker/last-deploy.log` on the server and is shown in the workflow when it finishes, so a dropped connection cannot kill a deploy half-way | The only automated path to prod. It runs only on `main`, only after `images`, only with the secrets of the GitHub environment `production` | Job goes red; GitHub emails the owner. The site keeps running the previous version |
| `infra/backup.sh` | Started by a timer on the server at 03:30 every night. Dumps the database, sends the dump and the media files to off-server storage with restic, thins out old snapshots (7 daily, 4 weekly, 6 monthly), checks the repository on Sundays, then touches `/opt/tracker/last-backup-ok` | The server's disk is the only copy of the data otherwise | Any failing step stops the script before the marker file is touched, so the freshness check below notices |
| `backup-check.yml` | Every day at 06:00 UTC: logs in to the server and fails unless `last-backup-ok` is younger than 26 hours | A backup that silently stopped is worse than none, because everyone believes it exists | The workflow goes red and GitHub emails the owner |
| `infra/restore.sh <snapshot> <dir>` | Fetches a snapshot into an empty directory, loads the dump into a throwaway Postgres container with no network access to anything, prints the row count of every table and the number of media files | A backup is only proven by restoring it. This does so without going near the live database | Prints the error and stops; the live system is never touched either way |

**The deploy switch.** `images`, `deploy` and the backup check all look at
one repository variable, `DEPLOY_ENABLED`. Unless it is exactly `true`,
they are skipped. It is unset today, so merging to `main` builds nothing
and contacts no server. Setting it (Settings → Secrets and variables →
Actions → Variables) is the developer's go-live decision; deleting it
later is the way to stop all deploys at once.

**Redeploying an older version by hand** (manual rollback): Actions → CI →
Run workflow → enter the commit SHA of the version wanted. No image is
rebuilt and the three checks are skipped, so only the `deploy` job runs;
`deploy.sh` runs with that tag. It uses today's `compose.yml` with the
older images.

**What was checked, and what was not:**

- Checked on the laptop: `shellcheck` is clean; 31 script tests pass.
  The tests run the real scripts against stand-ins for `docker`, `curl`
  and `restic`, and cover: healthy deploy, unhealthy deploy rolled back,
  containers that fail to start rolled back, failed first deploy, failed
  pull, failed dump, dump rotation, the previous `compose.yml` coming back
  on failure, the backend log staying on the server, a tag with shell
  characters, backup marker only after every step succeeded, and restore
  refusing a non-empty directory. Fifteen deliberate breakages of the
  scripts were each caught by a test.
- An independent review of the scripts and workflows found eight defects
  (three in the rollback path); all eight were fixed, the testable ones
  with a test that failed first.
- Checked on the laptop with real Postgres: `restore.sh` loaded a real
  `pg_dump` into the throwaway container and printed correct row counts
  (run twice; the container had no published port and sat on an internal
  network).
- Checked in an Ubuntu 24.04 container: every package `provision.sh`
  installs exists under that name, including from Docker's repository.
- **Not checked, because there is no server:** `provision.sh` end to end
  (firewall, SSH settings, timer), a real deploy and a real rollback, a
  real backup to off-server storage, the two workflows' SSH steps, and
  how the workflow's job conditions behave on GitHub (read and reasoned
  about, never run). These are the drills in tasks 4–7 of the deploy
  plan.

**Known gaps in these scripts:**

- A rollback switches the code back, not the database. If a new version
  changed the schema before failing, the old code runs against the new
  schema. The pre-deploy dump in `/opt/tracker/predeploy/` (last 5 kept)
  is the way back, by hand.
- The `deploy` job trusts the server's SSH host key the first time it
  sees it on each run (`ssh-keyscan`) instead of comparing it with a
  stored one.
- GitHub pauses scheduled workflows in a repository with no activity for
  60 days. If the repo goes quiet that long, the backup check stops
  until it is re-enabled.
- Docker publishes ports 80 and 443 itself, past the `ufw` firewall. That
  is intended here (they are the two public ports), but it means `ufw`
  alone does not decide what is reachable if a compose file ever
  publishes another port.

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
