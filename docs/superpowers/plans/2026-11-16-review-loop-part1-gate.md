# Review loop, part 1 (gate, labels, scrap) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the part of the review loop that does not need an agent: the
script that turns review findings into a decision, the script that carries
the decision out on GitHub (labels, comments, close PR, delete branch), the
labels, a workflow that runs both, and a drill on a throwaway PR.

**Architecture:** Two pure decision functions in `factory/gate.sh` (no
network), one state reader and one actor that call `gh`
(`factory/state.sh`, `factory/apply.sh`), and a workflow
`.github/workflows/factory-gate.yml` that wires them together. Today the
workflow is started by hand with fake findings; part 2 calls the same
workflow from the review and analysis agents. Tests follow
`infra/test/run.sh`: plain bash, a stub `gh` on `PATH` that logs calls.

**Tech Stack:** bash, `jq`, `gh`, GitHub Actions, `shellcheck`.

**Spec:** `docs/superpowers/specs/2026-11-16-review-loop-design.md`

**Not in this plan (part 2, written once plan A or B is chosen):** review,
fix, and analysis agents and their prompts; report-only review on
non-factory PRs; the implementation agent's hand-off; the GitHub App token.

## Global Constraints

- Scrap at once when critical findings ≥ 6. Exactly 5 → fix.
- At most 3 review rounds; any major or critical finding in round 3 → scrap.
- Red CI = failed round, counted as one major finding, review not needed.
- Only `critical` and `major` count; any other severity is ignored.
- Invalid findings → run the review once more; invalid again → `needs-human`.
- One automatic rebuild per issue. Rebuild only when attempt = 1 and the
  cause is not `review`; otherwise `needs-human`.
- Only PRs labelled `factory` are acted on.
- Every decision is posted as a comment with the numbers used, format:
  `Round 2 · CI green · critical 1 · major 3 → fix`.
- Stop switch: repo variable `FACTORY_ENABLED`; anything but `true` → the
  workflow does nothing.
- No code comments. No secrets in files. Commits only when the developer
  asks; nothing pushed to `main`.

## Review Focus

1. Findings that are not a JSON array (empty file, prose, an object,
   `null`) → never `PASS`; first time `RERUN_REVIEW`, second `NEEDS_HUMAN`.
2. Exactly 5 critical in round 1 → `FIX`, not `SCRAP` (boundary).
3. Severity written as `Critical` / `MAJOR`, or `minor` entries mixed in →
   case-insensitive count; minors ignored, not an error.
4. PR with no `review-round-N` label, or two of them, or no linked issue →
   `NEEDS_HUMAN`, never a guess at the round.
5. `FACTORY_ENABLED` not `true` → workflow exits before touching GitHub.

Each has a test in the task that owns it (marked **[RF n]**).

## Labels (created by Task 4)

| Label | On | Meaning |
|-------|----|---------|
| `factory` | PR | PR is in the loop |
| `review-round-1` / `-2` / `-3` | PR | current round |
| `needs-fix` | PR | gate asked the fix agent to work |
| `review-passed` | PR | no major/critical left; ready for the developer |
| `attempt-1` / `attempt-2` | issue | which build of this issue |
| `needs-analysis` | issue | PR was scrapped; analysis agent should run |
| `needs-human` | issue or PR | factory stopped; the developer decides |

## File Structure

- `factory/gate.sh` — pure decisions: `review` and `rebuild` subcommands.
- `factory/state.sh` — reads PR labels and linked issue through `gh`.
- `factory/apply.sh` — carries a decision out through `gh`.
- `factory/labels.sh` — creates the labels above (idempotent).
- `factory/test/run.sh` — all tests; `factory/test/stubs/gh` — logging stub.
- `.github/workflows/factory-gate.yml` — manual + reusable entry point.
- Modify `.github/workflows/ci.yml` (`infra` job) — lint and run factory tests.
- Modify `docs/ARCHITECTURE.md` — new section "Factory: review gate".

---

### Task 1: Decision function `gate.sh`

**Files:**
- Create: `factory/gate.sh`, `factory/test/run.sh`

