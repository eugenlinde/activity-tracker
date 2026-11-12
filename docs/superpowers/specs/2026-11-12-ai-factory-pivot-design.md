# AI factory pivot — design

Date: 2026-11-12. Status: draft, awaiting developer approval.

## Intent and why

The project stops being a "learn Spring syntax by writing it" exercise. The
developer has learned what they wanted from writing code by hand and no
longer sees syntax as the bottleneck. From now on AI writes the
application and its infrastructure. The developer's goal becomes a proof of
concept: **as a developer, ship a working product that improves itself** —
a software factory in the sense of Zach Lloyd's talk (inputs → triage →
spec → implement → review → verify → ship → monitor → back to inputs).

The developer's skill being built is designing and running the factory:
which steps exist, where a human steps in, how feedback, logs and errors
flow back to the top, and how to tell whether the factory works. Learning
infra tools by hand is not a goal; they are parts of the factory.

## Success picture (in ~3 months)

- The developer uses the app daily on laptop and phone. It replaces the
  manual Excel log (weight entry, Garmin steps, workouts, …).
- The app is reliable: data is never lost, a deploy never breaks prod
  without a way back.
- The developer files a bug or feature request from anywhere (phone).
- A few friends can use the app as testers (it is reachable on the public
  internet with HTTPS).
- Live monitoring exists. Errors become work items automatically.
- AI picks up work from issues or monitoring, builds it, and notifies the
  developer when it is ready to test.

**PoC done** = one real bug or feature request travels the whole loop
(phone → triage → spec → code → tests → staging → developer's "ok" → prod
→ monitoring) without the developer writing code. *(builder's call: this
is the acceptance test; the developer confirmed the overall picture.)*

## Anti-goal

**The PoC fails if the developer cannot understand what was built.** A
factory that works but is a black box to its owner is a failure even if
every check is green. This outranks speed and automation:

- Every piece of infra and every factory step ships with a plain-language
  explanation the developer reads before approving it.
- A single living architecture doc (`docs/ARCHITECTURE.md`) describes what
  runs where, how a change flows from issue to prod, where secrets live,
  how to restore a backup, and how to stop the factory. Updated in the same
  PR as any change to infra or the factory.
- At each infra/factory milestone the reviewer asks the developer to
  explain the piece back (draw the flow, say what happens when X fails).
  The milestone is not checked off until they can.
- Prefer fewer, boring moving parts over clever ones, specifically so the
  whole system fits in one head.

## Non-goals (for now)

- Kubernetes, Terraform/IaC, multiple clouds, more than prod + one staging.
- Per-PR preview environments.
- Grafana / Prometheus / log aggregation stacks.
- In-app feedback form (friends give feedback to the developer directly,
  who files the issue).
- PWA or native mobile app — responsive web only.
- Automated skill-improvement loops (observer agents rewriting
  CLAUDE.md/skills). Comes after the basic loop works end to end.
- Fully autonomous prod changes from monitoring with no human approval.
- Any "nice to have" spend or feature. Bare minimum only.

## Constraints

- **Budget: €20/month total** for VPS, backups, domain, and the app's own
  OpenRouter calls (CSV import). Expected ~€8–12; the rest is headroom.
- **Agent compute is outside the budget**: the developer has a Claude Code
  subscription they can use for development. Whether that plan allows
  unattended runs in GitHub Actions (API key / OAuth token in CI) must be
  verified before the factory steps are built. If it does not, the
  fallback is: agents run interactively from the developer's machine, and
  the automated trigger part waits.
- **Real data**: prod holds the developer's and friends' real data. Nothing
  automated may touch prod without the developer's approval.
- Keep the existing stack: Spring Boot (Gradle) backend, Postgres +
  Liquibase, React + Vite + TS frontend, JWT auth, local-filesystem media
  storage, OpenRouter client. Existing test suite (115 green as of
  2026-11-05) stays green and is the factory's safety net.

## Decided by the developer

