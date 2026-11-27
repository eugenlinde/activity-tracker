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
    - [x] Developer: the Claude Code subscription allows runs in GitHub
          Actions → plan A (said by the developer 2026-11-19; not yet
          tried with a real run)
    - [ ] Explain-back: what CI does on a PR and what a red check means
          — deferred
- [ ] **F2 — First deploy** (planned 2026-11-13:
      `docs/superpowers/plans/2026-11-13-f2-first-deploy.md`; waits for the
      developer's server, domain, backup bucket and invited emails)
    - [x] Dockerfile(s): backend jar + frontend static build (2026-11-13,
          rehearsed locally)
    - [x] Prod config from env vars (DB, JWT secret, OpenRouter, storage
          path) — set in `infra/compose.yml` (2026-11-13)
    - [x] Closed signup (allowlist from `SIGNUP_ALLOWED_EMAILS`) (2026-11-13)
    - [x] Actuator `health` endpoint (2026-11-13)
    - [x] Server scripts written ahead of the server (2026-11-13):
          `provision.sh`, `deploy.sh`, `backup.sh`, `restore.sh`, `images` +
          `deploy` jobs, `backup-check.yml`. Tested with stand-ins on the
          laptop; never run on a real server. Deploys stay off until the
          repo variable `DEPLOY_ENABLED` is `true`.
    - [ ] VPS, domain, Caddy with HTTPS, Docker Compose for prod
        - [x] Server bought (Hetzner CPX22, Ubuntu 26.04), domain bought,
              DNS record for the tracker subdomain live, `provision.sh`
              run and verified on the box (2026-11-16)
        - [x] First deploy: `production` environment + secrets,
              `DEPLOY_ENABLED` on, site live over HTTPS (2026-11-16)
    - [x] Deploy workflow: merge to `main` → image to GHCR → SSH deploy →
          health check (first real run green and rollback drill passed
          2026-11-16)
    - [x] Nightly backup of database + media off-box; one real restore test
          (2026-11-16: first snapshot stored, restore rehearsed and row
          counts matched — on a database with no user data yet; repeat
          once real data exists)
    - [ ] Explain-back: path of a request, where data lives, how to restore
- [ ] **F3 — Staging** (built and drilled 2026-11-23; explain-back
      deferred like the others — spec: issue #22, approved 2026-11-19; plan:
      `docs/superpowers/plans/2026-11-19-f3-staging.md`)
    - [x] Scripts, compose override, proxy configuration, workflows —
          written, 79 script tests, rehearsed on the laptop with both
          copies side by side (2026-11-19)
    - [x] On the server: DNS record (developer), `provision-staging.sh`,
          GitHub environment `staging` limited to `main` with its key,
          `STAGING_ENABLED` on (2026-11-23)
    - [x] Drills on the real server (2026-11-23): a PR appears on
          staging, a second PR takes the slot, a PR that cannot start
          goes back without touching production, the restricted key
          refuses everything else, reset. Results in
          `docs/ARCHITECTURE.md`, "Staging".
    - [x] The developer's own look at staging (an account exists there
          since 2026-11-23)
    - [ ] Explain-back: how staging differs from prod and who can reach it