**Interfaces:**
- Produces:
  - `gate.sh review --round N --ci success|failure --findings PATH --review-try 1|2`
  - `gate.sh rebuild --attempt 1|2 --cause spec|plan|implementation|review`
  - stdout line 1: decision word — review: `PASS` `FIX` `SCRAP`
    `RERUN_REVIEW` `NEEDS_HUMAN`; rebuild: `REBUILD` `NEEDS_HUMAN`.
  - stdout line 2: summary, e.g. `Round 2 · CI green · critical 1 · major 3 → fix`,
    `Round 1 · CI red → fix`, `Attempt 1 · cause implementation → rebuild`.
  - exit 0 on any decision; exit 2 on bad arguments (unknown subcommand,
    round outside 1–3, unknown cause).
- Findings format: JSON array of objects with at least `severity`; other
  fields (`rule`, `file`, `line`, `what`, `why`) are not read by the gate.

- [ ] **Step 1: Write the test harness and failing tests** in
  `factory/test/run.sh`, same shape as `infra/test/run.sh` (`begin`, `run`,
  `fail`, PASSED/FAILED counters, exit 1 if any failed). Helper
  `findings '<json>'` writes a temp file and echoes its path. Cases
  (expected line 1 / line 2):

  ```
  review_empty_round1            r=1 ci=success '[]'                    → PASS  / "Round 1 · CI green · critical 0 · major 0 → pass"
  review_majors_round1           r=1 '[{"severity":"major"},{"severity":"major"}]' → FIX / "... critical 0 · major 2 → fix"
  review_five_critical    [RF2]  r=1 five critical                      → FIX
  review_six_critical            r=1 six critical                       → SCRAP / "... critical 6 · major 0 → scrap (critical ≥ 6)"
  review_six_critical_round3     r=3 six critical                       → SCRAP
  review_round3_one_major        r=3 one major                          → SCRAP / "... → scrap (round 3)"
  review_round3_clean            r=3 '[]'                               → PASS
  review_ci_red_round1           r=1 ci=failure, findings file absent   → FIX / "Round 1 · CI red → fix"
  review_ci_red_round3           r=3 ci=failure                         → SCRAP / "Round 3 · CI red → scrap (round 3)"
  review_case_and_minor   [RF3]  r=1 '[{"severity":"Critical"},{"severity":"MAJOR"},{"severity":"minor"}]' → FIX / "... critical 1 · major 1 → fix"
  review_only_minor       [RF3]  r=1 '[{"severity":"minor"}]'           → PASS
  review_not_array_try1   [RF1]  each of: '' / 'looks fine' / '{}' / 'null', try 1 → RERUN_REVIEW
  review_not_array_try2   [RF1]  same inputs, try 2                     → NEEDS_HUMAN
  review_entry_no_severity[RF1]  '[{"file":"a"}]', try 1                → RERUN_REVIEW
  review_bad_round               r=4                                    → exit 2
  rebuild_attempt1_impl          a=1 cause=implementation               → REBUILD
  rebuild_attempt1_spec          a=1 cause=spec                         → REBUILD
  rebuild_attempt1_review        a=1 cause=review                       → NEEDS_HUMAN / "Attempt 1 · cause review → needs human (review was wrong)"
  rebuild_attempt2               a=2 cause=plan                         → NEEDS_HUMAN / "Attempt 2 · cause plan → needs human (rebuild already used)"
  rebuild_bad_cause              a=1 cause=other                        → exit 2
  ```

- [ ] **Step 2: Run and see them fail**
  Run: `bash factory/test/run.sh`
  Expected: every case FAIL (`gate.sh` missing), exit 1.

- [ ] **Step 3: Implement `factory/gate.sh`** — `set -euo pipefail`, arg
  parsing with `case`. Validity check with
  `jq -e 'type=="array" and all(.[]; has("severity"))'`; counts with
  `ascii_downcase`. Decision order for review: CI red → (round 3 ? SCRAP :
  FIX); invalid → try 1 RERUN_REVIEW / try 2 NEEDS_HUMAN; critical ≥ 6 →
  SCRAP; critical+major = 0 → PASS; round 3 → SCRAP; else FIX. Summary
  uses the middle dot `·` and arrow `→` exactly as in Global Constraints.

- [ ] **Step 4: Run and see them pass**
  Run: `bash factory/test/run.sh && shellcheck factory/gate.sh factory/test/run.sh`
  Expected: all PASS, exit 0, no shellcheck output.

- [ ] **Step 5: Commit** — `factory: decision rules for the review gate`

### Task 2: State reader `state.sh` and `gh` stub

