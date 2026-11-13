# Roadmap

Pivot 2026-11-12: AI builds the app and the infra; the developer owns and
understands the factory. Design:
`docs/superpowers/specs/2026-11-12-ai-factory-pivot-design.md`.
The pre-pivot roadmap (learning phases 0–7, debt lists) is kept privately
by the developer, outside the repo.

Each phase ends with an explain-back checkpoint: the developer explains the
piece in their own words before the phase is checked off.

## Factory phases

- [x] **F0 — Pivot groundwork** (done 2026-11-12)
    - [x] Design doc written and approved by the developer (2026-11-12)
    - [x] `CLAUDE.md` rewritten for the builder + explainer role
    - [x] Prototype copied to `docs/design/prototype/`
    - [x] `docs/ARCHITECTURE.md` skeleton (describes today's local-only setup)
    - [x] Implementation plan for F1 (`docs/superpowers/plans/2026-11-12-f1-repo-and-ci.md`), awaiting review
    - [x] First commit of the pivot docs
- [x] **F1 — Repo and CI** (built and verified 2026-11-12; explain-back
      deferred by the developer 2026-11-13 — see Understanding debt)
    - [x] Private GitHub repo `eugenlinde/activity-tracker`, history pushed,
          `.claude/` tracked (2026-11-12)
    - [x] CI on every PR: backend build + tests (Testcontainers), frontend
          build + lint — merged by the developer, `main` green
          (2026-11-12).
    - [x] Protecting `main`: branch protection on (PR required, `backend`
          + `frontend` green, branch up to date, applies to the owner too).
          Proven: direct push rejected, red PR blocked (2026-11-12).
    - [x] Pre-publication clean-up: author email, sample data, fixture
          image, licence, README, private notes kept out (2026-11-12)
    - [x] Repo made public with the developer's ok (2026-11-12)
    - [ ] Developer: check whether the Claude Code subscription allows
          unattended runs in GitHub Actions → plan A or plan B (OpenRouter)
    - [ ] Explain-back: what CI does on a PR and what a red check means
          — deferred
- [ ] **F2 — First deploy**
    - [ ] Dockerfile(s): backend jar + frontend static build
    - [ ] Prod config from env vars (DB, JWT secret, OpenRouter, storage path)
    - [ ] Closed signup (invite / allowlist) — before anything is public
    - [ ] Actuator `health` endpoint
    - [ ] VPS, domain, Caddy with HTTPS, Docker Compose for prod
    - [ ] Deploy workflow: merge to `main` → image to GHCR → SSH deploy →
          health check
    - [ ] Nightly backup of database + media off-box; one real restore test
    - [ ] Explain-back: path of a request, where data lives, how to restore
- [ ] **F3 — Staging**
    - [ ] One staging slot on the same VPS, own database and media dir
    - [ ] PR → staging deploy, developer notified
    - [ ] Explain-back: how staging differs from prod and who can reach it
- [ ] **F4 — Factory loop**
    - [ ] Agent runtime in Actions (plan A or B), switchable by config
    - [ ] Triage agent, spec agent, implementation agent, review agent
    - [ ] Label-driven hand-offs; nothing merges without the developer
    - [ ] Explain-back: every step of the loop and how to stop it
- [ ] **F5 — Monitoring loop**
    - [ ] Sentry (backend + frontend) → GitHub issues
    - [ ] External uptime check on the health endpoint
    - [ ] Explain-back: what happens between an exception and an issue
- [ ] **F6 — PoC acceptance**
    - [ ] One real bug or feature goes phone → triage → spec → code → tests
          → staging → "ok" → prod → monitoring, with no code from the developer
    - [ ] Write-up: time taken, number of human interventions, what broke

## Understanding debt

Explain-back checkpoints the developer chose to postpone until the MVP is
usable (decided 2026-11-13). `docs/ARCHITECTURE.md` is still kept current
with every change, so the explanations exist to read later. To be paid
back before F6 (PoC acceptance) at the latest.

- F1: what happens when a PR is opened; what a red check means and where
  to look; why the test suite needs Docker on the CI machine; what
  protects `main` and what does not.

## Product backlog

Goes through whatever part of the loop exists at the time. Order is the
developer's call; this is the suggested one.

- [ ] CI hardening (from the F1 review): fix the 5 oxlint warnings, then
      `oxlint --deny-warnings`; pin the Testcontainers image to
      `postgres:17-alpine` to match dev/prod. Small, good first factory
      tickets.
- [ ] Finish CSV import (pre-pivot steps 4d–6; detail in `.claude/SPEC.md`,
      "Phase 5 — CSV import"). Scope to be re-checked against the prototype's
      Import screen (Excel, multiple record kinds, per-row include).
- [x] Restyle the existing screens to the prototype (dark, Manrope, sidebar
      nav, mobile burger) with Ant Design — issue #4 (2026-11-13, PR open)
- [x] Body weight: manual entry + dashboard trend — issue #5 (2026-11-13,
      PR open)
- [x] Daily steps: manual entry + dashboard chart with goal — issue #6
      (2026-11-13, PR open)
- [ ] Dashboard calendar with workout / aerobic markers
- [ ] Aerobic view with weekly minutes
- [ ] Diet log (per-day status + comment)
- [ ] Workout detail: targets (sets × reps, RIR), volume
- [ ] Comments from viewers on workouts / exercises
- [ ] Share invites with pending state
- [ ] Templates (programmes)
- [ ] Garmin steps sync — needs developer-program access; apply early
- [ ] Xiaomi scale reading — unofficial, best effort
- [ ] Reporting — deferred until daily use shows which views are needed
- Dropped: Google Sheets integration

## Debt carried over from before the pivot

Still relevant for going public:

- JWT in `localStorage`, no refresh token, 24 h expiry
- 403 on a foreign activity id reveals that the id exists (accepted)
- No frontend tests
- `ShareResponse.email` has two meanings
