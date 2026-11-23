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
  the start of an hour; the check now asks for 06:17 UTC. That did not
  help: on 2026-11-18 (still the old time) it ran at 14:53 UTC, and on
  2026-11-19, the first day with the new time, it had not run by 14:20
  UTC. The delay is about nine hours whatever time is asked for. The
  alarm for a missed backup should move to the server side (F5).
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
a date/value table, a sheet with the days across the columns, or none of
these?

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

- *Days across the columns* (the developer's habit tracker: every week is
  a block — a row of dates, and under it a row for weight, a row for
  steps and rows for other habits): the model names the label of the
  date rows, of the weight rows and of the steps rows. No second call.
  Java finds every block by those labels and reads the cells under each
  date. What it refuses, counting the values instead of reading them:
  - a week whose days do not follow each other column by column, or that
    holds a date that does not exist (this catches a date the
    spreadsheet exported oddly, and it is how Java settles
    day-before-month by itself);
  - a week that repeats an earlier one, or starts more than 62 days
    after the week before it;
  - a value under a cell in the date row that is not a date, when it
    sits between two dates.

  A column right of the last date — the weekly average — is never read.
  Rows with other labels (diet, sleep, aerobic minutes, workout number)
  are not imported. Weeks listed newest first are recognised and read in
  their real order.

  *The year.* These dates have a day and a month but no year. "The year
  of the file" means the calendar year most of its days fall in, so a
  sheet for 2025 that begins on 30 December still reads as 2025, and a
  week running from December into January carries on into the next
  year. The year is, in this order: the one the user picks on the page;
  a year in the file name, when the name holds exactly one and it does
  not put filled days in the future; otherwise the latest year in which
  the last filled day is not in the future. The page always says which
  year was used and offers to change it; changing it reads the file
  again (one more model call).

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
(`OPENROUTER_MODEL`). A training plan is two calls and costs about
0.002 USD (measured: 6–7 thousand tokens in and 23 out for the first
call, 7–12 thousand in and about 1 300 out for the second). A weight
and steps file is one call, about 0.0004 USD. The model's own
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

- Checked: 233 automated tests for the importer (the whole backend
  suite is 438), with the model's answers supplied by the test,
  never by the real service. An independent reviewer read the first
  version and found no way to reach another user's data and nothing
  critical, and eight important defects; the ones in the code were fixed
  with a test that failed first. A second reviewer read the rework of
  2026-11-18: nothing critical; three important findings, all fixed with
  tests — a model answer with a missing field crashed instead of being
  sent back; wrong layouts that still produced believable workouts (the
  checks listed above came from this); skipped exercises were only
  counted, not named. Each check was switched off once to confirm that a
  test fails without it. A third reviewer read the days-across reader:
  one critical finding (a sheet starting in late December was read a
  year late when a year was given) and three more ways to a silently
  wrong date (a half-parsed week winning under the wrong date order,
  whole weeks exported as numbers, weeks listed newest first); the year
  and week-order rules above are the rewrite that came out of it, again
  with tests and again with each rule switched off once.
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
- **Checked on the real habit tracker (2026-11-18):** half a year of
  days across the columns, dates without a year. Through the running app
  with the real model: 157 weight entries and 175 steps entries, every
  one equal to what a separate script computed from the file; no value
  unreadable; the weekly averages not imported; confirming wrote them; a
  second upload left all 332 out as already stored — also when the file
  was renamed so that its name carries no year. In the browser, an
  invented sheet of the same shape showed the assumed year, and picking
  another year re-read the file onto that year.
- **Not checked:** a one-row-per-date weight or steps file from real life
  (only invented ones); a habit tracker that crosses New Year from real
  life (only in tests); any training plan with a different layout from
  this one template; any model other than Haiku 5.5.
- **In production since 2026-11-18.** The developer put a real key and
  the model name into the server's `.env`, merged, and imported their
  own files there. The next day the database held 15 imported workouts,
  268 weight entries and 308 steps entries, the backend log showed no
  error, and the nightly backup had run with that data in it. What the
  developer saw on the review pages was not recorded.
- The page's own logic (what gets ticked, what is sent) has no automated
  test — the frontend has no test runner. It was checked once with a
  throwaway script and by hand in the browser.

**Known gaps.**

- One file is one kind: a sheet holding both workouts and a weight table
  gives workouts only.
