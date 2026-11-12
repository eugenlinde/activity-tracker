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
