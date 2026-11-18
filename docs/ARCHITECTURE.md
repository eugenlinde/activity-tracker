# Architecture

The one living description of what runs where. Updated in the same change
as any infra or factory change. If this file and reality disagree, that is
a bug.

Last updated: 2026-11-17 (CSV import built; the real AI model has not been tried yet).

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
containers back (`restart: unless-stopped`). Proven with a real reboot on
2026-11-16: the site was unreachable for 29 seconds and came back without
anyone touching it.

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
| LLM calls | OpenRouter API, used by the CSV import — see "CSV import" below | called from the backend |
| Tests | JUnit suite (323 tests); database tests start their own Postgres via Testcontainers — image `postgres:latest`, not pinned to 17 like the dev database (to be pinned, see backlog) | laptop, `./gradlew test`, and CI |
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
| `backup-check.yml` | Every day at 06:17 UTC: logs in to the server and fails unless `last-backup-ok` is younger than 26 hours | A backup that silently stopped is worse than none, because everyone believes it exists | The workflow goes red and GitHub emails the owner |
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
- Reboot drill, 2026-11-16: the server was restarted on purpose. The
  site was down for 29 seconds. Afterwards, with nothing done by hand:
  Docker active, all three containers running, firewall active with the
  same rules, backup timer enabled and scheduled, password logins still
  refused, no failed system services, the account still in the database.
- The night of 2026-11-17 the backup timer fired by itself at 03:30 UTC
  and the backup succeeded. **The 06:00 UTC backup check ran at 15:19
  UTC, nine hours late**, and passed. On 2026-11-18 it had not run by
  09:30 UTC. GitHub says scheduled runs can be delayed or dropped at
  the start of an hour; the check now asks for 06:17 UTC. Whether that
  shortens the delay is not known yet.
- **What a late check means:** a missed backup is still noticed, only
  hours later than the table above says. If the check were ever more
  than 21 hours late and the next night's backup succeeded in between,
  one missed night would pass without an alarm.
- **Not checked yet:** a restore with workouts and media in it (only an
  account existed), a rollback where the new version had changed the
  database schema, and how late the backup check runs with the new
  time.

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
`backup.sh` touches `/opt/tracker/last-backup-ok`. Every morning at 06:17
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

## CSV import

Lets a signed-in user bring history in from a spreadsheet: workouts, body
weight and daily steps. Spec: GitHub issue #18.

**The flow.** The Import page uploads a `.csv` file to
`POST /api/imports/preview`. The backend reads it, shows what it
understood, and writes nothing. The user ticks what they want, gives each
workout a date, and `POST /api/imports/confirm` writes the selection in
one database transaction — all of it or none.

**What the AI model is asked, and what it is not.** The file's text goes
to OpenRouter (a third party; the page says so before upload). **The
model never copies a value.** It only says where things are; Java reads
every number and every name from the cells itself. The first call sees
only the first 40 rows and answers one question: is this a workout grid,
a date/value table, or neither?

- *Workout grid* (exercises down the rows grouped into workouts, week
  blocks across the columns): a second call gets the whole sheet, written
  out with a number on every row and every column, and answers with the
  layout only — which column holds the exercise names, which columns
  belong to each week block (weight, repetitions, reps in reserve,
  comment), and which rows belong to each workout. Java checks that
  answer against the sheet before using it:
  - every column is named once;
  - every week block has its columns in the same order and spacing as
    the first one, and the blocks go from left to right;
  - every week title and workout title the model gives is the text of a
    real cell in the right place (above the workouts and inside that
    block; between the previous workout and this one);
  - no two workouts share rows, and no row that looks like an exercise
    (a name and repetitions) is left outside every workout.

  If a check fails, the model is asked once more with the list of
  problems; if it still fails, the import is refused. Then Java walks
  the cells: a workout in a week is taken when at least one of its
  exercises has repetitions written; an exercise whose repetitions or
  weight are not plain numbers (`3x10`, `raske`, `kehakaal`) is skipped,
  never guessed, and the page lists every skipped one by row, exercise
  and workout.
- *Date/value table* (weight, steps): the model only says which column is
  the date and which is the value, and guesses the order of day, month
  and year. Java reads every row itself — and does not take the date
  order on trust: it uses the order under which the most rows are real
  dates, and when that does not settle it, the order that keeps the dates
  closest together.

**Why the model does not copy the workouts out.** It was built that way
first, and measured on a real training plan with Claude Haiku 5.5 on
2026-11-18. On the file as it was (three weeks filled) every answer was
exact. On the same plan filled to ten weeks, two of five answers had
silent errors — a wrong weight, an exercise from another workout, an
exercise name reworded — a third was rejected by the app's own checks,
and one answer took 34 000 tokens and 113 seconds. Asked for the layout only, the model gave the identical answer
in 14 of 14 runs in about five seconds, and the result matched the file
value for value.