**Files:**
- Create: `factory/state.sh`, `factory/test/stubs/gh`
- Modify: `factory/test/run.sh`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces: `state.sh --repo OWNER/NAME --pr N`, stdout `key=value` lines:
  `factory=true|false`, `round=1|2|3`, `issue=N`, `attempt=1|2`, then
  exit 0. On broken state prints `error=<reason>` and exits 3. Reasons:
  `no-round-label`, `many-round-labels`, `no-linked-issue`,
  `many-attempt-labels`. Missing attempt label on the issue → `attempt=1`.
- Stub `gh`: appends `gh <args>` to `$STUB_LOG`; for
  `pr view N --json labels,closingIssuesReferences` prints
  `$STUB_PR_JSON`; for `issue view N --json labels` prints
  `$STUB_ISSUE_JSON`; exits `$STUB_GH_FAILS` (default 0) for any other
  call whose first two args match `$STUB_GH_FAIL_ON` (e.g. `pr close`).

- [ ] **Step 1: Failing tests** (`state_*` cases):
  - `state_normal`: PR labels `factory, review-round-2`, closing issue 7,
    issue labels `attempt-1` → `factory=true round=2 issue=7 attempt=1`.
  - `state_not_factory`: no `factory` label → `factory=false`, exit 0,
    no further `gh` calls logged.
  - `state_no_round` **[RF4]** → exit 3, `error=no-round-label`.
  - `state_two_rounds` **[RF4]** → exit 3, `error=many-round-labels`.
  - `state_no_issue` **[RF4]** → exit 3, `error=no-linked-issue`.
  - `state_no_attempt_label` → `attempt=1`.
- [ ] **Step 2: Run, expect the new cases to fail.** `bash factory/test/run.sh`
- [ ] **Step 3: Implement `state.sh`** with `gh pr view` / `gh issue view` +
  `jq`. First linked issue only when exactly one; more than one →
  `error=no-linked-issue` too (an ambiguous link is not guessed).
- [ ] **Step 4: Run, all pass; shellcheck clean** (include `factory/test/stubs/*`).
- [ ] **Step 5: Commit** — `factory: read loop state from PR and issue labels`

### Task 3: Actor `apply.sh`

**Files:**
- Create: `factory/apply.sh`
- Modify: `factory/test/run.sh`

**Interfaces:**
- Consumes: decision words and summary from Task 1; `round`, `issue` from Task 2.
- Produces: `apply.sh --repo R --pr N --issue I --round R --decision D --summary S`;
  exit 0 when every `gh` call succeeded, else non-zero with the failing
  call on stderr.

`gh` calls per decision (tests assert these exact calls in the stub log,
in this order):

| Decision | Calls |
|----------|-------|
| `PASS` | `pr comment N --body S` · `pr edit N --remove-label needs-fix --add-label review-passed` |
| `FIX` | `pr comment N --body S` · `pr edit N --remove-label review-round-R --add-label review-round-(R+1) --add-label needs-fix` |
| `RERUN_REVIEW` | `pr comment N --body S` |
| `SCRAP` | `pr comment N --body S` · `pr close N --delete-branch --comment "Scrapped by the review gate. Analysis follows on #I."` · `issue edit I --add-label needs-analysis` |
| `NEEDS_HUMAN` | `pr comment N --body S` · `pr edit N --add-label needs-human` · `issue edit I --add-label needs-human` |
| `REBUILD` | `issue comment I --body S` · `issue edit I --remove-label needs-analysis --remove-label attempt-1 --add-label attempt-2` |
| rebuild `NEEDS_HUMAN` (`--pr 0`) | `issue comment I --body S` · `issue edit I --remove-label needs-analysis --add-label needs-human` |

All `gh` calls get `--repo R`. Removing a label the PR or issue does not
have must not fail the step; check `gh`'s real behaviour once in the drill.
The concurrency group here is per PR; the per-issue group from the spec
belongs to the agent workflows in part 2.

- [ ] **Step 1: Failing tests** — one `apply_<decision>` case per row
  asserting the call list; plus `apply_close_fails`: `STUB_GH_FAIL_ON="pr close"`
  → non-zero exit and **no** `issue edit` logged (analysis must not start
  for a PR that is still open).
- [ ] **Step 2: Run, expect failures.**
- [ ] **Step 3: Implement `apply.sh`** — `set -euo pipefail`, one `case` on
  the decision; `--pr 0` selects the rebuild rows.
