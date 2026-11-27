# Session notes

Append-only log, public from the pivot of 2026-11-12. Earlier session
notes (the learning phase) are kept privately by the developer.

## 2026-11-10 — Talk review: "software factory" (Zach Lloyd, Warp)
Developer asked how the factory approach (triage → spec → implement →
review → verify → monitor, plus skill-improvement loops and a data plane)
maps onto this project. Nothing decided yet. Proposals given:
- Already in place: specs (SPEC.md step plans), human review gates, a data
  plane (the four `.claude/` files), one manual skill loop (annotation rule
  added to CLAUDE.md 2026-11-05).
- Tension: the talk's "engineers write no code" contradicts the learning
  goal. Proposed an explicit line: learning zone (Java/Spring core, dev
  writes) vs. factory zone (frontend, tests, docs — reviewer already writes
  these on request). Would go into DECISIONS + CLAUDE.md if accepted.
- Gaps: verification step is manual and keeps being skipped (9 NOTES
  entries say not checked in browser / not curled / uncommitted) → project
  `/verify` skill (gradle test, boot against docker-compose Postgres, run
  `http/*.http`, browser check of frontend). Session-end `/retro` loop that
  turns reviewer corrections and repeated findings into CLAUDE.md rules.
- Data plane hygiene: `.claude/` docs ~3200 lines / ~200 KB read at every
  session start; archive closed phases, keep a short current-state header.
  `.claude/` is untracked in git.
- Measure: review rounds per step (client took v1–v4), suite size, LLM
  token cost from `ChatCompletionResponse.usage` once import runs.
- Not worth it solo: triage agent, cloud sandboxes, control plane, parallel
  implementation agents. CI + agent PR review + monitoring belong in Phase 7.

## 2026-11-12 — Pivot to AI factory PoC
- Developer decided the full pivot (see DECISIONS). Brainstormed scope:
  success picture, budget, agent runtime, tools, anti-goal.
- Wrote `docs/superpowers/specs/2026-11-12-ai-factory-pivot-design.md`; a
  builder-check subagent raised 4 questions (unattended runs, product
  scope, open signup, GitHub plan) — 2 answered by the developer, 2 settled
  as builder's calls. Developer approved: "plaan on hea".
- Developer shared the frontend prototype (8 artboards: dashboard,
  workouts, workout detail, aerobic, diet, shares, import, templates).
  Copied to `docs/design/prototype/`; the developer's email in the sample
  code was replaced with a placeholder in the copy. Prototype needs
  entities that do not exist: weight, steps, diet, comments, invites,
  templates.
- Rewrote `CLAUDE.md` (builder + explainer), `.claude/ROADMAP.md` (F0–F6 +
  backlog), added `docs/ARCHITECTURE.md` skeleton.
- State of the repo: no git remote, `gh` is logged in. Nothing committed
  for the pivot yet.
- Open: developer to check the Claude Code subscription; which GitHub
  account owns the repo; Garmin developer-program application; whether
  the prototype really overrides read-only sharing and CSV-only import.

## 2026-11-12 — F1 executed (repo and CI)
- Task 1: ignore rules, history secret scan (only the JWT test secret),
  `master` → `main`, pivot docs committed. Local: 115 tests
  green, frontend lint (3 old warnings) and build green.
- Task 2: private repo `eugenlinde/activity-tracker` created and pushed
  after the developer's ok.
- Task 3: `.github/workflows/ci.yml` (actions at current majors: checkout
  v7, setup-java v6, setup-node v7, upload-artifact v7, gradle/actions v6).
  PR #1: backend 1m43s, frontend 24s, both green. Negative controls: PR #2
  failing test → backend red, "116 tests completed, 1 failed"; PR #3 type
  error → frontend red, TS2322. Both closed, branches deleted.
- Task 4: branch protection refused (403, needs Pro or public). Open
  decision for the developer.
- Task 5: ARCHITECTURE.md has "Path of a change". Explain-back not done
  yet; PR #1 not merged yet; F1 stays open until both.
- Open for the developer: merge PR #1; choose how `main` is protected;
  Claude Code subscription check; Garmin developer-program application.
- Correction: frontend lint has 5 warnings, not 3 (3 `set-state-in-effect`,
  2 `only-export-components`).
- F1 independent review (fresh subagent), no Critical. Fixed: CI
  concurrency no longer cancels runs on `main`; Gradle cache set to
  `cache-provider: basic` (the v6 default is a commercial caching service,
  free preview for private repos). Docs made truthful: lint is mostly
  advisory (only `rules-of-hooks` is an error), test report artifact exists
  only when tests fail, tests run on `postgres:latest`. Deferred to
  backlog: `--deny-warnings` after fixing 5 warnings; pin Testcontainers
  image.

## 2026-11-12 — Open source direction (not executed yet)
- Developer wants the repo public: AI factory PoC + portfolio/demo project.
  Asked what should be hidden. Resolves the `main` protection question
  (public repo → branch protection available) once done.
- Pre-publication audit (read-only) found: a non-public email as author
  on all commits; wording about the agent subscription in 6 files; real-looking sample
  people and email addresses in the prototype copy; `http/fixtures/sample.jpg`
  is a third-party cartoon character image; no LICENSE, no root README;
  pre-pivot NOTES/SPEC name a personal file.
- Proposed: rewrite history to the GitHub noreply email while the repo is
  still private, neutral wording for the agent subscription, fictional
  sample data, own fixture image, MIT licence + README, keep
  ROADMAP/SPEC/DECISIONS/ARCHITECTURE public, pre-pivot session notes out
  of the public repo.
- Design consequences for F4/F5: agents react only to the owner; error
  reports must not copy user data into public issues; fork PRs get no
  secrets and no staging deploy.
- Waiting for the developer's go-ahead. Not committed.

## 2026-11-12 — Pre-publication clean-up (local, done)
- Developer approved the clean-up. Backup of the old history kept locally
  outside git tracking. Pre-pivot notes and old roadmap moved to the
  git-ignored `.claude/private/`.