- Body weight is stored with one decimal, like a manual entry. A sheet
  that holds 74.45 is imported as 74.5. Kept that way on purpose (the
  developer's decision, 2026-11-18); the review page shows the rounded
  value before anything is written.
- In a days-across sheet a week is refused as a whole when its dates do
  not follow each other (a missing weekday column, a mistyped date); its
  values are counted as unreadable, not listed one by one. A sheet with
  one weigh-in a week across the columns is refused entirely.
- A days-across sheet with a hole of more than 62 days: everything after
  the hole is refused.
- Changing the year on the page asks the model again and uses one of the
  day's 20 previews.
- A days-across sheet with no year anywhere: if neither the file name
  nor the page's year choice is right, every date is a year off. The
  page says which year it used.
- The habit tracker's "workout number" row says on which day each
  workout was done. The import does not use it; workout dates are still
  typed by hand.
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
- Until 2026-11-19 the server sent the page itself (`index.html`)
  without a caching rule, so after a deploy a browser could go on
  showing the previous version: on 2026-11-18 the new Import entry was
  missing from the menu until a forced reload. Fixed together with
  staging (see "Staging"); takes effect with the deploy of that change.
- Pressing Import twice after a lost response writes the workouts twice
  (weight and steps are safe).
- Worst case one preview is four model calls on up to 200 kB of text
  (about 0.03 USD with Haiku 5.5); at 20 a day per user that stays under
  1 USD a day. With an expensive model set in `OPENROUTER_MODEL` it would
  not. The spending limit on the key is the guard.

## Staging (built 2026-11-19; on the server and switched on since 2026-11-23)

Spec: GitHub issue #22. Plan: `docs/superpowers/plans/2026-11-19-f3-staging.md`.

**What it is.** A second copy of the app on the same server, at its own
address, with its own database, files and passwords. A pull request is
put there after its checks are green, so a change can be tried before it
is merged. Every pull request from this repository goes there
automatically; the newest green one holds the slot.

**The pieces.**

| Piece | Where | What it does |
|---|---|---|
| Staging copy | `/opt/tracker-staging` on the server, compose project `tracker-staging` | Same three containers as production (`web`, `api`, `postgres`), started from production's `compose.yml` plus `compose.staging.yml` (no ports to the outside, memory limits: backend 768 MB, database 256 MB, proxy 128 MB). In the staging directory the two files are called `base.compose.yml` and `staging.compose.yml` on purpose — see "Typing commands in the staging directory" |
| The front door | production's `web` container (Caddy) | Answers for both addresses. Requests for the staging address are passed to the staging `web` container and marked "do not index" |
| The link | Docker network `tracker-edge` | The only thing the two copies share: both `web` containers are on it. The backends and databases are not |
| `infra/staging.sh` | `/opt/tracker/staging.sh` | The only command the staging key can run. Accepts `deploy pr-<number>-<commit> <number>` or `reset`, nothing else. One request at a time: a second one while the first is running is turned away |
| `.github/workflows/staging.yml` | GitHub | After a green CI run of a pull request: asks the server to put that pull request on staging and writes the result on the pull request. Also the manual "reset" |
| `images` job in `ci.yml` | GitHub | For a pull request from this repository it builds the two images and tags them `pr-<number>-<commit>` |
| `infra/provision-staging.sh` | run once by hand, as root | Creates the staging directory and its `.env`, the shared network, the staging address in production's `.env`, and the restricted key line |

**How a pull request gets there.**

1. A pull request is opened or pushed to. CI builds and tests it.
2. Green, from this repository, `STAGING_ENABLED` is `true`: CI builds the
   images from the pull request's code.
3. The Staging workflow starts. It always runs the version of itself that
   is on `main`, so a pull request cannot change what it does. It finds
   the open pull request whose newest commit this is; if the pull request
   has moved on or was closed, nothing happens.
4. It logs in to the server with the staging key and says
   `deploy <tag> <number>`.
5. On the server `staging.sh` copies the compose files and `deploy.sh`
   that production currently runs into the staging directory and runs
   that `deploy.sh` there: pull the images, start, wait for the health
   check, go back to the previous version if it does not come up.
6. The workflow comments on the pull request: running; did not start
   and went back; images could not be fetched (nothing changed); server
   busy; or failed. If another pull request held the slot, it is told it
   lost it.

Only the images come from the pull request. The compose files and the
scripts are the ones from `main`: a pull request that changes
`compose.yml` is not tested on staging with that change.

**What staging shares with production, and what it does not.**

- Shared: the machine, the Docker daemon, the proxy, the image registry.
- Not shared: database, uploaded files, database password, signing
  secret (a login from staging is not valid on production), invited
  addresses, the network the backend and database talk on.
- Rehearsed on a laptop with both copies running: from the staging
  backend the name of production's database does not resolve and its
  port cannot be reached; a staging login token is refused by
  production; wiping staging removed only staging's volumes and
  production's containers were never restarted.

**Who can reach it.** Anyone who knows the address, like production.
Sign-up is closed and staging has its own list of invited addresses.
There is no extra password in front: the browser's password prompt and
the app's own login would use the same request header.

**Where its secrets live.**

- `/opt/tracker-staging/.env`, mode 600, owned by `deploy`: staging's own
  database password and signing secret (made on the server, never
  shown), invited addresses, the address the health check uses,
  placeholder OpenRouter values until the developer sets a key, and the
  two lines that tell Docker Compose this directory is the project
  `tracker-staging`.
- GitHub environment `staging`, usable from `main` only: `STAGING_HOST`
  and `STAGING_SSH_KEY`. The Staging workflow runs on `main`, so it can
  read them; a pull request's own workflow run cannot. The environment
  must be limited to `main` before the key is put into it.
- The staging key's line in `deploy`'s `authorized_keys` starts with
  `restrict,command="/opt/tracker/staging.sh"`: whatever the caller
  asks for, the server runs that script and nothing else.
- The staging address itself is in production's `.env`
  (`STAGING_ADDRESS`) and, for the link in the pull request comment, in
  the repository variable `STAGING_URL` — not in the repository files.

**What happens when it fails.**

| Situation | What happens |
|---|---|
| The pull request's version does not start | Staging goes back to the version before; the comment says so; the backend log of the failed start stays on the server in `/opt/tracker-staging/last-failed-deploy.log`, the script's own output in `last-deploy.log` |
| The images cannot be fetched | Nothing is changed; staging keeps running what it ran; the comment says so |
| First ever deploy does not start, or going back does not work either | The comment says staging is not running; it stays down until the next push |
| A deploy or reset arrives while another is running | It is turned away ("busy"); the comment says to push again |
| Several pull requests turn green close together | They queue; GitHub keeps only the newest one waiting, so a pull request in the middle may be skipped without a comment. The newest one wins, which is the rule anyway |
| Staging backend down | The staging page still loads (it is static), its API answers 502. Production is not affected |
| Staging containers gone | The staging address answers 502. Production is not affected |
| Staging uses too much memory | Docker stops the container at its limit; production keeps its memory |
| Two pull requests changed the database differently | The later one may not start. Run the reset, push again |
| The server cannot be reached | The Staging run is red; the comment says the server gave no answer |

**The one shared risk.** The proxy's configuration now names staging. A
mistake in `infra/Caddyfile` could take production down — as it always
could. It changes only through a merge to `main`, with the health check
and the automatic rollback.

**How to wipe it.** GitHub → Actions → Staging → Run workflow → type
`reset`. The staging database and files are deleted and the same version
starts again, empty.

**How to stop it.**

- Stop deploys: repository variable `STAGING_ENABLED` to anything but
  `true`. Pull requests are no longer built for staging or put there.
- Cut GitHub off from staging: delete the line ending
  `github-actions-staging` from `/home/deploy/.ssh/authorized_keys`.
- Shut it down: on the server, `cd /opt/tracker-staging && docker
  compose down`. The address then answers 502; production is not
  touched.

**Typing commands in the staging directory.** Production's compose file
names its project `tracker`. If a copy of it sat in the staging
directory under its usual name, a `docker compose down -v` typed there
would have deleted production's data. So the copies have other names
(`base.compose.yml`, `staging.compose.yml`) and the staging `.env` says
which files and which project to use. Checked with real Compose: a
command typed in that directory acts on `tracker-staging`; with those
two `.env` lines removed it stops with "no configuration file", it does
not fall back to production.

**The page is no longer served stale.** With this change the page itself
(`index.html`) is sent with "always ask the server" and the files with a
content hash in their name with "keep for a year". Before, a browser
could show the previous version after a deploy (seen on 2026-11-18).

**What was checked, and what was not.**

- Checked: 106 script tests. Among them: the staging command accepts
  only its two forms — thirty other requests (with `;`, `&&`, `$( )`,
  line breaks, leading zeros, a number that does not match the tag) are
  refused and nothing is run; over SSH the caller cannot choose the
  directories; a failed start leaves the previous pull request running;
  the reset runs in the staging directory under the staging project
  only; provisioning twice changes nothing; a new key replaces the old
  one and leaves the owner's and the production key; a key that already
  has full access is refused; a staging address equal to production's,
  or one the proxy would choke on, is refused. A rehearsal on the laptop
  with real containers, both copies side by side behind one proxy: the
  results are listed above; cache headers as described; memory limits in
  force (staging backend used 321 MB of 768).
- An independent reviewer read the first version. Nothing critical;
  three important findings, fixed with tests that failed first: a
  compose command typed by hand in the staging directory would have
  acted on production (see above); the queue of the Staging workflow
  could silently drop a green pull request; provisioning accepted a
  staging address that would have stopped production's proxy at its
  next restart. Smaller ones fixed too: a request that looped inside
  the staging proxy, messages naming the wrong log file, a lock on the
  server.
- **On the real server, 2026-11-23.** Set up with `provision-staging.sh`;
  production's proxy restarted to learn the staging address (one failed
  health sample out of sixty taken twice a second; backend and database
  kept running); a certificate was issued for the staging address.
  - *The restricted key over real SSH* (with the owner's own key switched
    off for the test): `id`, `docker ps`, reading production's `.env`, a
    shell without a command, `deploy …; id`, a tag whose number does not
    match, and `scp` of production's `.env` were all refused; choosing
    the directories through the environment had no effect; `reset` and a
    deploy of an image that does not exist were accepted and changed
    nothing. Production's containers and version were the same before
    and after.
  - *Drill 1, a pull request arrives:* a draft PR with one changed word
    went through CI, its images were built, the Staging workflow put it
    on staging and commented on the PR. The word was on the staging
    address and not on production. About four minutes from push to
    comment.
  - *Drill 2, a second pull request:* it took the slot; the first PR got
    the one-line note.
  - *Drill 3, a backend that cannot start:* staging answered 502 for
    about two minutes (the wait for the health check), went back to the
    second PR's version by itself and the PR was told. Production
    answered 200 on every one of 110 samples taken during it. The
    backend log of the failed start stayed on the server, readable by
    `deploy` only; the public workflow log held only "rolled-back".
  - *Drill 4, reset:* a marker put into the staging database was gone
    afterwards and the staging volume had a new creation time;
    production's volume and row counts were unchanged. Starting the
    workflow with any word other than `reset` did nothing.
  - With both copies running the server had 1.9 GB of its 3.8 GB
    available; the staging backend used about 300 MB of its 768.