- [ ] **Step 4: Run, all pass; shellcheck clean.**
- [ ] **Step 5: Commit** — `factory: carry gate decisions out on GitHub`

### Task 4: Labels, workflow, CI wiring

**Files:**
- Create: `factory/labels.sh`, `.github/workflows/factory-gate.yml`
- Modify: `.github/workflows/ci.yml` (`infra` job)

**Interfaces:**
- Consumes: `gate.sh`, `state.sh`, `apply.sh`.
- Produces: workflow `factory-gate.yml` with triggers `workflow_dispatch`
  and `workflow_call`, inputs `pr` (number), `ci` (`success`|`failure`),
  `findings` (string, JSON), `review_try` (1|2, default 1). Part 2 calls it
  with `workflow_call`.

- [ ] **Step 1: `factory/labels.sh --repo R`** — `gh label create NAME --force
  --color C --description D` for every label in the table above. Test
  `labels_all_created`: stub log contains one `label create` per label (10).
- [ ] **Step 2: Workflow** — one job `gate`:
  - `if: vars.FACTORY_ENABLED == 'true'` on the job **[RF5]**;
  - `concurrency: factory-pr-${{ inputs.pr }}`, `cancel-in-progress: false`;
  - `permissions: contents: write, pull-requests: write, issues: write`;
  - `timeout-minutes: 5`;
  - steps: checkout → `state.sh` (exit 3 → `apply.sh --decision NEEDS_HUMAN
    --summary "Factory state is broken: <error>"` on the PR only, then fail
    the job; `factory=false` → print "Not a factory PR" and stop) → write
    `inputs.findings` to a file via an env var, never inline in `run:` →
    `gate.sh review` → `apply.sh`.
  - Token: `GH_TOKEN: ${{ github.token }}` for now. Part 2 swaps in the App
    token, because labels set with `GITHUB_TOKEN` do not start other
    workflows.
- [ ] **Step 3: CI** — in `ci.yml` `infra` job add
  `shellcheck factory/*.sh factory/test/run.sh factory/test/stubs/*` and
  `bash factory/test/run.sh`.
- [ ] **Step 4: Verify locally** —
  `bash factory/test/run.sh && shellcheck factory/*.sh factory/test/run.sh factory/test/stubs/* && bash infra/test/run.sh`
  Expected: all PASS, no shellcheck output. Lint the workflow with
  `actionlint` if installed; if not, say so (don't install it).
- [ ] **Step 5: Commit** — `factory: review gate workflow and labels`

### Task 5: Docs and drill

**Files:**
- Modify: `docs/ARCHITECTURE.md` (new section "Factory: review gate", and
  "How to stop the factory" moved out of "Target")
- Modify: `.claude/ROADMAP.md`, `.claude/NOTES.md`

- [ ] **Step 1: ARCHITECTURE section** — plain language: the round rules
  with the flow diagram from the spec, the label table, what each script
  does, what happens when a `gh` call fails mid-way (PR left as is, job
  red, rerun the workflow), how to stop (`FACTORY_ENABLED` off, or remove
  `factory` from a PR), how to run a drill.
- [ ] **Step 2: Open PR** with the developer's ok; CI must be green.
- [ ] **Step 3: Drill, after merge, with the developer's ok** (it closes a
  real PR and deletes a branch):
  1. developer sets `FACTORY_ENABLED=true`; run `labels.sh`;
  2. create issue "Drill: review gate" and branch `drill/review-gate` with
     an empty commit; PR with body `Closes #<issue>`, labels `factory`,
     `review-round-1`;
  3. dispatch with two majors → expect comment
     `Round 1 · CI green · critical 0 · major 2 → fix`, label round 2 +
     `needs-fix`;
  4. dispatch with `[]` → `review-passed`;
  5. relabel to round 3, dispatch with one major → PR closed, branch gone,
     issue has `needs-analysis`;
  6. dispatch with invalid findings `review_try=2` on a second drill PR →
     `needs-human`;
  7. set `FACTORY_ENABLED` off, dispatch → job skipped, nothing changes.
  Record results in `NOTES.md`; close the drill issue.
- [ ] **Step 4: Explain-back prompt** — ask the developer to walk through
  what the gate does with `round 3 · critical 0 · major 1` and how to stop
  the factory. Part of the F4 explain-back; not checked off until they can.