- History rewritten locally: 29 pre-pivot commits + pivot commit + CI
  commit, all with the noreply address; old fixture image gone from
  history. Verified: no sensitive strings in `git log -p main ci`; tree
  identical to the pre-rewrite final tree; 115 tests green; frontend lint
  and build green.
- Not done: the GitHub repo still holds the old history. Force-pushing is
  not enough — pull-request refs (#1, and the closed probes #2 and #3)
  keep the old commits reachable forever. The repo has to be deleted and
  re-created; deletion is the developer's to do.
- Then: push clean `main` and `ci`, reopen the CI PR, developer merges,
  make public (separate ok), enable branch protection, explain-back.

## 2026-11-12 — Repo public, `main` protected
- Old repo deleted by the developer; re-created private with the clean
  history; combined PR #1 (pivot + CI + clean-up) merged by the developer;
  CI on `main` green.
- With the developer's ok: repo made public, branch protection enabled.
- Probes: direct push to `main` rejected (GH006). PR #2 with a failing
  test: `backend` red, GitHub `mergeStateStatus=BLOCKED`; closed, branch
  deleted. An actual merge attempt on the probe was not made — the
  session's own permission guard refused it, and the BLOCKED state is the
  evidence instead.
- Left for F1: explain-back. Open for the developer: Claude Code
  subscription check for unattended runs; Garmin developer programme.

## 2026-11-13 — F1 closed without explain-back
- PR #3 merged by the developer. Local `main` in sync.
- Developer declined the explain-back for now ("focus on the MVP"). Logged
  in DECISIONS; F1 checkpoint moved to "Understanding debt" in ROADMAP.
- Doc changes sit on local branch `mvp-planning` (not pushed); they ride
  along with the next PR, since `main` only takes PRs now.
- Next: agree what the MVP contains and in which order.

## 2026-11-13 — Issue #4: app shell and look (Ant Design)
- Issues #4, #5, #6 created with short specs.
- Rewrote the frontend presentation on Ant Design 6: theme, sidebar with
  mobile burger, login, workouts (rows that expand; create/edit in a
  dialog), shares, shared log. API layer and logic unchanged.
- Verified in a browser against a local backend, desktop and 375 px: sign
  up, log in, create a workout with an exercise and two sets, edit it,
  view and delete an image, share with a second user, open the shared log
  as that user (read-only), revoke, delete the workout, sign out. No
  console errors; no horizontal scroll at 375 px.
- Not verified through the UI: choosing a file in the image picker (the
  image was uploaded through the API; display and delete were checked in
  the UI).
- `yarn lint`: the same 5 old warnings, none new. `yarn build` passes.
- Two local test users (`ui-check-1113a/b@example.com`) remain in the dev
  database.
- `.claude/launch.json` (dev-server config for the session's browser
  pane) is git-ignored.
- Developer hit "Request failed with 401" on `/activities` while trying
  the branch. Cause: a token left in the browser from an earlier backend
  run; the local run command generates a new `JWT_SECRET` on every start,
  so old tokens stop verifying. The app cleared the token but stayed on
  the page showing the error (old debt: "user silently logged out on
  first 401"). Fixed: a 401 now signs the user out in the UI and
  `RequireAuth` sends them to the login page. Checked in a browser: stale
  token → `/login`; wrong password on login still shows the error.

## 2026-11-13 — Issue #5: body weight
- Tests first (35 new): repository scoping and unique constraint on real
  Postgres, service rules with a fixed clock, controller validation and
  401/404/400. Two mutations (dropping the future-date check, dropping the
  one-decimal rule) each turned a test red. Whole suite: 150 green.
- Backend: `weight_entries` changeset, entity, repository, service,
  controller, two exceptions mapped in the handler, `Clock` bean.
- Frontend: Dashboard page at `/` (landing page, first nav item), weight
  card with latest value, change since first reading, 12-week line, empty
  state, add/replace/delete dialog.
- Checked against a real backend and database: API (seed of 11 readings,
  future date → 400, two decimals → 400, delete missing → 404) and UI
  (chart, add → value and chart update, replace note, delete, empty state
  for a second user who sees none of the first user's data, 375 px with no
  horizontal scroll, no console errors).
- 11 seeded readings remain for `ui-check-1113a@example.com` in the dev
  database.
- PR is stacked on `feat/app-shell` (PR #7) because the Dashboard builds
  on the new shell.

## 2026-11-13 — Issue #6: daily steps
- Tests first: 31 for steps, 4 for the shared date policy; weight service
  test adapted to the policy. Mutations (dropping the max, dropping the
  date check) turned tests red. A probe against the real API showed
  `9870.5` being saved as `9870`; added a failing test, then fixed. Whole
  suite: 186 green.
- Frontend: steps card (bars, goal line, 2/8-week toggle, weekly
  averages, legend), shared add/replace/delete dialog, sticky sidebar,
  smaller stat numbers on phone width.
- Checked against a real backend and database: API (41 seeded days,
  over max → 400, negative → 400, future → 400, delete missing → 404) and
  UI at desktop and 375 px (both ranges, add → average changes by the
  expected amount, delete, weight card still works with the shared
  dialog, no horizontal scroll).
- Seeded steps and weight readings remain for the test user in the dev
  database.
- PR stacked on `feat/weight` (PR #8).

## 2026-11-13 — F2 plan written
- State of the PRs: #7 merged to `main`; #9 was merged into `feat/weight`,
  so PR #8 (base `main`, green) now carries weight and steps and waits for
  the developer.
- Local test logins for the dev database are in the git-ignored
  `.claude/private/local-test-users.md`.
- F2 plan: closed sign-up (allowlist from an env var, missing = closed),
  Actuator health, two images (api, web with Caddy and the built
  frontend), Compose on one VPS, deploy job after green checks with a
  pre-deploy dump and automatic rollback, restic backups to object storage
  with a proven restore and a daily freshness check.
- Builder's calls in the plan: Caddy serves the frontend (no Spring
  change for SPA routing); GHCR packages public so the server pulls
  without credentials; x86 server so images need no cross-build;
  Backblaze B2 free tier for backups; GitHub environment `production`
  restricted to `main` for deploy secrets.
- Needs from the developer: server + IP, domain + DNS, B2 bucket + key,
  invited emails. Tasks 1–3 need none of these.
- Plan sits on local branch `f2-deploy` (based on `feat/weight`), not
  pushed.

## 2026-11-13 — F2 tasks 1–3 done (no server needed)
- Task 1, closed sign-up: tests first (8 policy + 4 flow), red with a stub
  policy, then green. Task 2, health endpoint: 7 tests, red then green.
  Whole suite 205 green.
- Task 3: `infra/` (two Dockerfiles, Caddyfile, compose, env example),
  `.dockerignore`. Images built: api 435 MB, web 89 MB.
- Dress rehearsal on the laptop from `infra/compose.yml` with throwaway
  secrets: health `UP` through Caddy; `/api/activities` 401 without a
  token; `/shares` served by the SPA fallback; `/actuator/env` returns the
  web app's HTML, not the actuator; uninvited sign-up refused in API and
  UI, invited one works; workout, image, weight, steps saved; image still
  served after `up -d --force-recreate api`; api uid 100; no SQL and no
  errors in the api log. Stack removed with `down -v`, scratch directory
  and local image tags deleted.
- Local development now needs `SIGNUP_ALLOWED_EMAILS='*'` in the run
  command to create new accounts; logging in to existing ones is
  unaffected.
- Remaining F2 tasks (4–7) need the developer's server, domain, backup
  bucket and invited emails.

## 2026-11-13 — PR housekeeping and F2 server scripts
- Merging PR #8 from the app returned 502. The PR itself was healthy;
  its branch was one commit behind `main`. After "update branch" and a
  green CI the developer merged it. Whether the 502 came from GitHub or
  from the stale branch was not established.
- Merging #8 deleted `feat/weight`, the base of PR #10, and GitHub closed
  #10. A closed PR cannot be retargeted, so PR #11 (`f2-deploy` → `main`,
  same content) replaced it. Lesson: do not stack a PR on a branch that
  will be deleted on merge; target `main` and say what it depends on.
- Wrote ahead of the server, on branch `f2-server-scripts` (on top of
  `f2-deploy`): `infra/provision.sh`, `deploy.sh`, `backup.sh`,
  `restore.sh`, `infra/test/` (24 tests with stand-ins), CI jobs `infra`,
  `images`, `deploy`, and `.github/workflows/backup-check.yml`.
- Verified: shellcheck clean; 24/24 script tests; 12 deliberate breakages
  each caught; `restore.sh` against a real Postgres container with a real
  dump (row counts 2 / 7 / 0 as expected, twice); package names in an
  Ubuntu 24.04 container; both workflow files parse as YAML.
- Not verified: anything on a real server, and the workflows' behaviour
  on GitHub beyond YAML syntax (the `if:` conditions of `images` and
  `deploy` are untested until `DEPLOY_ENABLED` is set).
- Still needed from the developer: server + IP, domain + DNS, backup
  bucket + key, invited emails.
- Independent review of the scripts before push: 8 findings (failed
  `up` skipped the rollback; backend log would have gone to the public
  workflow log; `compose.yml` overwritten before validation; SSH drop
  could kill a deploy mid-way; concurrency could delay a manual rollback;
  three smaller ones). All fixed; tests written first and seen failing
  for the testable ones. Script tests now 31, shellcheck clean.

## 2026-11-16 — Server bought, PRs #11 and #12 merged

- PR #11 and #12 merged by the developer; `main` carries the server
  scripts, deploys still off (`DEPLOY_ENABLED` unset, no `production`
  environment yet).
- Developer created a Hetzner CPX22 with Ubuntu 26.04 and bought a domain.
  Cost is over budget by the developer's decision — see DECISIONS.
- Measured memory of the full stack locally: about 565 MB.
- Ubuntu 26.04 checked: Docker apt repo has `resolute`; all packages from
  `provision.sh` have candidates there.
- Still needed from the developer: server IPv4, DNS A record for the
  tracker subdomain, backup bucket + its endpoint, invited emails.
- Not done: nothing has run on the server yet.

## 2026-11-16 — Server provisioned (F2 task 4)

- With the developer's go-ahead: `provision.sh` copied to the server and
  run as root, exit 0. Invited list holds one address (the developer's);
  more are added later in `/opt/tracker/.env`.
- Verified on the server afterwards: Docker 29.10 + compose 5.7, restic
  0.18; ufw active (22, 80, 443 only); password login refused; `root`
  and `deploy` log in with the key; `deploy` in docker group, no sudo;
  `.env` mode 600 with eight variables (secret values not read);
  backup timer enabled, next run 03:30 UTC; unattended upgrades on.
- DNS: the tracker subdomain resolves to the server from 1.1.1.1 and
  8.8.8.8.
- Backup storage known: one private Backblaze bucket, S3-compatible
  endpoint in EU Central. The keys stay with the developer.
- Expected until task 6: the nightly backup unit fails (no `backup.sh`,
  no `backup.env` yet).
- Next: task 5 — deploy key, `production` environment with
  `DEPLOY_HOST` / `DEPLOY_SSH_KEY`, `SITE_ADDRESS` variable, GHCR
  packages public, then the developer turns `DEPLOY_ENABLED` on.
- Docs for this are on branch `f2-go-live`, not committed yet.

## 2026-11-16 — First deploy prepared (F2 task 5, step 1)

- With the developer's go-ahead: GitHub environment `production` created,
  deployment branches limited to `main`; secrets `DEPLOY_HOST` and
  `DEPLOY_SSH_KEY` set in it.
- Deploy key: ed25519 pair made on the laptop, public half appended to
  `deploy`'s `authorized_keys`, private half stored in the secret, local
  copy deleted. Checked: the key logs in as `deploy`; as `root` it is
  refused.
- No `SITE_ADDRESS` variable on GitHub: the workflows do not read one,
  `deploy.sh` takes the address from the server's `.env`.
- Not done: `DEPLOY_ENABLED` is still unset — the developer's switch. The
  first run is expected to fail at `docker compose pull` until the two
  GHCR packages are made public.

## 2026-11-16 — First deploy: the site is live

- The developer set `DEPLOY_ENABLED=true` and merged PR #13. The run on
  `main` went green end to end (`images` 1 min, `deploy` 40 s).
- The builder expected `deploy` to fail at the image pull (new GHCR
  packages private). It did not: the packages came out public and the
  server pulled them without a login. No visibility change was needed.
- Checked from outside: health `UP` over HTTPS, valid Let's Encrypt
  certificate, HTTP → HTTPS redirect, front page loads, `/api` → 401
  without a token, other `/actuator` paths are not forwarded, sign-up
  with a non-invited address → 403.
- Memory on the server right after start: backend about 960 MB — nearly
  double the 500 MB measured on the laptop under a 1.9 GB limit. The
  earlier "2 GB is enough" claim was thinner than stated; the 4 GB server
  the developer chose has the room.
- Open: backups (task 6) — the backup check will be red every morning
  until then; rollback drill; reboot drill; the developer's first
  account. The pull's progress output makes the public deploy log long
  and noisy (cosmetic; fix with a quiet pull).

## 2026-11-16 — Backups working (F2 task 6)

- The developer created `/opt/tracker/backup.env` on the server and ran
  `restic init` (first try failed with "Access Denied": wrong key id
  typed; fixed by the developer). Storage is reached through Backblaze's
  S3-compatible interface.
- Builder ran the backup unit: green, one snapshot, marker touched. Ran
  `restore.sh latest`: restored copy's row counts equal the live
  database's (no user data yet). Throwaway container, network and
  directory removed.
- `backup-check.yml` by hand: green with a fresh marker; red after the
  marker was back-dated two days (the developer got GitHub's failure
  e-mail for that run — it was the drill); backup run again, marker
  fresh.
- Still to do in F2: the developer's first account and first real data,
  then the restore drill again; rollback drill; reboot drill; watch the
  timer fire by itself tonight (03:30 UTC).

## 2026-11-16 — Second deploy, first account, restore with a user row

- PR #14 merged by the developer → second deploy green. Postgres stayed
  up; `api` and `web` recreated; first pre-deploy dump written and
  readable (8 tables, 1 user row).
- The developer created their account on the live site.
- Backup + `restore.sh latest` again: restored row counts identical to
  live (users 1, everything else empty). Scratch container, network and
  files removed.
- Same-day snapshots are thinned to one by the retention rule
  (keep 7 daily) — expected.
- Observation: every merge to `main` deploys, including docs-only ones.
  Harmless (about 20 s of `api` restart) but worth a path filter later.
- Left for F2: rollback drill, reboot drill, the timer firing by itself
  tonight, a restore once workouts and media exist. These notes are on
  branch `f2-drills`, uncommitted, to go with the rollback-drill change.

## 2026-11-16 — Rollback drill passed

- PR #15 (one line: unknown JVM option in `infra/api.Dockerfile`) merged
  by the developer as a drill. Checks and `images` green, `deploy` red
  after 3 min 44 s: dump taken, new version never healthy, log saved on
  the server (not in the public workflow log), previous tag and compose
  file restored, health `UP`.
- Verified on the server: `IMAGE_TAG` back to the previous commit, three
  containers running, 1 user in the database, two pre-deploy dumps kept.
- This change removes the broken option; `infra/api.Dockerfile` is
  byte-identical to the last good version.
- Left for F2: reboot drill, the nightly timer firing by itself, a
  restore once workouts and media exist.

## 2026-11-16 — Review loop designed; part 1 (gate) built

- Developer asked for automatic review of factory PRs: only major and
  critical findings, 6+ critical → scrap, 3 rounds max, scrap → analysis →
  corrected spec → rebuild. Developer chose: rebuild starts by itself
  (option B), rules enforced by a tested script (approach 1). Design
  approved: `docs/superpowers/specs/2026-11-16-review-loop-design.md`.
- Built on branch `worktree-f4-review-gate` (not pushed): `factory/`
  scripts (gate, state, apply, run-gate, labels), workflow
  `factory-gate.yml`, tests in the CI `infra` job (62 cases, fake `gh`).
- Not done: the drill on a real throwaway PR (needs the developer's ok,
  `FACTORY_ENABLED=true`, labels created); part 2 (agents) waits for the
  plan A/B check.

## 2026-11-16 — Revert deployed, reboot drill passed

- PR #16 merged by the developer; deploy green, the server runs the
  current `main`.
- Reboot drill with the developer's go-ahead: 29 s of downtime; Docker,
  the three containers, ufw, the backup timer and the SSH settings all
  came back by themselves; no failed units; data intact.

## 2026-11-17 — Night check, import spec, import built

- Night check: the backup timer fired by itself at 03:30 UTC and the
  backup succeeded. The scheduled 06:00 UTC backup check never ran —
  GitHub lists no scheduled run for the repo. Cause not established.
  The cron moves to 06:17 in this change; watch for the first scheduled
  run.
- Import: four scope questions answered by the developer, spec written
  as issue #18 and approved. Built on branch `feat/csv-import` in its
  own worktree (`.claude/worktrees/csv-import`), because another session
  shares the main checkout.
- Built test-first: CSV reading, date and number reading, fingerprint,
  model layer (two prompts, two JSON schemas, one retry), preview and
  confirm services, endpoints, Import page. Frontend builds; lint shows only the five
  old warnings.
- End-to-end rehearsal on the laptop with a throwaway Postgres and a
  stand-in for OpenRouter, through the browser: worked, including a
  second import of the same files. It turned up one real gap — the log
  did not say why the model could not be reached — now fixed.
- Independent review of the branch: no critical finding; eight
  important ones, the code ones fixed test-first (17 new tests seen
  failing, then passing). Suite now 323, importer 118. Details in
  DECISIONS.
- Not verified: anything against the real model. Needed from the
  developer: sample files in `.claude/private/import-samples/`, a real
  OpenRouter key with a spending limit (locally and on the server).
- Housekeeping: the main checkout still holds this session's uncommitted
  reboot-drill notes on branch `f2-reboot-drill`; they are superseded by
  this change and can be discarded there.

## 2026-11-18 — Correction: the backup check did run, nine hours late

- The entry above says the scheduled backup check "never ran" on
  2026-11-17. Wrong: it was looked up before the run existed. GitHub
  started it at 15:19 UTC (asked for 06:00) and it passed.
- On 2026-11-18 no scheduled run by 09:30 UTC.
- The move to 06:17 stays (GitHub's own advice, costs nothing), but
  there is no evidence yet that it shortens the delay. `ARCHITECTURE.md`
  and the text of PR #20 were corrected.
- Lesson: "no run listed" a few hours after the scheduled time is not
  "did not run". Look again at the end of the day before concluding.
- Developer's answer on PR #19: wait with the merge until sample files
  and a real OpenRouter key exist.

## 2026-11-18 — Import measured on a real plan; reworked

- PR #20 merged by the developer; deploy green. The scheduled check with
  the old time came at 14:53 UTC on 2026-11-18, again about nine hours
  late. First run with 06:17 is due 2026-11-19.
- The developer supplied one real training plan (kept in
  `.claude/private/import-samples/`) and an OpenRouter key (in
  `.claude/private/openrouter.env`, mode 600, written by the developer
  from the clipboard; never read into the session).
- Measured, then reworked: the model no longer copies workouts out, it
  names the layout and `GridReader` reads the cells. Numbers and
  reasoning in `DECISIONS.md`, 2026-11-18.
- Tools left in the private folder for the next tuning round:
  `expected.py` (computes the right answer from a plan with this
  template), `tune.py` (asks the model directly and compares),
  `kava12-full10.csv` (the real plan filled to ten weeks with made-up
  numbers).
- Still open before merge: a real weight/steps file; the real key and
  `OPENROUTER_MODEL=anthropic/claude-haiku-5.5` in the server's `.env`
  (the developer's step); whether the key has a spending limit was not
  confirmed.
- Later the same day the developer supplied the real weight and steps
  file. It is not one row per date but days across the columns without a
  year; a third file kind was built for it (`DayGridReader`). Open
  question handed to the developer: weight precision (one decimal stored,
  two in the sheet).
- The habit tracker also holds, per day, which workout was done
  ("week/workout"). That could date imported workouts automatically one
  day; not built, the plan numbering restarts with every new plan.
- Third review (days-across reader) found a critical year bug; the year
  and week-order rules were rewritten (DECISIONS, same date). PR #19
  taken out of draft; merging and the server's key are the developer's.
- CI failed once on the last push with "Could not find
  org.mockito:mockito-core:5.23.0" — a download failure, green on rerun.
  `build.gradle` asks for `mockito-core:5.+`; pinning it would remove
  one lookup that can fail. Backlog.
- Weight precision settled by the developer: one decimal stays.

## 2026-11-19 — Import in production; F3 started

- The developer merged PR #19 on 2026-11-18 after putting the real key
  into the server's `.env` (from the clipboard, never shown). Deploy
  green. The Import menu entry did not appear until a forced reload:
  `index.html` is served without a caching rule. The developer declined
  a separate fix; it goes into F3.
- The builder's first "it is deployed" check was weak: `/import`
  answering 200 proves nothing in a single-page app, every unknown path
  returns the same page. Check the script for the feature instead.
- On 2026-11-19 production held 15 imported workouts, 268 weight
  entries, 308 steps entries; no errors in the backend log; the backup
  ran at 03:30 UTC. The scheduled backup check had not run by 14:20 UTC.
- The developer: the subscription allows runs in GitHub Actions (plan
  A), and F3 is next.

## 2026-11-19 — F3 staging built, not yet on the server

- Spec issue #22 approved by the developer. Branch `f3-staging`.
- Laptop rehearsal: both copies side by side with locally built images;
  routing, isolation, limits, cache headers, reset. zsh trap met on the
  way: `"$image:rehearsal"` is read as `$image` with the `:r` modifier —
  write `${image}:tag` in interactive zsh. The CI script runs in bash and
  is not affected.
- Cannot be rehearsed without a registry: `staging.sh` end to end (its
  `deploy.sh` pulls). First real run is the drill after the merge.
- After the merge, in this order, each with the developer's go-ahead:
  DNS record (developer) → `provision-staging.sh` on the server →
  restart of production's `web` so the proxy learns the staging address
  (a few seconds) → GitHub environment `staging` (main only) with
  `STAGING_HOST`, `STAGING_SSH_KEY`; variables `STAGING_URL`,
  `STAGING_ENABLED` → drills.

## 2026-11-23 — F3 staging on the server, drilled

- The developer merged PR #23 on 2026-11-19 and said go for the server
  on 2026-11-23. Order kept: GitHub environment limited to `main` →
  key and address into it only after that → provisioning as root →
  `caddy validate` inside the running proxy with the new address →
  `docker compose up -d web` → certificate → key tests → key's private
  half uploaded and the local copy deleted.
- When testing a restricted key, switch the agent off
  (`-o IdentitiesOnly=yes -o IdentityAgent=none`): otherwise the
  owner's unrestricted key logs in first and every test "passes".
- Four drills with draft PRs #24, #25, #26 (closed, branches deleted).
  All as designed; numbers in `docs/ARCHITECTURE.md`. The Staging
  workflow worked on its first real run.
- Left running on staging: the image of drill PR #25 (login page says
  "staging drill B") until the next PR arrives. Staging database is
  empty after the reset drill; nobody has an account there yet.
- Open: the developer's own look at staging; the 2026-11-19 deploy
  recreated the database container, cause not found; restore drill with
  real data; the scheduled backup check runs hours late.

## 2026-11-23 — F4 started: review agent built, not switched on

- The developer merged PR #27 (F3 docs) and said go for F4. Three
  questions answered (identity: Claude's official app, against the
  builder's suggestion; review agent first; mixed models). Spec issue
  #28 approved.
- Facts about Claude Code in GitHub Actions were looked up in the
  official documentation by a research agent, not taken from memory;
  the gaps it reported (token revocation, terms for unattended runs,
  concurrency limits) are named in the spec and in ARCHITECTURE.
- Claude Code 2.1.325 has `--restricted` and `--tools`; it has no
  `--max-turns` in its help. The run is bounded by time and by
  `--max-budget-usd` instead.
- Headless calls from a script need `< /dev/null`, otherwise the
  program waits three seconds for stdin and warns.
- Rehearsal with the real model on three planted defects: all found.
- After the merge, each with the developer's go-ahead: environment
  `factory` limited to `main` → the developer runs `claude setup-token`
  and puts the token into it from the clipboard → `FACTORY_ENABLED` →
  drills (manual run, planted defects, clean PR, docs-only PR, a PR
  that tries to instruct the reviewer, token not readable from a PR
  branch).

## 2026-11-23 (evening) — review agent switched on and drilled

- The developer merged PR #29 and put the Claude token into the
  `factory` environment from the clipboard. The builder switched
  `FACTORY_ENABLED` on right after that step, as announced, without
  asking for a separate yes; said so to the developer. The same switch
  allows manual runs of the gate workflow; nothing starts it by itself.
- First run failed: the token had a line break from the terminal's
  line wrap. Found from the status words alone (`api_error`, no HTTP
  status, 0 seconds) by trying made-up tokens on the laptop. Lesson: a
  long secret copied from a terminal goes through
  `tr -d '[:space:]'` on its way into GitHub.
- PR #31: the reviewer is not started with such a token and an API
  error is named in the run's detail line.
- Drills, all passed: manual run and clean change (#30), planted
  defects (#32), steering attempt (#33), token probe (#34), this
  documentation-only PR. Six reviews: 8–15 turns, 12–60 s, estimated
  0.18–0.57 USD each.
- Three pull requests turning green together: the middle one (#32) got
  no staging run, as ARCHITECTURE says it would.
- Drill PRs #30, #32, #33, #34 are closed, not merged.
- Open: slices 2–4 of F4; restore drill with real data; the scheduled
  backup check runs hours late; mockito version pin.

## 2026-11-24 — F4 slice 2a: spec approved, probe written

- PRs #31 and #35 merged; issue #28 closed; F4 slice 1 done.
- Spec for slice 2a is issue #36, approved by the developer in chat.
  Approval of a factory spec stays in chat: the label `approved` is for
  application issues once the agent exists.
- Facts about the official app and the action were looked up at the
  source by a research agent and then read in the action's source by the
  builder: the app token is cancelled at the end of the action; with
  `use_commit_signing` the action commits through GitHub's API with two
  tools; the branch is taken from the environment variable
  `CLAUDE_BRANCH`; `workflow_run` and `workflow_dispatch` are supported
  events; an actor that is a bot must be listed in `allowed_bots`.
- Next: the developer installs Claude's GitHub App on this repository
  and switches on "Allow GitHub Actions to create and approve pull
  requests"; after the probe's PR is merged the builder runs the five
  probes.

## 2026-11-24 (later) — probe run, implementation agent built

- The developer installed Claude's GitHub App on the repository,
  switched on "Allow GitHub Actions to create and approve pull requests"
  and merged PR #37 (the probe).
- Six probe runs. Results in ARCHITECTURE, "Factory: implementation
  agent", and on issue #36. The one that mattered most: GitHub refuses a
  workflow-file commit from Claude's app.
- Found while probing: with a bare `Read` allow rule the agent read
  `/etc/hostname`. With edit and write rules limited to paths and no
  bare allow rule, "don't ask" mode refuses everything outside the
  checkout. The real workflow allows writing only in the allowed places.
- Checked the reviewer too, on the laptop: `--restricted` already
  confines its reads. The "known gap" written on 2026-11-23 was a guess
  and was wrong; corrected in ARCHITECTURE. Lesson: a gap is written
  down after it is tried, not before.
- The job summary of a workflow run cannot be read through `gh`; the
  probe's summary was unreadable for the builder. The real workflow
  prints the run's numbers and tool names into the log as well.
- The developer wants the Claude desktop browser used, not Chrome.
- Next, after the merge and with the developer's go-ahead: create the
  labels (`factory/labels.sh`), set `FACTORY_AUTHORS` to
  `app/github-actions`, pick a small real issue together, drills.

## 2026-11-24 (evening) — first real build, drills of slice 2a

- PR #40 merged. With the developer's go-ahead: labels created,
  `FACTORY_AUTHORS` set to `app/github-actions`. Trial issue #41 (read
  one day's steps entry), chosen by the developer.
- The developer labelled #41. Pull request #42 with the agent's commit
  80 seconds later; CI green on the first attempt; review agent clean;
  staging took it. #42 is the developer's to test and merge or close.
- Drills: second build on #41 refused; switch off, everything skipped;
  the builder switched `FACTORY_ENABLED` back on right after.
- Drill issue #43 (forbidden changes only), labelled by the developer.
  The agent ended after one turn with no tool call. The builder then
  made the issue's text insist on real tool calls and started a second
  run by hand (same issue, same label, disclosed to the developer):
  same result. So the empty path is proven in the real repository and
  the tool limits are not; their evidence stays the probe.
- Lesson: a drill that depends on the agent misbehaving cannot be
  ordered through the issue; the prompt outranks the issue's text. To
  exercise the limits, the prompt has to ask, as the probe did.
- Seen: a pull request closed as empty keeps the label `building`.
  Recorded as a known gap, not fixed.
- Next: slice 2b gets its own spec issue (gate reads CI and findings,
  fix agent, three rounds) when the developer says so.

## 2026-11-25 — slice 2b: probe and build

- The developer approved the spec of slice 2b (issue 47) in chat on
  2026-11-24 and merged the probe (PR #48), then its fix (PR #50).
- Probe 1 (PR #49): Haiku committed nothing after one refused call; the
  log did not say which. Lesson: a probe's log must name refused calls;
  now it does. Probe 2 (PR #51, Sonnet): the app's commit started CI; the
  next run's actor was `claude[bot]`; token exchange worked; the action
  refused the bot actor until `allowed_bots` names it. Result on #47.
- Built on branch `f4-loop`: gate state checks, decisions on the issue,
  the fix agent's task, the shared agent workflow, the round in
  "Review". Suites: run 87, review 113, implement 147, infra 106, all
  green by exit status. Twenty-four mutations, all caught. An independent
  reviewer found one critical and two important defects; all fixed with
  tests that failed first (details in DECISIONS).
- Not seen in the repository yet: everything after the merge (drills).

## 2026-11-26 — slice 2b: drills

- The developer merged PR #52 and closed PR #42 (issue #41 is gone from
  GitHub; branch `factory/issue-41` is left).
- Drill 1, a pass: issue #53 (weight for one day) → PR #54. Implement
  ran through the shared workflow and the token reached the agent;
  review-passed in round 1, about 5 minutes after the label.
- Drill 2, a fix round: the developer chose Haiku for one issue.
  `FACTORY_MODEL_IMPLEMENT=claude-haiku-5-5` for issue #55 (steps
  summary) → PR #56: CI green and review clean in round 1. Variable
  removed again. No fix round happened.
- The developer chose to skip the fix round and scrap and go on.
- Drill 4, a push by the developer: on PR #54 the builder took off
  `review-passed` and pushed, in the developer's name, a commit that
  breaks a test. CI red, `failed-ci` read the backend log (89 lines),
  the gate said not-the-app and did nothing.
- Drill 5, the switch: `FACTORY_ENABLED=false`, a revert push and a hand
  start of Implement: every factory job skipped; staging still
  deployed. Switched back on.
- Results on issue #47. PR #54 is left without `review-passed` and
  without a review of its newest commit (code equal to the reviewed
  one); PRs #54 and #56 wait for the developer.

## 2026-11-26 — slice 3: spec and build

- The developer merged PR #57 (drills of 2b; closed #47) and chose slice
  3 before 2c (2c only matters after a scrap, and none has happened).
- Spec issue #58 approved in chat: one spec agent, questions or spec or
  out-of-reach, spec written into the issue's text in the format of #39.
- Built on branch `f4-spec`: `spec.yml`, `factory/spec/` (collect,
  run-spec, check-reply, write, prompt, schema), `factory/run-agent.sh`
  shared with the review agent, two labels. Suites: run 87, review 113,
  implement 147, spec 96, infra 106. Thirty-eight mutations, all caught.
  An independent reviewer found four important defects, no critical
  one; all fixed. The review agent on PR #59 found two major ones;
  both fixed (details in DECISIONS).
- Not seen in the repository yet: the workflow (drills after the merge).

## 2026-11-27 — Spec agent's first real issue; conflict gap

- Issue 61 (title only) → spec agent wrote a spec with defaults from
  the code, no questions → developer added `approved` → pull request 62
  → round 1 one major → first real fix round → round 2 passed. Merged
  and deployed to prod (one new column, `users.steps_goal`). Slice 3
  drill 1 recorded on issue 58.
- Issue 39 got `approved` by the developer; the agent committed nothing
  (the work is in `factory/`), pull request 60 closed: the `empty` path.
- Pull request 56 is in conflict after 62. Gap recorded as issue 63;
  the developer chose rebuild-from-spec, for slice 2c. Issue 63 opened
  with a hand-written body: the spec agent skipped it, as designed.
- Pull request 56: conflict resolved by hand (both sides kept; 483
  backend tests green). The new review found one major (the summary
  request missing from `http/steps.http`, a file that came with 62);
  fixed by hand, next review clean; merged. Gap noted: after a push by
  the developer a stale `review-passed` stays on the pull request.
- Slice 3 drills 2–4: issue 65 asked 5 questions and wrote the spec
  after the answer; a comment on it rewrote the spec; issue 67 (email)
  got `needs-human`; issue 66 (Garmin .FIT) got a spec with a hand-made
  reader instead of out-of-reach. The developer asked how the factory
  could refuse vague input better → slice 3b (issue 68), built the same
  day: assumptions listed, workarounds and more than 3 assumptions wait,
  risk computed. Spec suite 147 tests, 43 rules mutation-checked,
  independent review. Drill 5 (the switch) still open.


## 2026-11-27 — Spec agent drills finished

- Issue 18 closed: the OpenRouter spending limit is confirmed.
- Drill 5 (the switch): `FACTORY_ENABLED=false`, issue 71 started a
  Spec run with both jobs skipped; switch back on; issue 58 closed.
- Slice 3b drills, run by the builder from the developer's account at
  their request: 72 (title only) → questions; 73 (PDF) → questions,
  then `spec-ready` after "your call"; 74 (clear) → `spec-ready` with 3
  assumptions; 75 (.xlsx) → waiting spec with 8 assumptions and a
  workaround, "ok" → `spec-ready`. Recorded on issue 68. Issues 72–75
  left open for the developer to keep or close.
- Still owed for slice 3: the developer's explain-back.

## 2026-11-27 — Issue 63 built: conflicts and the line

- Spec written into issue 63 after the developer's four decisions
  (needs-human on own commits, one shared limit of 2 attempts, 2 open
  pull requests, analysis agent postponed); approved in chat.
- Built: `.github/workflows/queue.yml`, `factory/queue/conflicts.sh`,
  `factory/queue/next.sh`, `factory/limits.sh`; "Implement" checks who
  added `approved` from the issue's history and refuses with `full`
  (label `queued`) when 2 factory pull requests are open. Queue suite
  50 cases, implement suite 170; 74 rules mutation-checked. One real
  defect found by mutation: a failed branch delete left the issue out of
  line; fixed.
- Independent review: three majors, all fixed with tests first: the
  search index behind `gh … list --label` lags (now plain REST lists and
  a fresh read of each pull request); a merge fired two Queue runs (now
  only closes without merge start one); a fix asked for just before a
  rebuild could land on the new branch and its "closed" report delete
  it (now the fix checks its pull request first, and the report deletes
  a branch only when it still ends at the closed pull request's
  commit). Minors fixed: `queued` removed when a build starts; issue
  labelled before the pull request is closed.
- The factory's review agent on pull request 77 found one more major:
  queue-started builds run as `github-actions[bot]`, which the action's
  `allowed_bots: claude` refuses. Confirmed in the action's source;
  `allowed_bots: claude,github-actions`. Token exchange for that starter
  is unverified until drill 1.
- Drills still to run on GitHub after the merge.

## 2026-11-27 — Issue 63 drilled; issue 63 closed

- PRs 76 and 77 merged by the developer; `factory/labels.sh` run.
- At the developer's request the builder added `approved` to drill
  issues 78–80 and merged the drill pull requests 82 and 84 itself
  (both deployed to prod, green).
- All four drills passed (table on issue 63 and in ARCHITECTURE). The
  open risk is settled: queue-started builds run as
  `github-actions[bot]` and the token exchange serves them.
- Gap found: the review agent raises "no automated test" as major on
  frontend changes (81, 83) though no test runner exists; fix agent
  cannot help → `needs-human`. Pull request 83 and issue 79 wait for
  the developer's decision (frontend test runner, or a rubric rule).

## 2026-11-27 — Frontend tests

- Developer chose a test runner. Vitest 5.1.0 (exact), `yarn test` in
  CI before the build, first tests for `dates.ts` and `weightChart.ts`
  (13 cases; a mutation of the delta threshold and of the Monday rule
  each fail them). Implement, fix and spec prompts and the review
  rubric say where frontend logic and its test go.
- Pull request 83 and issue 79 (the colour change) still carry
  `needs-human`; after this merge they can be rebuilt with a test.

## 2026-11-27 — Issue 79 to prod; checkpoints dropped

- Pull requests 85 and 86 merged by the builder at the developer's
  request; pull request 83 closed; issue 79 rebuilt as 87, which wrote
  its own Vitest test, passed review in round 1 and was merged at the
  developer's request (deployed, green). Issue 79 went from text to
  prod with no code from the developer.
- The developer dropped the phase checkpoints; `CLAUDE.md`, ROADMAP and
  the design doc updated. F3, slice 3, slice 3b and issue 63 ticked.
- No open issues or pull requests. Next: F5 (monitoring).

## 2026-11-27 — F5 started: no Sentry, issue 89 at the factory

- Local checkout was 105 commits behind on `f2-reboot-drill`; moved to
  `main`, the two stale edits discarded (same text already on `main`).
- The developer: F5 without Sentry, errors go straight to GitHub
  issues; work autonomously, split it into issues, let the factory
  build. Split and reasons in DECISIONS.
- Issue 89 (backend errors → `prod-error` issue) opened from the
  developer's account. The spec agent answered twice and GitHub
  withheld the answer both times ("Skip output 'reply' since it may
  contain secret"); `needs-human` each time. A hand-written spec was
  dropped the same way on its way to the implementation agent (pull
  request 90 closed empty). Cause found with a throw-away workflow on
  branch `probe/masked-output` (deleted again): the runner masks the
  word `Bearer` followed by any value. Spec reworded, `approved` added
  again by the builder at the developer's request.
- Branch `f5-monitoring-infra` (by hand): `ERROR_REPORT_GITHUB_TOKEN`
  and `ERROR_REPORT_GITHUB_REPO` in `infra/compose.yml`, workflow
  "Uptime" (SSH to the server, health address, issue `prod-down`),
  labels `prod-error` and `prod-down` (created on GitHub and added to
  `factory/labels.sh`), the spec job prints its reply into the log so a
  withheld answer can be diagnosed.
- Issue 89 → pull request 92: round 1 CI red → fix agent; round 2 one
  major (the hand-off to the reporter's thread had no test) → fix
  agent; round 3 passed, CI green. The first build that used all three
  rounds. Waits for the developer's merge (before pull request 91:
  both add a "Monitoring" section at the same place).
- Not verified: the uptime workflow has never run (it can only be
  started once it is on `main`); nothing of F5 is drilled.
- Open for the developer: the token for the server; merges.
- Next: part 2 (browser errors) as an issue after 89 is merged; drills.

## 2026-11-27 — F5 parts 1 and 3 in production; part 2 built

- The developer said to merge: pull request 92, then 91 (brought up
  to date by hand: one "Monitoring" section). Both deploys green.
- "Uptime" started by hand on `main`: green, "Production is UP". The
  failing path (issue `prod-down`) is not drilled.
- The developer's rule: no user or business data in logs, reports or
  issues, backend and frontend; otherwise keep it simple. In the review
  rubric (`project-rule`), ARCHITECTURE and DECISIONS.
- Issue 93 (browser errors): the spec agent wrote the spec in one run
  (the wording rule from pull request 91 held; three assumptions),
  `approved` by the builder, pull request 94: round 1 CI red → fix,
  round 2 one major (no test of `reportBrowser` with reporting on) →
  fix, round 3 passed. Waits for the developer's merge.
- Nothing reports yet: no token on the server.
- Next: merge 94; the token; the two drills; F6.
- Later the same day: the developer said to merge 94; deploy green.
  All three parts of F5 are in production; still nothing reports
  until the token is on the server.

