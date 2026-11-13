# F2 — First Deploy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The app runs on the public internet over HTTPS on one small server, only invited people can sign up, every merge to `main` deploys itself after the checks pass, and the data is backed up off the server every night with a restore that has been proven once.

**Architecture:** Three containers on one VPS, run by Docker Compose: `web` (Caddy — terminates HTTPS, serves the built frontend, forwards `/api` to the backend), `api` (the Spring Boot jar), `postgres`. Images are built by GitHub Actions and stored in GHCR. A deploy job in the existing CI workflow runs only on `main` after `backend` and `frontend` pass; it copies two files to the server and runs one script there. Backups are restic snapshots (database dump + media files) to off-server object storage, started by a nightly timer on the server and checked by a daily scheduled workflow.

**Tech Stack:** Docker + Docker Compose, Caddy 2, eclipse-temurin 26 JRE (alpine), PostgreSQL 17 (alpine), GitHub Actions + GHCR, restic, Ubuntu LTS on a Hetzner VPS, Spring Boot Actuator.

**Spec:** `docs/superpowers/specs/2026-11-12-ai-factory-pivot-design.md`

## Global Constraints

- Budget €20/month for VPS, backups, domain, and the app's OpenRouter calls. Expected for F2: about €6–8 (to be checked against real prices when buying).
- Bare minimum only: no staging (F3), no agents (F4), no error tracking (F5), no Kubernetes, no Terraform.
- Secrets never go into git — not in code, config, workflow files, docs, or commit messages. The repo is public.
- The server's IP address is not written into the repo; it lives in a GitHub secret.
- Nothing reaches prod without the developer's approval. A merge to `main` by the developer is that approval; nothing else may deploy.
- Prod holds real data: sign-up is closed before the first public deploy; a fresh JWT secret is generated for prod and the test-profile value is never reused.
- Fork pull requests get no secrets and never deploy.
- `docs/ARCHITECTURE.md` is updated in the same change as any infra change, in plain language.
- The test suite stays green (186 tests today); new behaviour gets tests first.
- Anything that costs money, creates an account, or touches the live server is done only with the developer's explicit go-ahead at that step. Accounts and payments are the developer's own to do.
- Scripts never use `set -x` and never print a secret.
- No comments in code, scripts, or workflow files.

## Changes made while building (2026-11-13)

Tasks 4–6 were written before a server existed. Where the result differs
from the task text below, this list wins:

- The `images` and `deploy` jobs and the backup check run only when the
  repository variable `DEPLOY_ENABLED` is `true`. Setting it is part of
  Task 5 Step 6.
- A CI job `infra` runs `shellcheck` and `infra/test/run.sh`.
- The deploy job sends files with `tar` over SSH instead of `scp`, and
  also sends `backup.sh` and `restore.sh`.
