# Automatic review loop for factory PRs — design

Date: 2026-11-16. Status: approved by the developer 2026-11-16.
Part of roadmap phase **F4 — Factory loop**. Extends step 6 ("CI runs the
tests; review agent reviews the PR") of the loop in
`2026-11-12-ai-factory-pivot-design.md`.

## Intent and why

When the implementation agent finishes a PR, an independent review agent
checks it automatically. The developer should only ever be asked to test a
PR that has no known serious defects. A PR that is badly wrong is not
patched over and over: it is thrown away, the cause is analysed, the spec or
plan is corrected, and the work is rebuilt from scratch. Patching a broken
foundation produces worse code than rebuilding from a better spec.

## What the developer decided

- **Automatic.** The review starts by itself when the implementation agent
  has finished its PR. No human trigger.
- **Only major and critical findings.** The review agent reports nothing
  below major: no style, naming, or nice-to-haves.
- **More than 5 critical findings → scrap at once.** The branch is not
  fixed; it is deleted. Only critical findings count toward this limit,
  majors do not. *(builder's reading: "üle 5" = 6 or more.)*
- **At most 3 review rounds.** If the third review still finds any major or
  critical defect → scrap, the same as above.
- **Scrap means:** delete the branch, analyse why the defects happened,
  correct the original task or plan, rebuild from zero.
- **Rebuild starts by itself (option B).** The corrected spec does not wait
  for the developer's `approved` label.
- **Built purely on GitHub**: issues, PRs, labels, Actions. No extra
  service.
- **Rules enforced by a script, not by an LLM (approach 1).** Agents only
  produce content (code, findings, analysis). Counting, limits, and the
  choice of next step are done by a plain shell script that is tested.

## Builder's calls (the developer may override)

- **One automatic rebuild per issue (N = 1).** If the rebuilt attempt is
  scrapped too, the issue gets `needs-human`, the factory stops on that
  issue, and the developer is notified. Reason: without a cap, scrap →
  rebuild → scrap can run forever, and a second failure usually means the
  spec needs a human.
- **CI is part of each round.** A round = one push by the implementation
  agent, then CI, then review. If CI is red, the review agent does not run;
  the round counts as failed with the CI failure as its finding (severity
  major) and goes to fix. Reason: reviewing code that does not build wastes
  a run, and a PR that cannot get CI green in 3 rounds should be scrapped
  like any other.
- **Severity rubric** (given to the review agent verbatim; it must name
  which rule each finding breaks):
  - *critical*: data loss or corruption; a security hole (auth bypass,
    one user reading or changing another's data, a secret in the code or
    logs); prod cannot start or a migration cannot run; the change does
    the opposite of the spec.
  - *major*: wrong behaviour on the main path of the feature; an acceptance
    criterion of the spec is not met; new behaviour has no test; an error
    case that will happen in normal use is not handled (crash, 500, lost
    input); a hard rule from `CLAUDE.md` is broken (entity at the API
    boundary, logic in a controller, code comments).
  - Anything else is not reported.
- **Review agent is independent.** It sees the issue, the approved spec
  (the latest spec comment), the diff, the CI result, and the rubric. It
  does not see the implementation agent's reasoning. From round 2 on it
  also gets the previous round's findings and must say for each whether it
  is fixed — and still does a full fresh review.
- **Findings are machine-readable.** The review agent writes a JSON list
  (`severity`, `rule`, `file`, `line`, `what`, `why`) and also a readable
  PR review comment. If the JSON is missing or invalid, the review is run
  once more; if it fails again, the PR gets `needs-human`.
- **State lives in labels**, so anyone can see where a PR or issue is from
  the phone:
  - PR: `factory` (this PR is in the loop), `review-round-1` … `-3`,
    `review-passed`.
  - Issue: `attempt-1`, `attempt-2`, `needs-human`.
- **The loop only applies to PRs with the `factory` label**, which only the
  implementation agent sets. PRs opened by hand in an interactive session
  get one report-only review per push: findings are posted, nothing is
  fixed or scrapped automatically.
- **Scrap keeps the evidence.** The PR is closed (not deleted) with a
  comment saying why; the branch is deleted. GitHub keeps the closed PR's
  diff and review comments, which is what the analysis agent reads.
- **Analysis (post-mortem) agent**, after a scrap, writes on the issue:
  1. what went wrong, each cause tagged: *spec unclear or wrong*, *plan
     wrong*, *implementation careless*, or *review wrong*;
  2. a corrected spec as a new comment, marked as version 2, with a short
     list of what changed from version 1.
  If the main cause is *review wrong*, it does not rebuild; the issue gets
  `needs-human` instead (a wrong reviewer would scrap the rebuild too).
- **After a second scrap** the analysis is still written (so the developer
  can read it) but no rebuild starts.
- **Nothing reaches prod differently.** `review-passed` only means "ready
  for the developer". Staging (F3) and the developer's merge stay as they
  are.
- **One run at a time per issue** (Actions concurrency group per issue), so
  a fix push and a review cannot race.
- **Stop switch:** repo variable `FACTORY_ENABLED`. When it is not `true`,
  every factory workflow exits at its first step. Same pattern as
  `DEPLOY_ENABLED`.
- **Who can start it:** only events from actors with write access move the
  loop (the repo is public; labels can only be set by collaborators, and
  workflows ignore PRs from forks).

## Builder's calls from the builder check

- **Issue without a spec.** Small issues may have no spec comment. Then
  the issue body counts as spec version 1 for the reviewer and the
  analysis agent. The analysis always writes a full spec v2, so a rebuild
  always has a proper spec.
- **What starts a rebuild:** the gate script puts `attempt-2` on the
  issue; that label starts the implementation agent, the same way
  `approved` starts it the first time.
- **The developer steps in by removing `factory`** from the PR. From then
  on the loop ignores that PR: no more review rounds, no fixes, no scrap.
  Pushes to a `factory` PR by anyone other than the factory's own app do
  not count as rounds and do not start a review.
- **Restart after `needs-human`:** the developer removes `needs-human` and
  adds `approved` again. That starts a fresh attempt with the counters
  reset (attempt labels cleared). The cap stops the machine, not the
  human.
- **Report-only review covers every non-`factory` PR**, infra PRs and PRs
  from interactive sessions included. It runs once per push, after CI.
- **Build order.** The gate script, labels, and scrap/close/delete steps
  do not depend on plan A or B and can be built and tested first with
  stub findings. The agent steps (review, fix, analysis) are wired in once
  the runtime is chosen, alongside the implementation agent in F4.

## The flow

```
implementation agent pushes, PR labelled factory + review-round-1
        │
        ▼
CI (backend, frontend, infra) ──red──► round failed (CI finding)
        │ green
        ▼
review agent ──► findings JSON + PR review comment
        │
        ▼
gate script decides:
  critical ≥ 6                      → SCRAP
  no major/critical                 → review-passed, notify developer
  findings, round < 3               → FIX: implementation agent fixes on
                                      the same branch, label round+1
  findings, round = 3               → SCRAP

SCRAP: close PR, delete branch → analysis agent on the issue
  issue has attempt-1, cause ≠ review wrong → spec v2, attempt-2,
                                              implementation agent
                                              starts from zero
  otherwise                         → needs-human, stop
```

Worst case per issue: 2 attempts × 3 rounds × (review + fix) + 2 analyses
≈ 14 agent runs. The repo is public, so standard Actions minutes are free;
agent tokens are on plan A's subscription or plan B's OpenRouter cap.

## Approaches considered

1. **Label state machine, rules in a tested script** — chosen by the
   developer. Every step visible in the PR/issue history, limits are code,
   each step can be stopped on its own. Costs a few small workflow files
   and a label scheme to document.
2. **One long agent session per issue** that builds, reviews itself, fixes
   and scraps. One file, but the agent grades and polices itself, the run
   is a black box (breaks the anti-goal), and a job has a 6-hour limit.
3. **External review service** (e.g. CodeRabbit). Extra tool and spend;
   the severity rule cannot be controlled. Dropped.

## Constraints and dependencies

- **Needs the agent runtime (plan A or B) first.** The developer has not
  yet checked whether the Claude Code subscription allows unattended runs.
  Nothing here can run until that is settled.
- **Needs the implementation agent** (also F4). This design covers only
  what happens after it has opened a PR, plus the rebuild hand-back.
- **Token for opening PRs.** A PR or push made with the default
  `GITHUB_TOKEN` does not start other workflows, so CI and the review would
  never run. The agents must act through a GitHub App token (Claude's app
  on plan A; a small GitHub App the developer creates on plan B, free).
- Budget: no new monthly cost.

## Goals, non-goals, anti-goals

- Goal: a factory PR reaches the developer only with zero known major or
  critical defects, or does not reach them at all and says why.
- Non-goals: reviewing style or minor issues; auto-merging; checking the
  running server (that is monitoring, F5).
- Anti-goal: a loop the developer cannot follow. Every decision the gate
  makes is posted as a comment with the numbers it used ("round 2,
  critical 1, major 3 → fix").

## Left to the builder

- Workflow file split, trigger events (`workflow_run` after CI vs. a label
  event), and agent prompts.
- Gate script language (bash + `jq`), its test fixtures, and where the
  tests run in CI.
- Exact comment formats and hidden markers for finding the latest spec.
- Updates in the same change as the build: `docs/ARCHITECTURE.md` (loop,
  labels, how to stop it), `CLAUDE.md` "How work flows" step 6, and
  `.claude/DECISIONS.md`.