- Pivot to 100% AI-built application and infra.
- GitHub Actions as the CI / agent runtime.
- Tool choices are delegated to the builder; bare minimum only.
- Agents use the developer's Claude Code subscription; the €20 budget covers only hosting,
  backups, domain, and app API calls.

## Tooling (builder's calls, developer may veto)

- **Repo**: private GitHub repo. Push the existing history. Bring `.claude/`
  planning docs under version control.
- **Factory engine**: Claude Code GitHub Action, triggered by issue labels
  and `@claude` mentions.
- **Factory loop**:
  1. Developer opens an issue (GitHub mobile app).
  2. Triage agent labels it (bug / feature / unclear), asks questions on
     the issue if unclear.
  3. For non-trivial work, spec agent writes a short product + tech spec as
     an issue comment.
  4. Developer approves by adding a label (e.g. `approved`).
  5. Implementation agent opens a PR.
  6. CI runs the test suite; review agent reviews the PR.
  7. PR is deployed to staging; developer gets a GitHub notification.
  8. Developer tests on staging and merges.
  9. Merge to main deploys to prod.
  10. Sentry errors on prod create GitHub issues → back to step 2.
- **Hosting**: one small Hetzner VPS. Docker Compose runs app, Postgres,
  and Caddy (automatic HTTPS). Prod and one staging slot on the same box,
  separate databases and separate media directories.
- **Images**: built in Actions, pushed to GHCR. Deploy via SSH +
  `docker compose pull && up`.
- **Backups**: nightly `pg_dump` **and the media storage directory** to
  cheap off-box object storage. A restore is actually performed at least
  once and documented in `ARCHITECTURE.md`.
- **Monitoring**: Sentry free tier (backend + frontend errors, with GitHub
  issue creation), a free external uptime check on the health endpoint.
- **Config**: prod secrets (DB password, JWT secret, OpenRouter key) as env
  vars from GitHub Secrets / a server-side env file. The current hard-coded
  local datasource credentials stay for local dev only.

## Further builder's calls (from the builder check)

- **Signup is closed before the app goes public.** Registration becomes
  invite-only (an email allowlist the developer controls). Open signup on a
  public URL would let strangers store data next to friends' real data and
  spend the OpenRouter budget. Must land before the first public deploy.
- **GitHub plan assumed free.** Private-repo Actions minutes are limited;
  agent runs and the Testcontainers suite consume them. Keep triggers
  label-based (no polling), cache Gradle and Docker layers. If minutes run
  out, move to a self-hosted runner on the VPS instead of paying.
- **Health endpoint**: add Spring Boot Actuator `health` only, for the
  uptime check and the deploy smoke test.
- **No CORS config needed**: Caddy serves frontend and API from one
  origin.

## Agent runtime: plan A and plan B (decided by the developer)

- **Plan A**: the developer's Claude Code subscription runs unattended in
  GitHub Actions. The developer does not yet know if the plan allows it;
  they verify.
- **Plan B**: agents run in GitHub Actions against the OpenRouter API with a
  lighter model (developer named Sonnet 5.5 as an example). Unattended
  triggering stays part of the PoC either way — a laptop-only loop does not
  count as PoC done. *(builder's reading of the developer's answer)*
  Plan B agent tokens have their own OpenRouter monthly cap, outside the
  €20 budget. Cost optimisation of agent runs is not a concern for now.
- Builder's call: the agent harness is chosen so that switching A ↔ B is a
  config/secret change, not a rewrite.

## Product scope for the 3-month horizon (decided by the developer)

- **In**: finish CSV import; manual entry of body weight and daily steps
  (needed regardless of any integration); everything needed for daily use
  on laptop and phone.
- **Factory workloads later, same horizon**: automatic Garmin steps sync
  (blocked on Garmin developer program access — verify early, since
  approval can take weeks); Xiaomi smart-scale weight reading
  (unofficial/best-effort, may break).
- **Out**: Google Sheets integration — dropped.
- **Undecided**: reporting. Builder's call: deferred; it becomes a factory
  ticket once daily use shows which views are actually needed.

