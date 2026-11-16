# Architecture

The one living description of what runs where. Updated in the same change
as any infra or factory change. If this file and reality disagree, that is
a bug.

Last updated: 2026-11-16 (first deploy: the app is live on one server over HTTPS; nightly off-server backups run and a restore has been rehearsed).

## What exists today

Since 2026-11-16 the app runs on one server on the public internet, and
still on the developer's laptop for development. The code lives on GitHub;
every pull request is checked there automatically and every merge to
`main` deploys itself.

**Production**

| Piece | What it is | Where it runs |
|---|---|---|
| Server | One Hetzner cloud server (CPX22: 2 vCPU, 4 GB, 80 GB), Ubuntu 26.04, Helsinki | Hetzner |
| `web` | Caddy: gets and renews the HTTPS certificate by itself (Let's Encrypt), serves the built frontend, forwards `/api/*` and `/actuator/health` to the backend. The only container with public ports (80, 443) | container on the server |
| `api` | The Spring Boot backend. Reachable only from `web`, inside Docker's network | container on the server |
| `postgres` | Postgres 17. Reachable only from `api` | container on the server |
| Data | Database, uploaded media and certificates live in Docker volumes on the server's disk (`tracker_pgdata`, `tracker_media`, `tracker_caddy_data`) — they survive restarts and redeploys, **not** the loss of the server. For that there is the nightly backup — see "Backups and how to restore" | server disk |
| Images | `ghcr.io/eugenlinde/activity-tracker-api` and `-web`, one tag per commit on `main`, public like the repo | GitHub's registry (GHCR) |
| Settings and secrets | `/opt/tracker/.env` | server |

**Path of a request:** browser → DNS gives the server's address → Caddy
on port 443 (HTTP on port 80 is redirected to HTTPS) → a page or script
is answered by Caddy itself from the built frontend; a call under `/api`
is passed to the backend → the backend checks the login token and talks
to Postgres → the answer goes back the same way. Anything else under
`/actuator` is not forwarded; the backend's management endpoints are not
reachable from outside.

**If the server restarts:** Docker starts by itself and brings the three
containers back (`restart: unless-stopped`). Not yet proven with a real
reboot.

**Memory in use right after the first deploy:** backend about 960 MB,
Postgres 45 MB, Caddy 17 MB, of 3.7 GB. (On the laptop, held to a 1.9 GB
limit, the backend settled at 500 MB — the Java runtime takes more when
more is there.)

**Development (laptop) and tooling**

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

On the server and on GitHub (set up 2026-11-16, no deploy has used them
yet):

- `/opt/tracker/.env` on the server, mode 600, owned by `deploy`: the
  production database password and JWT secret, generated on the box by
  `provision.sh` and never shown; the site address; the invited e-mail
  list; placeholder OpenRouter values. Readable by `root` and `deploy`
  only. To invite someone: add the address to `SIGNUP_ALLOWED_EMAILS`
  there and restart the backend.
- GitHub environment `production`, usable only by runs on `main`:
  `DEPLOY_HOST` (the server's address) and `DEPLOY_SSH_KEY` (the private
  half of a key made only for deploys). Pull requests and forks cannot
  read them. Once stored, GitHub shows a secret to nobody; it can only be
  replaced.
- The deploy key's public half is in `deploy`'s `authorized_keys` on the
  server. That key logs in as `deploy` only: it can run Docker and write
  in `/opt/tracker`, and it has no sudo. No copy of the private half
  exists outside GitHub. To cut GitHub off from the server: delete the
  line ending `github-actions-deploy` from
  `/home/deploy/.ssh/authorized_keys`.
- The developer's own SSH key logs in as `root` and as `deploy`.
- `/opt/tracker/backup.env` on the server, mode 600, owned by `root`:
  the address of the backup storage, its access key, and the restic
  password that encrypts the backups. Typed in by the developer; the
  builder has not seen the values. **The restic password also lives in
  the developer's password manager — if both copies are lost, the
  backups cannot be read by anyone.**

## The server pieces, one by one

Everything the server runs comes from `infra/`. Rehearsed on the laptop on
2026-11-13, live since 2026-11-16.

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

### Server scripts (written 2026-11-13; all four have run on the real server)

Four scripts and two workflow pieces that turn an empty server into the
running app and keep it backed up. All of them wait for a server.

| Piece | What it does | Why it is needed | When it fails |
|---|---|---|---|
| `infra/provision.sh` | Run once as root on a fresh Ubuntu 24.04 or 26.04. Installs Docker, the firewall and restic; creates the user `deploy`; turns off password logins; opens only ports 22, 80, 443; writes `/opt/tracker/.env` with a freshly generated database password and JWT secret; installs the nightly backup timer | A new server is an open, empty machine. This makes it safe and ready in one repeatable step instead of a list of hand-typed commands | It stops at the first error and can simply be run again — it never overwrites an existing `.env` |
| `infra/deploy.sh <tag>` | Runs on the server. Dumps the database, swaps in the new `compose.yml` if one arrived (keeping the old one aside), pulls the images for `<tag>`, restarts the containers, then asks `https://<site>/actuator/health` every 5 s for up to 2 minutes | One command that either ends with the new version healthy or puts the old one back | Pull fails → the old `compose.yml` is put back, nothing else was changed. Containers do not start, or the new version is not healthy → saves the last 50 lines of the backend log to `/opt/tracker/last-failed-deploy.log` (on the server only — the log may contain users' data and the workflow log is public), switches back to the previous tag and the previous `compose.yml`, restarts, exits with an error so the workflow goes red |
| `images` job in `ci.yml` | After green checks on `main`: builds the jar and the frontend, packs them into the two images, pushes them to GHCR tagged with the commit SHA | The server needs something to pull; the SHA tag means every version can be named and redeployed | Job goes red, `deploy` does not run, the server is untouched |
| `deploy` job in `ci.yml` | Copies the scripts to `/opt/tracker/` and the new `compose.yml` to `/opt/tracker/incoming/` over SSH, then runs `deploy.sh <sha>`. The script's output goes to `/opt/tracker/last-deploy.log` on the server and is shown in the workflow when it finishes, so a dropped connection cannot kill a deploy half-way | The only automated path to prod. It runs only on `main`, only after `images`, only with the secrets of the GitHub environment `production` | Job goes red; GitHub emails the owner. The site keeps running the previous version |
| `infra/backup.sh` | Started by a timer on the server at 03:30 every night. Dumps the database, sends the dump and the media files to off-server storage with restic, thins out old snapshots (7 daily, 4 weekly, 6 monthly), checks the repository on Sundays, then touches `/opt/tracker/last-backup-ok` | The server's disk is the only copy of the data otherwise | Any failing step stops the script before the marker file is touched, so the freshness check below notices |
| `backup-check.yml` | Every day at 06:00 UTC: logs in to the server and fails unless `last-backup-ok` is younger than 26 hours | A backup that silently stopped is worse than none, because everyone believes it exists | The workflow goes red and GitHub emails the owner |
| `infra/restore.sh <snapshot> <dir>` | Fetches a snapshot into an empty directory, loads the dump into a throwaway Postgres container with no network access to anything, prints the row count of every table and the number of media files | A backup is only proven by restoring it. This does so without going near the live database | Prints the error and stops; the live system is never touched either way |

**The deploy switch.** `images`, `deploy` and the backup check all look at
one repository variable, `DEPLOY_ENABLED`. Unless it is exactly `true`,
they are skipped. The developer set it on 2026-11-16 (Settings → Secrets
and variables → Actions → Variables), so every merge to `main` now
deploys. Deleting it, or setting it to anything else, stops all deploys
and the backup check at once; the running site is not affected.

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
- Checked in Ubuntu 24.04 and 26.04 containers: every package
  `provision.sh` installs exists under that name, including from Docker's
  repository. The server bought on 2026-11-16 runs 26.04.
- `provision.sh` ran end to end on the real server on 2026-11-16 (Hetzner
  CPX22, Ubuntu 26.04) and was checked afterwards on the box: Docker and
  the compose plugin installed and running; firewall active with only
  22, 80, 443 open; password logins refused, key logins work for `root`
  and `deploy`; `deploy` is in the docker group and has no sudo;
  `/opt/tracker/.env` exists, mode 600, owned by `deploy`, with all eight
  variables; the backup timer is enabled for 03:30 UTC; automatic
  security updates are on. The tracker subdomain resolves to the server
  from public resolvers.
- First real deploy, 2026-11-16: merging to `main` with the switch on ran
  `backend`, `frontend`, `infra`, `images`, `deploy`, all green, about
  three minutes from merge to live. Checked from outside afterwards: the
  health endpoint answers `UP` over HTTPS with a valid Let's Encrypt
  certificate; HTTP redirects to HTTPS; the front page loads; `/api`
  without a login answers 401; other `/actuator` paths return the
  frontend's page, not backend internals; sign-up with a non-invited
  address is refused with 403. The server pulled the images without
  logging in, so the packages are public.
- First real backup and restore, 2026-11-16: the backup unit ran green
  and stored one snapshot off the server; `restore.sh latest` fetched it
  back, loaded it into the throwaway database, and its row counts
  matched the live database table for table (the database was still
  empty of user data: 17 schema-history rows, nothing else). The
  backup-check workflow was run by hand twice: green with a fresh
  marker, red after the marker was made to look two days old.
- Second deploy, 2026-11-16, over the running version: green; the
  database kept running, `api` and `web` were replaced, and
  `/opt/tracker/predeploy/` holds the dump taken just before (it
  contains the developer's account, created minutes earlier). A backup
  and restore after that again matched the live database table for
  table, this time with one user row in it.
- Rollback drill, 2026-11-16: a backend image that cannot start (a Java
  option that does not exist) was merged on purpose. CI was green — the
  checks never start the image. `deploy` took a dump, started the new
  version, waited two minutes for a health check that never came, saved
  the backend's log to `/opt/tracker/last-failed-deploy.log`, put the
  previous version back and saw it healthy; the job ended red with
  "Rolled back to …, health check UP" after 3 min 44 s. Checked on the
  server afterwards: previous image tag in `.env`, all three containers
  running, the account still in the database. The broken line was
  removed again in the next change.
- **Not checked yet:** a restore with workouts and media in it (only an
  account existed), a rollback where the new version had changed the
  database schema, a reboot, and the timer firing by itself at night.

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

## Backups and how to restore

**What is backed up:** a full dump of the database and the uploaded media
files. **Where to:** one private Backblaze B2 bucket in the EU, written
with restic, which encrypts everything before it leaves the server — the
storage provider cannot read it. **When:** every night at 03:30 UTC
(a systemd timer on the server, `tracker-backup.timer`). **How long it is
kept:** 7 daily, 4 weekly and 6 monthly snapshots.

**How a missed backup is noticed:** after every fully successful run
`backup.sh` touches `/opt/tracker/last-backup-ok`. Every morning at 06:00
UTC the GitHub workflow `backup-check.yml` logs in and fails if that file
is older than 26 hours; a failed scheduled workflow makes GitHub e-mail
the repository owner.

**To run a backup now** (as `root` on the server):
`systemctl start tracker-backup.service`, then
`journalctl -u tracker-backup.service -n 30` to read what it did.

**To check that a backup can be read** (touches nothing live): as `root`,
`mkdir /root/restore-check && /opt/tracker/restore.sh latest /root/restore-check`.
It prints the number of rows in every table of the restored copy and the
number of media files, then the one command that removes the throwaway
copy again.

**To actually restore after losing the server** — not rehearsed end to
end yet, and prod data is the developer's call, so do this with the
builder, not from memory: new server → `provision.sh` → put `backup.env`
back (storage address and key from Backblaze, restic password from the
password manager) → deploy → `restore.sh latest <dir>` to fetch the
snapshot → load `db.sql` into the live database and copy the media files
into the media volume.

## Target (not built yet)

Filled in phase by phase as each piece becomes real — see
`.claude/ROADMAP.md`. Planned shape: GitHub repo with CI → images in GHCR →
one VPS running Docker Compose (Caddy for HTTPS, app, Postgres; prod and
one staging slot) → nightly off-box backups → error tracking and uptime
check that open GitHub issues → agents in GitHub Actions that turn issues
into pull requests.

Sections to be written when they become true:

- The rest of the path of a change (issue → spec → … → staging → prod)
- How to stop the factory
- What it costs per month