- `provision.sh` already installs the backup timer (Task 6's change to it).
- `backup.env` needs `RESTIC_REPOSITORY` and `RESTIC_PASSWORD`; the storage
  credentials' variable names are chosen in Task 6 Step 4.
- `restore.sh` needs an absolute path to an empty directory.

## What the developer provides

These cannot be done by the builder (accounts and payments). They can be done in parallel with Tasks 1–3.

1. **Server:** a Hetzner account and one server — type CX22 (2 vCPU, 4 GB, x86), Ubuntu 24.04, location Helsinki, with the developer's SSH public key added at creation. Give the builder the IP address in chat (it goes into a GitHub secret, not the repo).
2. **Domain:** a domain name, with an `A` record pointing at the server's IP. Tell the builder the name.
3. **Backup storage:** a Backblaze B2 account, one private bucket, and one application key limited to that bucket (free up to 10 GB). The developer types the key on the server themselves in Task 6; the builder never sees it.
4. **Invited emails:** the addresses allowed to sign up, starting with the developer's own.

## Review Focus

1. **A deploy whose new version does not start** (failed migration, bad config). Expected: the previous version comes back automatically and the workflow goes red. → Task 5, Step 7.
2. **The server reboots.** Expected: all three containers and the backup timer come back by themselves. → Task 7, Step 5.
3. **Sign-up by someone not invited**, including when the allowlist variable is missing altogether. Expected: refused; missing variable means nobody can sign up. → Task 1.
4. **Backups silently stop.** Expected: within a day a scheduled workflow fails and GitHub emails the owner. → Task 6, Step 6.
5. **The disk fills up with old images.** Expected: each deploy prunes images older than a week. → Task 5, Step 3.
6. **Uploaded media after a redeploy.** Expected: an image uploaded before a deploy is still there after it (the media directory is a volume, writable by the non-root app user). → Task 7, Step 4.
7. **HTTPS certificate cannot be issued** because DNS does not point at the server yet. Expected: checked with `dig` before the first deploy, not discovered afterwards. → Task 7, Step 1.

---

## File Structure

| File | Responsibility |
|---|---|
| `src/main/java/.../authentication/SignupPolicy.java` (create) | Decides whether an email may sign up |
| `src/main/java/.../config/SignupProperties.java` (create) | Binds `signup.allowed-emails` |
| `src/main/java/.../common/exception/SignupNotAllowedException.java` (create) | Mapped to 403 |
| `src/main/java/.../authentication/service/AuthenticationService.java` (modify) | Asks the policy before creating a user |
| `src/main/java/.../config/SecurityConfig.java` (modify) | Permits `/actuator/health` |
| `src/main/resources/application.properties` (modify) | `signup.allowed-emails`, actuator exposure |
| `build.gradle` (modify) | Actuator dependency; plain jar off |
| `infra/api.Dockerfile`, `infra/web.Dockerfile`, `.dockerignore` (create) | The two images |
| `infra/Caddyfile` (create) | HTTPS, static files, proxy rules |
| `infra/compose.yml` (create) | The three services and their volumes |
| `infra/env.example` (create) | Names of every variable the server needs, no values |
| `infra/provision.sh` (create) | One-time server setup |
| `infra/deploy.sh` (create) | Deploy one image tag, roll back if unhealthy |
| `infra/backup.sh`, `infra/restore.sh` (create) | Nightly backup; restore into a scratch database |
| `.github/workflows/ci.yml` (modify) | `images` and `deploy` jobs after the checks |
| `.github/workflows/backup-check.yml` (create) | Daily "is the last backup fresh?" |
| `docs/ARCHITECTURE.md`, `README.md`, `.claude/*` (modify) | Kept true |

---

### Task 1: Closed sign-up

**Files:**
- Create: `authentication/SignupPolicy.java`, `config/SignupProperties.java`, `common/exception/SignupNotAllowedException.java`
- Modify: `authentication/service/AuthenticationService.java`, `common/exception/GlobalExceptionHandler.java`, `application.properties`, `src/test/resources/application-test.properties`, `README.md`
- Test: `authentication/SignupPolicyTest.java` (new), `authentication/AuthenticationFlowTest.java` (extend)

**Interfaces:**
- Produces: property `signup.allowed-emails` (comma-separated; `*` means anyone), fed from env `SIGNUP_ALLOWED_EMAILS`. `SignupPolicy.isAllowed(String email): boolean`. HTTP 403 with body `Sign-up is by invitation only` for anyone not allowed.

- [ ] **Step 1: Write `SignupPolicyTest` (plain JUnit)**

Cases, each constructing `new SignupPolicy(properties)` with the given list:
- empty list → `isAllowed("a@example.com")` is `false`
- `["*"]` → `true` for any address
- `["a@example.com", "b@example.com"]` → `true` for both, `false` for `c@example.com`
- matching ignores case and surrounding spaces: list `[" A@Example.com "]` allows `a@example.com`
- `null` email → `false`

- [ ] **Step 2: Run it — expect a compile failure (classes missing)**

Run: `./gradlew test --tests '*SignupPolicyTest'`

- [ ] **Step 3: Implement `SignupProperties` (`@ConfigurationProperties(prefix = "signup")`, `List<String> allowedEmails`, default empty) and `SignupPolicy` (`@Component`)**

- [ ] **Step 4: Run it — expect PASS**

- [ ] **Step 5: Extend `AuthenticationFlowTest`**

The `test` profile gets `signup.allowed-emails=*` so the existing tests keep passing. Add a nested class (or a second test class with `@TestPropertySource(properties = "signup.allowed-emails=invited@example.com")`) with:
- `signup_whenEmailNotInvited_returns403AndCreatesNoUser` — POST sign-up for `stranger@example.com` → 403, body `Sign-up is by invitation only`, `userRepository.existsByEmail("stranger@example.com")` is `false`
- `signup_whenEmailInvited_returns201`
- `signup_whenNotInvited_andEmailAlreadyRegistered_stillReturns403` — the answer must not reveal whether the address exists

- [ ] **Step 6: Run — expect the first and third to FAIL (201 / 409 instead of 403)**

- [ ] **Step 7: Wire it**

`AuthenticationService.signUp` asks `SignupPolicy` first and throws `SignupNotAllowedException` before the duplicate check. `GlobalExceptionHandler` maps it to 403. `application.properties`: `signup.allowed-emails=${SIGNUP_ALLOWED_EMAILS:}` — a missing variable means closed.

- [ ] **Step 8: Run the whole suite — expect PASS**

Run: `./gradlew cleanTest test`

- [ ] **Step 9: Local run keeps working**

`README.md` run command gains `SIGNUP_ALLOWED_EMAILS='*'`. Start the backend with it and sign up through the UI; start it without and confirm sign-up shows "Sign-up is by invitation only" while log-in still works.

- [ ] **Step 10: Commit** — `auth: sign-up by invitation only`

---

### Task 2: Health endpoint

**Files:**
- Modify: `build.gradle`, `application.properties`, `config/SecurityConfig.java`
- Test: `config/HealthEndpointTest.java` (new; `@SpringBootTest` + `@AutoConfigureMockMvc` + `TestcontainersConfiguration`, like `AuthenticationFlowTest`)

**Interfaces:**
- Produces: `GET /actuator/health` → 200 `{"status":"UP"}` without a token. Nothing else under `/actuator` is reachable. Task 3's Caddyfile and Task 5's deploy script rely on this exact path.

- [ ] **Step 1: Write the test**

- `health_withoutToken_returns200AndStatusUp` — `jsonPath("$.status").value("UP")`, and the body has no `components` key (no details)
- `otherActuatorEndpoints_areNotExposed` — `GET /actuator/env` and `GET /actuator/beans` without a token are not 200
- `apiStillRequiresAToken` — `GET /api/activities` without a token → 401

- [ ] **Step 2: Run — expect FAIL (404 or 401 on `/actuator/health`)**

- [ ] **Step 3: Implement**

Add `org.springframework.boot:spring-boot-starter-actuator`. Properties: `management.endpoints.web.exposure.include=health`, `management.endpoint.health.show-details=never`. `SecurityConfig`: `.requestMatchers("/actuator/health").permitAll()` ahead of the `/api/**` rule; everything else stays as is.

- [ ] **Step 4: Run the whole suite — expect PASS**

- [ ] **Step 5: Commit** — `ops: health endpoint for deploy checks`

---

### Task 3: Images and a local dress rehearsal

**Files:**
- Create: `infra/api.Dockerfile`, `infra/web.Dockerfile`, `infra/Caddyfile`, `infra/compose.yml`, `infra/env.example`, `.dockerignore`
- Modify: `build.gradle` (`jar { enabled = false }` so `build/libs` holds only the runnable jar)

**Interfaces:**
- Consumes: `/actuator/health` (Task 2), `SIGNUP_ALLOWED_EMAILS` (Task 1).
- Produces: image names `ghcr.io/eugenlinde/activity-tracker-api` and `ghcr.io/eugenlinde/activity-tracker-web`, both tagged by `IMAGE_TAG`. Compose service names `postgres`, `api`, `web`. Named volumes `pgdata`, `media`, `caddy_data`, `caddy_config`. Variables in `infra/env.example`: `SITE_ADDRESS`, `IMAGE_TAG`, `POSTGRES_PASSWORD`, `JWT_SECRET`, `SIGNUP_ALLOWED_EMAILS`, `OPENROUTER_API_KEY`, `OPENROUTER_BASE_URL`, `OPENROUTER_MODEL`. The datasource URL and user, the media path and `SPRING_JPA_SHOW_SQL=false` are fixed in `infra/compose.yml`, which also hands `POSTGRES_PASSWORD` to the api as its datasource password.

- [ ] **Step 1: `infra/api.Dockerfile`**

Base `eclipse-temurin:26-jre-alpine`. Non-root user `app`. Copies `build/libs/*.jar` to `/app/app.jar` (the jar is built by Gradle beforehand, not inside Docker). Creates `/data/media` owned by `app`. `EXPOSE 8080`. Entry point `java -XX:MaxRAMPercentage=60 -jar /app/app.jar`. Build context is the repo root.

- [ ] **Step 2: `infra/web.Dockerfile` and `infra/Caddyfile`**

Base `caddy:2-alpine`; copies `frontend/dist` to `/srv` and the Caddyfile to `/etc/caddy/Caddyfile`. Caddyfile, one site block for `{$SITE_ADDRESS}`:
- `/api/*` and `/actuator/health` → `reverse_proxy api:8080`
- everything else → static files from `/srv` with `try_files {path} /index.html` (so a reload on `/shares` works)
- `encode zstd gzip`

With `SITE_ADDRESS` a real domain Caddy gets a certificate by itself; with `:80` it serves plain HTTP for the local rehearsal.

- [ ] **Step 3: `infra/compose.yml`**

- `postgres`: `postgres:17-alpine`, `POSTGRES_DB=tracker`, `POSTGRES_USER=tracker`, `POSTGRES_PASSWORD` from the env file, volume `pgdata`, `pg_isready` health check, no published ports.
- `api`: the api image, an explicit `environment:` block (only the variables it needs), `depends_on: postgres (healthy)`, volume `media:/data/media`, no published ports.
- `web`: the web image, ports `80:80`, `443:443`, `443:443/udp`, volumes `caddy_data:/data`, `caddy_config:/config`, `SITE_ADDRESS` from the env file, `depends_on: api`.
- all three `restart: unless-stopped`.

- [ ] **Step 4: `.dockerignore`** — everything except `build/libs`, `frontend/dist`, `infra/Caddyfile`.

- [ ] **Step 5: Build both images locally**

```bash
./gradlew bootJar && (cd frontend && yarn install --frozen-lockfile && yarn build)
docker build -f infra/api.Dockerfile -t ghcr.io/eugenlinde/activity-tracker-api:local .
docker build -f infra/web.Dockerfile -t ghcr.io/eugenlinde/activity-tracker-web:local .
```

Expected: both build; `ls build/libs` shows exactly one jar.

- [ ] **Step 6: Dress rehearsal on the laptop**

In a scratch directory outside the repo: copy `infra/compose.yml`, write a `.env` with `SITE_ADDRESS=:80`, `IMAGE_TAG=local`, generated throwaway secrets, `SIGNUP_ALLOWED_EMAILS=rehearsal@example.com`. The rehearsal's Postgres publishes no ports, so it does not clash with the dev database. `docker compose up -d`, then:

- `curl -s localhost/actuator/health` → `{"status":"UP"}`
- `curl -s -o /dev/null -w '%{http_code}' localhost/api/activities` → `401`
- `curl -s -o /dev/null -w '%{http_code}' localhost/shares` → `200` (SPA fallback)
- in a browser at `http://localhost`: sign up as the invited address works, a different address is refused, create a workout, upload an image, add weight and steps
- `docker compose exec api id -u` is not `0`
- `docker compose restart api`, then the uploaded image still opens

Then `docker compose down -v` and delete the scratch directory.

- [ ] **Step 7: Commit** — `infra: container images, Caddy, compose`

---

### Task 4: Prepare the server

Needs items 1 and 2 from "What the developer provides", and the developer's go-ahead before the first SSH command.

**Files:**
- Create: `infra/provision.sh`

**Interfaces:**
- Produces on the server: user `deploy` (in the `docker` group, key-only SSH), directory `/opt/tracker` owned by `deploy`, file `/opt/tracker/.env` (mode 600) with every variable from `infra/env.example` filled in, firewall open on 22, 80, 443 only.

- [ ] **Step 1: Write `infra/provision.sh`** (idempotent — safe to run twice)

Run as root on a fresh Ubuntu 24.04. It:
- installs Docker Engine + compose plugin from Docker's apt repository, `ufw`, `unattended-upgrades`, `restic`
- creates user `deploy`, copies root's `authorized_keys` to it, adds it to `docker`
- sets `PasswordAuthentication no` and `PermitRootLogin prohibit-password` in sshd, reloads it
- `ufw allow 22,80,443/tcp`, `ufw allow 443/udp`, `ufw --force enable`
- creates `/opt/tracker` and, only if `/opt/tracker/.env` does not exist, writes it with: `POSTGRES_PASSWORD` (`openssl rand -hex 24`) and `JWT_SECRET` (`openssl rand -base64 32`), `IMAGE_TAG=none`, placeholders for OpenRouter, and `SITE_ADDRESS` / `SIGNUP_ALLOWED_EMAILS` taken from the script's two arguments
- prints what it did, never a secret value

- [ ] **Step 2: Check the script before it touches anything**

Run: `bash -n infra/provision.sh` and `shellcheck infra/provision.sh` (install shellcheck with brew if missing). Expected: no errors.

- [ ] **Step 3: Ask the developer, then run it**

State exactly what will happen on which server. After the go-ahead:

```bash
scp infra/provision.sh root@<ip>:/root/ && ssh root@<ip> 'bash /root/provision.sh <domain> "<emails>"'
```

- [ ] **Step 4: Verify**

- `ssh deploy@<ip> 'docker run --rm hello-world'` works
- `ssh deploy@<ip> 'stat -c "%a %U" /opt/tracker/.env'` → `600 deploy`
- `ssh root@<ip> 'ufw status'` shows only 22, 80, 443
- `ssh -o PreferredAuthentications=password deploy@<ip>` is refused
- `ssh deploy@<ip> 'grep -c "=" /opt/tracker/.env'` equals the number of variables in `infra/env.example` (names only — do not print values)

- [ ] **Step 5: Commit** — `infra: one-time server provisioning script`

---

### Task 5: Deploy on merge to `main`

**Files:**
- Create: `infra/deploy.sh`
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: checks `backend`, `frontend`; images and compose file from Task 3; server layout from Task 4.
- Produces: jobs `images` and `deploy`. GitHub environment `production` (restricted to branch `main`) holding secrets `DEPLOY_HOST`, `DEPLOY_SSH_KEY` and variable `SITE_ADDRESS`. On the server: `/opt/tracker/deploy.sh <tag>`, `/opt/tracker/compose.yml`, `/opt/tracker/predeploy/` (last 5 database dumps).

- [ ] **Step 1: A deploy key that the builder never reads**

On the developer's machine, with their go-ahead:

```bash
ssh-keygen -t ed25519 -N '' -C github-deploy -f "$TMPDIR/tracker_deploy" -q
gh api -X PUT repos/eugenlinde/activity-tracker/environments/production >/dev/null
gh secret set DEPLOY_SSH_KEY --env production < "$TMPDIR/tracker_deploy"
ssh deploy@<ip> 'cat >> ~/.ssh/authorized_keys' < "$TMPDIR/tracker_deploy.pub"
rm -P "$TMPDIR/tracker_deploy" "$TMPDIR/tracker_deploy.pub"
```

Then `gh secret set DEPLOY_HOST --env production` (value typed by the developer or piped from the IP given in chat), `gh variable set SITE_ADDRESS --env production`, and restrict the environment to `main` (Settings → Environments → production → Deployment branches).

- [ ] **Step 2: Make the two GHCR packages public after the first push**

The images contain only what the public repo contains, so the server can pull without credentials. A new package starts private, so the very first `deploy` run is expected to fail at `docker compose pull`. After the first `images` run: set both packages to public (Package settings → Change visibility → Public, done once per image by the developer or via `gh api` with their go-ahead), then re-run the `deploy` job. Both Dockerfiles carry the label `org.opencontainers.image.source=https://github.com/eugenlinde/activity-tracker` so the packages show up under the repo.

- [ ] **Step 3: Write `infra/deploy.sh`**

Usage `deploy.sh <tag>`, run as `deploy` in `/opt/tracker`:
1. read the current `IMAGE_TAG` from `.env` as `previous`
2. if the `postgres` container is running: `pg_dump` (via `docker compose exec`) gzipped into `predeploy/<timestamp>-<previous>.sql.gz`; keep the newest 5
3. set `IMAGE_TAG=<tag>` in `.env`; `docker compose pull`; `docker compose up -d --remove-orphans`
4. poll `https://$SITE_ADDRESS/actuator/health` every 5 s for up to 120 s until it contains `"status":"UP"`
5. healthy → `docker image prune -af --filter until=168h`; exit 0
6. not healthy → print the last 50 lines of the `api` log, set `IMAGE_TAG` back to `previous`, `docker compose up -d`, exit 1 (when `previous` is `none` there is nothing to roll back to: just exit 1)

- [ ] **Step 4: Check the script** — `bash -n` and `shellcheck`, no errors.

- [ ] **Step 5: Extend `ci.yml`**

- job `images`: `needs: [backend, frontend]`, `if: github.event_name == 'push' && github.ref == 'refs/heads/main'`, `permissions: { contents: read, packages: write }`. Builds the jar and the frontend, logs in to GHCR with `GITHUB_TOKEN`, builds and pushes both images tagged `${{ github.sha }}`.
- job `deploy`: `needs: images`, same `if`, `environment: production`, `concurrency: { group: deploy-production, cancel-in-progress: false }`. Writes the key to a file with mode 600, adds the host to `known_hosts` with `ssh-keyscan`, copies `infra/compose.yml` and `infra/deploy.sh` to `/opt/tracker/`, runs `./deploy.sh ${{ github.sha }}`.
- `workflow_dispatch` with an optional `tag` input, so an older tag can be redeployed by hand (manual rollback).
- No secret is echoed; no step runs on `pull_request`.

- [ ] **Step 6: First real deploy**

Open the PR; the developer merges it. Expected in the run on `main`: `backend`, `frontend`, `images` green; `deploy` green after Step 2's one-time visibility change and a re-run; `curl -s https://<domain>/actuator/health` → `{"status":"UP"}`.

- [ ] **Step 7: Prove the rollback**

On a branch, make the app unable to start (for example a changeset that references a table that does not exist). The developer merges it knowingly as a drill. Expected: `deploy` goes red, its log shows the api error and "rolled back to <previous sha>", and the site still answers `UP` on the previous version. Then revert the commit through a PR.

- [ ] **Step 8: Commit** — `ci: build images and deploy to production after green checks`

---

### Task 6: Backups and a proven restore

Needs item 3 from "What the developer provides".

**Files:**
- Create: `infra/backup.sh`, `infra/restore.sh`, `.github/workflows/backup-check.yml`
- Modify: `infra/provision.sh` (installs the timer)

**Interfaces:**
- Produces on the server: `/opt/tracker/backup.env` (mode 600: `RESTIC_REPOSITORY`, `RESTIC_PASSWORD`, `B2_ACCOUNT_ID`, `B2_ACCOUNT_KEY`), systemd timer `tracker-backup.timer` (daily 03:30), marker file `/opt/tracker/last-backup-ok` (touched after a successful run).

- [ ] **Step 1: `infra/backup.sh`**

1. `pg_dump` the database into `/opt/tracker/backup-staging/db.sql` 
2. `restic backup` of that file plus the media volume's directory (`docker volume inspect` gives the path), tagged `nightly`
3. `restic forget --keep-daily 7 --keep-weekly 4 --keep-monthly 6 --prune`
4. `restic check` once a week (Sundays)
5. only if every step succeeded: `touch /opt/tracker/last-backup-ok`

- [ ] **Step 2: `infra/restore.sh`**

Usage `restore.sh <snapshot|latest> <target-dir>`. Restores the snapshot into `<target-dir>`, starts a throwaway `postgres:17-alpine` container on a private network, loads `db.sql` into it, prints the row count of every table and the number of restored media files, and leaves the container running under the name `tracker-restore-check` for inspection. It never touches the live database or the live media volume.

- [ ] **Step 3: Check both scripts** — `bash -n` and `shellcheck`.

- [ ] **Step 4: The developer enters the storage credentials on the server**

The developer runs, in their own SSH session:

```bash
sudo install -m 600 -o root /dev/null /opt/tracker/backup.env && sudoedit /opt/tracker/backup.env
```

and fills in the four values (`RESTIC_PASSWORD` from `openssl rand -base64 32`). **The developer saves `RESTIC_PASSWORD` in their password manager** — without it the backups cannot be read by anyone. Then `restic init` once.

- [ ] **Step 5: Run a backup, then prove a restore**

`sudo systemctl start tracker-backup.service` → `restic snapshots` lists one snapshot; `last-backup-ok` exists. Then, with real data present (after Task 7 Step 3): `restore.sh latest /tmp/restore-check` and compare its row counts with the live database's (`select count(*)` per table) and the media file count. They must match. Remove the scratch container and directory afterwards. Write the date and the result into `docs/ARCHITECTURE.md`.

- [ ] **Step 6: `backup-check.yml`**

Scheduled daily at 06:00 UTC and on `workflow_dispatch`, `environment: production`. SSHes in and fails unless `/opt/tracker/last-backup-ok` is younger than 26 hours. A failed scheduled workflow makes GitHub email the repo owner. Prove it: run it by hand once green, then `touch -d '2 days ago'` the marker and run it again → red; restore the marker.

- [ ] **Step 7: Commit** — `infra: nightly backups, restore script, freshness check`

---

### Task 7: Go live

- [ ] **Step 1: DNS before certificates**

Run: `dig +short <domain>` → the server's IP. If not, wait; do not deploy.

- [ ] **Step 2: First deploy** (Task 5 Step 6 if not done yet). In a browser: `https://<domain>` shows the login page with a valid certificate; `http://` redirects to `https://`.

- [ ] **Step 3: The developer creates their account** through Sign up with their invited address; a non-invited address is refused. They log a workout, a weight and today's steps — the first real data.

- [ ] **Step 4: Media survives a deploy**

Upload an image to a workout, trigger a redeploy of the same tag (`workflow_dispatch`), open the image again.

- [ ] **Step 5: Reboot drill**

With the developer's go-ahead: `ssh root@<ip> reboot`. After two minutes: the site answers `UP`, `docker compose ps` shows three services running, `systemctl is-enabled tracker-backup.timer` → `enabled`.

- [ ] **Step 6: Phone check**

The developer opens the site on their phone: log in, add weight, add steps, open a workout.

- [ ] **Step 7: Docs**

`docs/ARCHITECTURE.md`: path of a request (browser → Caddy → api → Postgres), path of a change including `images` and `deploy`, where each secret lives and who can read it, backups and how to restore (the exact command), how to roll back, how to stop everything (`docker compose down`), what it costs per month with real numbers, known gaps (no staging, no error tracking, no login rate limiting, JWT in `localStorage`). `README.md`: status and a link to the live site if the developer wants it public. `.claude/ROADMAP.md`: F2 items ticked; the explain-back goes to "Understanding debt" unless the developer wants it now. `.claude/DECISIONS.md` and `NOTES.md`: calls made and drill results.

- [ ] **Step 8: Commit** — `docs: architecture after first deploy`

---

## Not in this plan

- Staging (F3), agent loop (F4), error tracking and uptime alerts (F5).
- Rate limiting on login, refresh tokens, moving the JWT out of `localStorage`.
- Inviting people from the UI — the allowlist is edited in `/opt/tracker/.env` on the server and applied with `docker compose up -d`.
- Pinning the Testcontainers image and making lint warnings fail CI (backlog).