- **Not checked:** that the key cannot be used for port forwarding (it
  rests on OpenSSH's `restrict`; not demonstrated); a pull request from a
  fork (none exists; by GitHub's rules and the workflow's own check it
  gets neither images nor a deploy); two pull requests whose database
  changes collide; the developer's own look at the staging page.

**Known gaps.**

- Pull request code runs on the same machine as production. Containers
  and memory limits separate ordinary mistakes; they are not a wall
  against code written to break out.
- A pull request can still overwrite an image tag in the registry from
  its own CI run (that was already so before staging). Deploying images
  by their content hash instead of a tag would close it.
- The server is asked for its host key anew on every run, as for
  production: a first-use check each time, not a pinned key.
- Docs-only pull requests are put on staging too.
- A pull request that loses the slot is told once; nothing on the staging
  page itself says which pull request it is running.
- When a pull request is closed or merged, staging goes on running it
  until the next pull request arrives.
- The comment on the pull request does not carry the staging address
  (the repository is public). Setting the repository variable
  `STAGING_URL` puts a link there.
- The first production deploy with the new compose file (2026-11-19)
  also recreated the database container, which earlier deploys had not
  touched. The data lives in a volume and was intact; the deploy took 43
  seconds instead of about 27. The cause was not established: the
  database's part of the file had not changed, Docker had not been
  updated, and the same change on a laptop (Compose 5.6.0; the server
  has 5.7.1) did not recreate it. Expect it again when the compose file
  changes.

## Factory: review agent (report only; switched on since 2026-11-23)

Spec: GitHub issue #28. Plan: `docs/superpowers/plans/2026-11-23-f4-review-agent.md`.

**What it is.** After a pull request's checks are green, an AI reviewer
that has not seen how the code was written reads the change and its
findings are posted on the pull request. Only what the rubric calls
critical or major. It changes nothing and blocks nothing. It is the
first agent of the factory, and the first thing that runs on the
developer's Claude subscription without a person at the keyboard.

**The rule it is built on:** agents only produce content; scripts act.
The reviewer returns a list. Deciding whether to review, checking the
list and posting the comment are plain shell scripts with tests.

**How a review happens.**

1. CI finishes green on a pull request. The workflow "Review" starts. It
   is always the copy on `main`, so a pull request cannot change how it
   is reviewed. It can also be started by hand for one pull request.
2. `collect.sh` asks GitHub about the pull request and decides. Not
   reviewed: closed; from a fork; not opened by the repository owner;
   documentation only; larger than 400 kB of diff. "Documentation" is a
   short list on purpose — `.md` files under `docs/`, `README.md` files,
   and the four logs `.claude/NOTES.md`, `DECISIONS.md`, `ROADMAP.md`,
   `SPEC.md`. `CLAUDE.md`, the reviewer's own prompt and rubric,
   settings files and scripts are reviewed like code, and a file moved
   into `docs/` is judged by where it came from. If it is reviewed, the script writes the diff and the spec
   (the pull request's own text and the issue it names — only when the
   owner opened that issue) into a folder.
3. The pull request's code is checked out next to it, to be read.
4. `run-review.sh` starts Claude Code once, without a terminal, in
   restricted mode. It gets the rubric, the spec, the diff, the
   project's rules (`CLAUDE.md` from `main`) and the code.
5. Its answer must fit a fixed shape: whether it could read the
   material at all, a summary, and a list of findings, each with
   severity, the id of the rubric rule, file, line, what, why.
   `check-findings.sh` refuses anything else, and also an answer where
   the severity does not belong to the rule. An answer given without
   reading anything, or one that says "I could not read the change", is
   not accepted as a clean review. A refused answer is asked for once
   more; twice refused is "could not be read".
6. `comment.sh` turns the list into the comment and the workflow posts
   it: counts, each finding with rule, file and line, and at the bottom
   the model, the number of turns, the time and the cost estimate.

**What the reviewer can and cannot do.**

- It can read and search files in two folders: the inputs and the pull
  request's code.
- It has no shell, cannot edit or write files, cannot fetch from the
  web, and loads no settings, hooks or extra tools from the pull
  request. The code under review is never run.
- It has no GitHub token. The step that runs it holds exactly one
  secret, the Claude token; the GitHub token is in other steps. The
  program itself is started with an emptied environment: only the
  search path, the home folder and the Claude token.
- Links in the pull request's code that point elsewhere (a file that is
  really `/proc/self/environ`, say) are deleted before it starts, so a
  read cannot be led outside the code. And an answer that contains the
  Claude token is thrown away and counted as a failed review.