## Frontend prototype = product target

The developer has a design prototype (Design canvas "Activity Tracker —
prototype", https://claude.ai/artifact/T7q7abGGd1HZWq2sjApFcQ). A copy of
its eight artboards lives in `docs/design/prototype/` so factory agents
running in CI can read it. It is the visual and functional target for the
product. Sample data in it is illustrative, not real.

**Look**: dark theme only. Manrope (300–600). Ground `#15181D`, cards
`#1B1F25`, inputs `#181C22`, borders `#22272E` / `#2E343D`, text `#E6E8EB`,
muted `#8E97A3`. Accent sage `#A8B9A3` (primary buttons, goal reached),
secondary blue `#8DAACB`, danger `#C58F94`, sand `#C9B08A`. Radius 8–12 px,
44 px touch targets. Left sidebar nav that collapses to a burger menu below
760 px.

**Screens** (nav order): Dashboard, Workouts, Templates, Aerobic, Diet,
Shares, Import.

| Screen | What it shows / collects | Backend today |
|---|---|---|
| Dashboard | Weight trend (12 weeks), daily steps vs. goal (2/8 weeks), diet last 7 days, month calendar with workout/aerobic markers | none of weight, steps, diet exist |
| Workouts | Session list by date, search, "with comments" filter, comment and media counts | activities list exists; no comments |
| Workout detail | Exercises with target sets × reps · RIR, logged sets, volume, per-exercise comments, media upload | exercises `jsonb` + media exist; no targets/RIR, no comments |
| Templates | "Coming soon" — reusable programmes, sessions named `template week/routine` | nothing |
| Aerobic | Entries (type, date, minutes, note), weekly-minutes chart | `Activity` can hold these; no dedicated view or aggregates |
| Diet | Per-day status (on plan / more / less / outside) + comment, plan kcal/protein, calendar | nothing |
| Shares | Invite by email with pending state, revoke, logs shared with you | grant/revoke exist; no pending/invite state |
| Import | CSV **or Excel**, AI classifies each row (Workout / Aerobic / Weight / Steps / Diet / Skip), per-row include/exclude, confirm | CSV-only workout import in progress |

**Conflicts with decisions logged before the pivot** — the prototype is the
newer statement of intent, so it wins unless the developer says otherwise
*(builder's call)*:

- Sharing was "read-only". Prototype: viewers can comment, never edit.
- Import was "CSV only, workouts only, reject the whole file on any invalid
  row". Prototype: Excel too, multiple record kinds, per-row
  include/exclude.
- Shares were granted directly to an existing user. Prototype: invite by
  email with a pending state. This fits the closed-signup decision — an
  invite is how a friend gets an account.

**Scope reading** *(builder's call)*: the prototype is the product backlog
the factory works through, not a gate on "PoC done". The PoC is about the
loop; the prototype screens are what flows through it. Suggested first
slices: restyle the existing screens to the prototype's look, then weight
and steps (manual entry + dashboard), since those replace the Excel log.

## Existing work in flight

Phase 5 CSV import (steps 4d–6 in `.claude/SPEC.md`) is unfinished. It
becomes the factory's first real workload, built by AI. The order between
"finish CSV import" and "build the factory" is left to the plan, but the
PoC acceptance test requires at least one item to go through the full
loop.

## Changes to the project's working rules

`CLAUDE.md` is rewritten: the agent is no longer a reviewer-only pair
programmer forbidden to write code. New role: builder of the app and the
factory, and explainer to the developer (per the anti-goal). The roadmap
in `CLAUDE.md` and `.claude/ROADMAP.md` is restructured around the
factory. `.claude/DECISIONS.md` logs the pivot with both the old and new
positions.

## Left to the builder

- Exact phase order and milestones in the new roadmap.
- Hetzner instance size, backup storage provider, domain registrar.
- Staging deploy mechanics (which branch/PR occupies the single slot).
- Label names, agent prompts, workflow file structure.
- How ARCHITECTURE.md is organized.