**The model in use.** `anthropic/claude-haiku-5.5` through OpenRouter
(`OPENROUTER_MODEL`). One import is two calls and costs about 0.002 USD
(measured: 6–7 thousand tokens in and 23 out for the first call, 7–12
thousand in and about 1 300 out for the second). The model's own
"thinking" is left at its default; this model does not accept the
temperature setting, so the request's `temperature: 0` has no effect.

**Repeated imports.** Every imported workout keeps a fingerprint of its
exercise names, weights and reps (`activities.import_fingerprint`). On
the next import a workout with the same fingerprint is marked "already
imported" and starts unticked; identical workouts are matched by count.
Weight and steps hold one value per day anyway: the same value is left
out, a different one is shown next to the stored one and starts unticked.

**Limits.** Only `.csv`; 10 MB upload; at most 200 kB of text is sent to
the model; 20 previews per user per day. The daily count lives in the
backend's memory, so a restart (every deploy) resets it — the real guard
on spending is the limit set on the OpenRouter key itself.

**When it fails.** Not a CSV → 415, nothing sent to the model. Empty →
400. Too large (over 200 kB of text, more than 200 workouts, or a
workout with more than 12 exercises) → 413 saying which. Over the daily
limit → 429. The model unreachable or its answer unusable → 502 with a
plain message; the backend log holds the reason (the upstream status
code, never the upstream message and never the file's content).

**What was checked, and what was not.**

- Checked: 191 automated tests for the importer (the whole backend
  suite is 396), with the model's answers supplied by the test,
  never by the real service. An independent reviewer read the first
  version and found no way to reach another user's data and nothing
  critical, and eight important defects; the ones in the code were fixed
  with a test that failed first. A second reviewer read the rework of
  2026-11-18: nothing critical; three important findings, all fixed with
  tests — a model answer with a missing field crashed instead of being
  sent back; wrong layouts that still produced believable workouts (the
  checks listed above came from this); skipped exercises were only
  counted, not named. Each check was switched off once to confirm that a
  test fails without it.
- **Checked against the real model (2026-11-18, Claude Haiku 5.5, the
  developer's key):** one real training plan (3 workouts, 3 of 10 weeks
  filled, a long FAQ under the grid) and the same plan filled to 10
  weeks. Through the running app on the laptop — real backend, real
  Postgres, real model: the real file gave 9 workouts with all 308
  values equal to the file, in 8 seconds; confirming wrote 9 workouts; a
  second upload marked all 9 as already imported. The ten-week file gave
  30 workouts with all 1 141 values equal, five times, in 5–7 seconds. An
  invented sheet with a `3x10` cell and a weight written `kehakaal`
  listed the three skipped entries by row in the browser and read the
  rest. Two invented weight and
  steps tables (Estonian style and US style) were classified with the
  right columns and date order. The key never appeared in the log.
- **Not checked:** a real weight or steps file (none was available — only
  invented ones); any training plan with a different layout from this
  one template; any model other than Haiku 5.5; production, which still
  holds a placeholder key — until the developer sets a real one there,
  every import ends with "could not read this file".
- The page's own logic (what gets ticked, what is sent) has no automated
  test — the frontend has no test runner. It was checked once with a
  throwaway script and by hand in the browser.

**Known gaps.**

- One file is one kind: a sheet holding both workouts and a weight table
  gives workouts only.
- A comma-separated file with no header row and decimal commas
  (`03.11.2026,81,3`) cannot be told from a three-column file and is read
  wrong. With a header row the extra cell is noticed and the row refused.
- Text that is neither UTF-8 nor windows-1257 (some Mac exports) is read
  with wrong letters; the names are stored that way.
- Only one grid layout is understood: exercises down, week blocks across,
  one row per exercise. A log with one row per set, or weeks down the
  rows, gives "nothing to import".
- If the model moves a whole week block onto wrong columns that still
  hold numbers (sets instead of repetitions in every week), the checks do
  not see it and every exercise would be read wrong in the same way. It
  did not happen in 23 runs; the review page is where it would be seen.
- The checks are strict on purpose: a sheet where one week has an extra
  column, or where a stray table with names and numbers sits beside the
  plan, is refused rather than half read.
- A weight written as words (`kehakaal`, `BW`) makes that exercise entry
  skipped, not stored without a weight. It is listed on the page.
- If Excel has turned a typed `12,10` into the number 12,1, the file
  says `12,1` and that is what is read. Not seen in the real file.
- A workout that is half filled in when imported and complete at the next
  import has a different fingerprint, so it shows up as new and would be
  stored a second time. Untick a workout that is not finished yet.
- A workout with more than 12 exercises cannot be stored and makes the
  whole file be refused, with the workout named. An exercise with more
  than 12 sets is skipped and listed.
- Pressing Import twice after a lost response writes the workouts twice
  (weight and steps are safe).
- Worst case one preview is four model calls on up to 200 kB of text
  (about 0.03 USD with Haiku 5.5); at 20 a day per user that stays under
  1 USD a day. With an expensive model set in `OPENROUTER_MODEL` it would
  not. The spending limit on the key is the guard.

## Factory: review gate (part 1 built, no agents yet)

Design: `docs/superpowers/specs/2026-11-16-review-loop-design.md`.

**What it is.** The rule book of the review loop, as a script. A review
agent (not built yet) will read a factory PR and list its major and
critical findings. The gate takes that list, the round number and the CI
result, and decides what happens to the PR. The decision is made by plain
shell code, not by an AI, so the limits cannot be talked around and are
covered by tests.

**The rules.**

| Situation | Decision |
|---|---|
| CI red, round 1 or 2 | fix |
| CI red, round 3 | scrap |
| findings unreadable, first time | run the review again |
| findings unreadable, second time | needs human |
| 6 or more critical findings, any round | scrap |
| no major or critical findings | pass |
| major or critical findings, round 3 | scrap |
| major or critical findings, round 1 or 2 | fix |

After a scrap the analysis agent (not built yet) names the cause. A rebuild
starts only on the first attempt and only when the cause is not "the
review was wrong"; otherwise the issue gets `needs-human`.

**State lives in labels**, so it is visible from the phone:

| Label | On | Meaning |
|---|---|---|
| `factory` | PR | the loop acts on this PR; remove it to take the PR over by hand |
| `review-round-1` / `-2` / `-3` | PR | current round |
| `needs-fix` | PR | the gate asked the fix agent to work |
| `review-passed` | PR | no major or critical findings left; ready for the developer |
| `attempt-1` / `attempt-2` | issue | first build or the one rebuild |
| `needs-analysis` | issue | PR was scrapped; analysis should run |
| `needs-human` | issue or PR | the factory stopped; the developer decides |

**The pieces** (all in `factory/`, tests in `factory/test/run.sh`, run by
the `infra` job of CI with a fake `gh`):

- `gate.sh` — the rules above; reads nothing from GitHub.
- `state.sh` — reads the PR's state, labels and its linked issue
  (`Closes #N` in the PR text; links to issues in other repositories are
  ignored). A closed or merged PR is left alone. Otherwise exactly one
  round label and one linked issue, or it reports the state as broken.
- `apply.sh` — writes the decision to GitHub: a comment with the numbers
  (`Round 2 · CI green · critical 1 · major 3 → fix`), label changes, and
  for a scrap: close the PR, delete its branch, mark the issue
  `needs-analysis`. The closed PR keeps its diff and comments.
- `run-gate.sh` — the three above in order; what the workflow runs.
- `labels.sh` — creates the labels once (safe to run again).
- `.github/workflows/factory-gate.yml` — starts `run-gate.sh`. Today by
  hand (Actions → Factory gate → Run workflow, with a PR number, the CI
  result and findings as JSON); later the review agent calls it. Its
  checkout is pinned to `main`, never the PR, so a PR cannot change the
  rules it is judged by. Empty findings are treated as unreadable, never
  as "no findings", so a caller that forgets to pass them cannot make a
  PR pass. Findings must use the severities critical, major, minor or
  info; anything else counts as unreadable.

**What happens when it fails.**

- Broken label state (no round label, two round labels, no linked issue,
  two attempt labels): the PR, and the issue when known, get
  `needs-human` with a comment saying which; the run is red.
- GitHub unreachable or a `gh` call fails: the run is red. Steps already
  done stay done; if the PR could not be closed, the issue is not marked
  `needs-analysis`. Rerun the workflow once the cause is gone.
- A label missing from the repository makes `gh` fail: run
  `factory/labels.sh --repo eugenlinde/activity-tracker` once before the
  first use.

**How to stop it.** Set the repository variable `FACTORY_ENABLED` to
anything but `true` (Settings → Secrets and variables → Actions →
Variables): every gate run is skipped. To stop it on one PR only, remove
the `factory` label.

**Not built yet (part 2, after plan A or B is chosen):** the review, fix
and analysis agents; report-only review of other PRs; the GitHub App token
the agents need, because labels set with the workflow's own token do not
start other workflows.

## Target (not built yet)

Filled in phase by phase as each piece becomes real — see
`.claude/ROADMAP.md`. Planned shape: GitHub repo with CI → images in GHCR →
one VPS running Docker Compose (Caddy for HTTPS, app, Postgres; prod and
one staging slot) → nightly off-box backups → error tracking and uptime
check that open GitHub issues → agents in GitHub Actions that turn issues
into pull requests.

Sections to be written when they become true:

- The rest of the path of a change (issue → spec → … → staging → prod)
- How to stop the factory as a whole (the review gate already has its switch, see above)
- What it costs per month
