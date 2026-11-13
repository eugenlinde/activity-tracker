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