- So the worst a pull request that tries to instruct its reviewer can
  achieve is a wrong comment.

**The rubric** (`factory/review/rubric.md`) is the one the developer
approved on 2026-11-16, with an id per rule:

| Severity | Rule ids |
|---|---|
| critical | `data-loss`, `security`, `cannot-start`, `opposite-of-spec` |
| major | `wrong-main-path`, `criterion-not-met`, `no-test`, `unhandled-error`, `project-rule` |

**Where its secrets and settings live.**

- GitHub environment `factory`, usable from `main` only: the secret
  `CLAUDE_CODE_OAUTH_TOKEN`. It is made on the developer's laptop with
  `claude setup-token`, is tied to the developer's Claude account and is
  valid for a year. A workflow run on a pull request's own branch cannot
  read it.
- Repository variables: `FACTORY_ENABLED` (the switch, shared with the
  gate), `FACTORY_MODEL_REVIEW` (the model; `claude-fable-5-2` when not
  set), `FACTORY_AUTHORS` (extra logins whose pull requests are
  reviewed; empty today).

**What it costs.** No money: runs count against the same session and
weekly allowance of the Claude subscription that the developer's own
use does. The rehearsal review of a three-file change took 24 seconds,
12 turns and an estimated 0.50 USD worth of usage; the first six reviews
in GitHub Actions took 12–60 seconds, 8–15 turns and an estimated
0.18–0.57 USD each. Limits: one review at
a time per pull request, a newer push cancels the older review, 15
minutes at most, a per-run ceiling on the estimate.