- [ ] **F4 — Factory loop** (four slices, each with its own issue)
    - [x] Slice 1, issue #28: runtime proof + review agent, report only
          (plan A proven 2026-11-23: the review runs in GitHub Actions on
          the Claude subscription)
        - [x] Scripts, rubric, prompt, workflow; 54 script tests; one
              rehearsal with the real model on planted defects (2026-11-23)
        - [x] Environment `factory`, the developer's token,
              `FACTORY_ENABLED` on; the drills from the spec, all passed
              (2026-11-23; results in `docs/ARCHITECTURE.md`, "Factory:
              review agent")
        - [ ] Explain-back: what the reviewer sees, what it cannot do,
              how to stop it
    - [ ] Slice 2: implementation agent with fix and analysis agents,
          the gate deciding. Identity: Claude's official GitHub App; the
          agent runs no commands (the developer's decisions of
          2026-11-23 and 2026-11-24). Three steps:
        - [x] 2a, issue #36: approved issue → pull request (2026-11-24)
            - [x] Probe in the real repository (2026-11-24): all five
                  answers in favour; the app cannot change workflow files
            - [x] "Implement" workflow and scripts, 127 script tests,
                  independent review (four important findings fixed)
            - [x] Drills after the merge (2026-11-24): issue 41 → pull
                  request 42 in 80 seconds, CI green on the first
                  attempt (one of one), review clean; a second build and
                  the switch refused; an issue asking for forbidden
                  things ended empty. Not drilled: a stranger's label or
                  issue (needs a second account); the tool limits in the
                  real workflow (the agent declined before trying)
            - [ ] Explain-back: what starts a build, what the agent can
                  and cannot do, what the check does, how to stop it
        - [x] 2b, issue #47: the gate reads CI and findings; fix agent,
              three rounds (spec approved 2026-11-24; done 2026-11-26)
            - [x] Probe (2026-11-25, PRs #49, #51): the token exchange
                  works when the app's own commit started the run; the
                  action needs `allowed_bots: claude`
            - [x] Gate wired in after every review, fix agent, check
                  (2026-11-25; script tests, 24 rules mutation-checked, independent review)
            - [x] Drills (2026-11-26): a pass (#53 → #54, round 1), a
                  push by the developer (gate: not-the-app), the switch
                  (everything skipped). A real fix round was tried on
                  Haiku (#55 → #56) and passed in round 1 instead. Not
                  drilled, by the developer's decision: a real fix round
                  and a scrap; the first real failure shows them
            - [ ] Explain-back: one round, what the gate decides, what
                  stops the loop, how to stop it
        - [ ] 2c: analysis agent and one automatic rebuild
            - [ ] Issue #63: a factory pull request in conflict with
                  `main` is closed and rebuilt from the spec on the
                  current `main`; at most 2 open factory pull requests,
                  the rest wait as `queued` (spec approved 2026-11-27)
                - [x] Built (2026-11-27): `queue.yml`,
                      `factory/queue/`, owner check from the issue's
                      history; script tests, 74 rules mutation-checked,
                      independent review (three major findings fixed)
                - [x] Drills (2026-11-27, recorded on #63, closed): the
                      line (78–80), a real conflict (81 → rebuilt as
                      83), the token exchange for a queue-started build,
                      a pull request only behind, the switch
            - [x] Review rubric vs. frontend tests: "no test" raised
                  as major on frontend changes with no test runner
                  (81, 83) → `needs-human`. Developer chose a test
                  runner (2026-11-27): Vitest 5.1.0 in CI; prompts and
                  rubric updated
            - [ ] Analysis agent and a rebuild after a scrap
                  (postponed by the developer 2026-11-27)
        - [ ] Later: build the jar in a CI job that cannot publish, so
              that the agent may change `build.gradle`
    - [ ] Slice 3, issue #58: spec agent (triage and spec in one; spec
          approved 2026-11-26)
        - [x] Built (2026-11-26): `spec.yml`, `factory/spec/`, shared
              `factory/run-agent.sh`; script tests, 38 rules
              mutation-checked, independent review
        - [x] Drills: clear request to `review-passed` (61 → 62), vague
              request to questions and a spec (65), a change asked on a
              spec (65), out of reach (67; 66 became slice 3b), the
              switch (71, 2026-11-27: Spec run started, both jobs
              skipped); recorded on #58, closed
        - [ ] Slice 3b, issue #68: assumptions listed, a workaround or
              more than 3 assumptions wait for confirmation, risk
              computed from facts (spec approved 2026-11-27)
            - [x] Built (2026-11-27): script tests, 43 rules
                  mutation-checked, independent review
            - [x] Drills (2026-11-27, recorded on #68): title only →
                  questions (72); a request needing a PDF library →
                  questions naming the workaround, then `spec-ready`
                  (73); a clear small request → `spec-ready` with 3
                  assumptions (74); a precise request needing an Excel
                  library → waiting spec with 8 assumptions and a
                  workaround, then "ok" → `spec-ready` (75)
        - [ ] Explain-back: what starts it, the three answers, where the
              spec lives, how to stop it
    - [ ] Slice 4: acceptance — one issue from label to a tested pull
          request on staging without an interactive session
    - [ ] Review loop: 3 rounds, scrap and rebuild (design approved
          2026-11-16: `docs/superpowers/specs/2026-11-16-review-loop-design.md`)
        - [x] Part 1: gate script, labels, workflow, tests (2026-11-16,
              not yet drilled on a real PR)
        - [ ] Part 2: review, fix, analysis agents (after plan A/B)
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

- F3: how staging differs from production, what the two share, who can
  reach it, how to wipe it and how to stop it.
- F1: what happens when a PR is opened; what a red check means and where
  to look; why the test suite needs Docker on the CI machine; what
  protects `main` and what does not.
- F4 slice 1: what the review agent sees, what it cannot do, how to
  stop it.
- F4 slice 2a: what starts a build, what the implementation agent can
  and cannot do, what the check after it does, how to stop it.

## Product backlog

Goes through whatever part of the loop exists at the time. Order is the
developer's call; this is the suggested one.

- [ ] CI hardening (from the F1 review): fix the 5 oxlint warnings, then
      `oxlint --deny-warnings`; pin the Testcontainers image to
      `postgres:17-alpine` to match dev/prod. Small, good first factory
      tickets.
- [x] CSV import: workouts, weight and steps — issue #18 (2026-11-17
      built; 2026-11-18 measured on the developer's real files and
      reworked so the model only names the layout and code reads the
      cells; merged and in production 2026-11-18, PR #19). Spending limit on
      the OpenRouter key confirmed by the developer 2026-11-27; closed.
- [ ] Small fixes found along the way: `index.html` served without a
      caching rule (fixed in the F3 change, PR #23); `mockito-core:5.+` in `build.gradle`
      should be a fixed version (one CI run failed on its download);
      the scheduled backup check runs about nine hours late whatever
      time it asks for (alarm to the server side in F5).
- [ ] Restore drill with real data — the first one ran on an empty
      database; production holds real entries since 2026-11-18.
- [ ] Later for import: Excel files, aerobic and diet rows, workout
      dates filled in automatically (the habit tracker has a row saying
      which workout was done on which day).
- [x] Restyle the existing screens to the prototype (dark, Manrope, sidebar
      nav, mobile burger) with Ant Design — issue #4 (2026-11-13, PR open)
- [x] Body weight: manual entry + dashboard trend — issue #5 (2026-11-13,
      PR #8 open, now also carries steps)
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
