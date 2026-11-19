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