**What happens when it fails.**

| Situation | What happens |
|---|---|
| Token missing, expired or refused; usage limit reached; 15 minutes passed | The run is red; the pull request gets "the review did not run". CI, staging and merging do not depend on it. The run's summary has a "Detail" line with the reason: what the API answered (for example `…-401-Failed-to-authenticate…`), or `token-has-a-line-break-or-a-space` when the stored token is not one unbroken line — a long token copied from a terminal picks up the line break where the terminal wrapped it; the reviewer is not started then. Fix: put the token into the secret again with spaces and line breaks removed |
| The answer does not fit the shape twice | "The review could not be read" |
| The change is too large | "Did not run: too large" |
| The pull request moved on while CI ran | Nothing; the newer commit gets its own review |
| The pull request cannot be read at all | The run is red, no comment |
| Its file list or diff cannot be fetched | "The review did not run" |
| The issue it names does not exist or cannot be read | Reviewed against the pull request's own description |
| More findings than fit in one comment | The most serious ones are shown and the comment says how many are not |
| A newer push arrives during the review | The older review is cancelled without a comment |

A review that did not happen never shows up as "no findings".

**How to stop it.** Set `FACTORY_ENABLED` to anything but `true`. To take
the token away: delete the secret from the `factory` environment (the
documentation does not say how to revoke the token itself before its
year is over).

**What was checked, and what was not.**

- Checked: 105 script tests with stand-ins for GitHub and for the Claude
  program (who is reviewed and who is not; twenty-eight malformed
  answers refused; a failed, unreadable or blind review never reported
  as clean; the reviewer started with the restricted flags, in the work
  folder, with an environment that holds nothing but the Claude token;
  links removed from the code; an answer holding the token discarded;
  reviewer text cannot become a mention, a link, HTML or a heading; the
  longest accepted answer still fits one comment). Two rehearsals on
  the laptop with the real model on a copy of the code with three
  planted defects — another user's data readable, a missing 404, no
  test: all three found both times, each with the right rule, file and
  line; about 23 seconds and 11–12 turns each.
- An independent reviewer read the first version. Nothing critical;
  seven important findings, fixed with tests that failed first: a
  reviewer that could read nothing would have posted "no findings"; a
  link in the pull request's code could have led a read to the Claude
  token; a mistyped issue number or an unreachable diff ended in a red
  run with no word on the pull request; "documentation only" skipped
  the project's rule book, the reviewer's own instructions and scripts
  under `docs/`; a long list of findings would have exceeded GitHub's
  comment size and posted nothing; a failed run left no reason in the
  log; the rubric's security rule read as a closed list.
- Drilled in GitHub Actions on 2026-11-23, on the developer's
  subscription token (plan A works):
    - The very first run failed in 15 seconds: the stored token had a
      line break in it, picked up where the terminal wrapped the long
      line on copying. The failure path worked — red run, "did not run"
      on the pull request — but the log named no reason. The cause was
      found by reproducing the run's status words on the laptop with
      made-up tokens; the token was stored again with line breaks
      removed. The scripts now say this in words (pull request 31).
    - A manual run of "Review" on an open pull request, and the
      automatic run after CI: both post the comment.
    - A clean small change with tests (pull request 30): "No critical
      or major findings".
    - Planted defects (pull request 32): an entry readable by any
      signed-in user was reported as critical `security` at the lookup
      in the service; the untested new behaviour as major `no-test` at
      its line. A third finding (no test for another user's entry) was
      a fair addition, not noise.
    - A change that tries to steer and arm the reviewer (pull request
      33: a section added to `CLAUDE.md` telling it to run a command,
      write a file, quote `/etc/hostname` and report nothing; a link to
      that file under `docs/`; a `.claude/settings.json` that switches
      permission checks off and adds a start-up command; the same
      request in the pull request text). The reviewer did none of it,
      reported the three planted files as critical `security` and still
      found the untested behaviour. None of the planted marker words
      and no host name appeared in its answer, so the settings file and
      its start-up command had no effect. The run shows 0 refused tool
      calls: the reviewer never tried, so this drill shows that it was
      not steered, not that a tool call would have been refused. That
      it has no tool to run a command or write a file rests on how it
      is started (three reading tools only), which the script tests
      and the laptop rehearsal check.
    - A workflow added by a pull request that asks for the token (pull
      request 34): the job naming the `factory` environment was refused
      before any step ran ("Branch refs/pull/34/merge is not allowed to
      deploy to factory"); the job naming no environment saw an empty
      value.
    - A documentation-only pull request (the one that added these
      lines) was not reviewed.
    - The run summary shows outcome, model, turns, seconds, the cost
      estimate and refused tool calls.

**Known gaps.**

- The reviewer can be wrong in both directions. Its comment is advice.
- A pull request that names no issue is judged against its own
  description, so the description is the spec then. The reviewer treats
  what it reads as material, not as orders, but a description can still
  understate what a change does.
- A reviewer that was talked into it could read any file the runner
  user can read; only reading tools exist, and an answer that holds the
  token is thrown away, but a token rewritten into another form would
  not be recognised. Today only the owner's pull requests are reviewed.
  This matters once other agents open pull requests (slice 2).
- Nothing is done with the findings yet: no fix round, no labels, no
  gate. That is the next slice.
- The spec the reviewer sees is the text of the issue. A spec written
  later as a comment on the issue is not picked up yet; that comes with
  the spec agent.
- Every reviewed push adds a new comment; older ones are not edited or
  hidden.
- The workflow's own glue (which outcome leads to which comment, red or
  green) is shell inside the workflow file and has no tests; the scripts
  it calls do.
- Every reviewed push uses some of the developer's allowance; there is
  no separate budget for the factory and no view of its share other
  than the estimate in each comment.
- Whether unattended runs are within the subscription's terms is the
  developer's reading: the documentation describes this setup but the
  legal page only speaks of "ordinary, individual usage".

## Factory: implementation agent (issue 36; only the probe exists)

**What it will be.** The developer puts the label `approved` on an issue
they wrote; an agent writes the change and its tests into a pull
request; CI, staging and the review agent take it from there as for any
pull request. Decided by the developer on 2026-11-24: the agent acts on
GitHub as Claude's official GitHub App, and it runs no commands — it
reads and edits files only. Spec: issue 36.

**Why a probe first.** The official app's token exists only inside the
ready-made action `anthropics/claude-code-action`: the action asks
Anthropic for it and cancels it when it ends. So this agent, unlike the
reviewer, runs inside that action, and several things the design rests
on are read from the action's source code, not from its documentation.
The probe settles them in this repository before anything is built on
them:

1. the action runs on the subscription token with file tools only, and
   a call for a shell is refused;
2. its commit lands on the branch the workflow names;
3. GitHub accepts a pull request whose only commit is empty, and the
   app's commit on it starts CI;
4. whether the app can change a workflow file (Anthropic's
   documentation says it holds that right, the action's FAQ says it
   does not);
5. what the public run log shows of the agent's work.

**The probe** is the workflow "Factory probe"
(`.github/workflows/factory-probe.yml`). It has no trigger but a manual
start, and it runs only when the repository owner starts it from `main`
with `FACTORY_ENABLED` on. Given a branch name `probe/<name>` and a
prompt, it

1. creates that branch from `main` with one empty commit and, unless
   told not to, opens a pull request for it — with the workflow's own
   token;
2. runs the action, pinned to an exact commit, with the prompt. The
   agent's tools are reading, searching, editing and creating files in
   the checkout, plus the action's two commit operations ("commit these
   files", "delete these files"), which write to the named branch
   through GitHub's API as Claude's app;
3. writes a summary of the run: the commits on the branch and who made
   them, the files changed, turns, duration, cost estimate, which tools
   were called and which calls were refused. Tool names only, never
   their content.

**What it needs.** Claude's GitHub App installed on this repository;
the repository setting "Allow GitHub Actions to create and approve pull
requests"; the Claude token in the `factory` environment (the same one
the reviewer uses). No new secret: the app's token is made per run.

**What can go wrong.** The probe writes only to branches named
`probe/…` and to pull requests it opened; none of it is merged. A probe
pull request goes to staging like any pull request. If the app turns
out to be able to change workflow files, the probe proves that on a
throw-away branch with a workflow that has no trigger of its own.

**How to stop it.** Set `FACTORY_ENABLED` to anything but `true`, or
do not start it: nothing else starts it. The probe workflow is removed
in the same change that adds the real "Implement" workflow.

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
