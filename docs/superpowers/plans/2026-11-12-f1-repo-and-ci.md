# F1 — Repo and CI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The project lives in a private GitHub repo, and every pull request is automatically built and tested (backend and frontend) before it can be considered for merge.

**Architecture:** One GitHub Actions workflow, `ci.yml`, with two independent jobs — `backend` (Gradle build + JUnit suite, Testcontainers Postgres on the runner's Docker) and `frontend` (yarn install, lint, type-check + build). It runs on every pull request and on every push to `main`. No deploy, no agents yet — those are F2 and F4.

**Tech Stack:** GitHub (private repo, `gh` CLI), GitHub Actions, Gradle 9.5.1 wrapper, Java 26 (Temurin), Spring Boot 4.1.0, Testcontainers, Node 22, Yarn 1.22 (classic), oxlint, Vite 8.

**Spec:** `docs/superpowers/specs/2026-11-12-ai-factory-pivot-design.md`

## Global Constraints

- Bare minimum only — no tool, service, or spend that F1 does not need.
- Budget €20/month covers VPS, backups, domain, app OpenRouter calls. F1 must cost €0.
- GitHub plan assumed free. Private-repo Actions minutes are limited: cache Gradle and yarn, cancel superseded runs.
- Secrets never go into git — not in code, config, workflows, or docs.
- Nothing pushes to `main` directly once CI exists; changes arrive by PR.
- `docs/ARCHITECTURE.md` is updated in the same change as any infra change.
- The existing test suite stays green (115 tests as of 2026-11-05).
- Phase is not checked off until the developer can explain it back.
- Creating the GitHub repo and the first push are outward-facing: the executor asks the developer for an explicit "ok" at Task 2 and does not proceed without it.
- No comments in code or workflow files.

## Review Focus

1. **A secret already in git history** (API key, real password, real JWT secret in `http/*.http` or old properties). Expected: found and dealt with before the first push, not after. → Task 1, Step 2.
2. **A red test must make the PR red.** A CI that is green no matter what is worse than none. Expected: a deliberately failing test turns the `backend` check red. → Task 3, Step 6.
3. **A lint or type error in the frontend must make the PR red.** Expected: a deliberate TypeScript error turns the `frontend` check red. → Task 3, Step 6.
4. **Testcontainers on the runner.** The suite needs Docker; if the runner cannot start Postgres the failure must be loud, not a skipped test. Expected: the CI log shows the Postgres container starting and the test count matches the local run. → Task 3, Step 5.
5. **Local-only files leaking into the repo** (`.claude/settings.local.json`, `storage/`, `test_storage/`, `.idea/`, `frontend/dist`). Expected: none of them tracked. → Task 1, Step 1 and Step 4.

---

## File Structure

| File | Responsibility |
|---|---|
| `.gitignore` (modify) | Keep local-only files out of the repo |
| `.github/workflows/ci.yml` (create) | The whole CI: `backend` and `frontend` jobs |
| `docs/ARCHITECTURE.md` (modify) | "Path of a change" section; today's table gains the repo and CI |
| `.claude/ROADMAP.md` (modify) | F0/F1 check-offs |
| `.claude/DECISIONS.md`, `.claude/NOTES.md` (append) | Calls made during F1 |

No application source changes in this phase.

---

### Task 1: Make the repo safe to publish

**Files:**
- Modify: `.gitignore`
- Commit: `CLAUDE.md`, `docs/`, `.claude/` (minus local settings)

**Interfaces:**
- Consumes: nothing.
- Produces: a clean working tree on branch `main`, with the pivot docs committed. Task 2 pushes exactly this.

- [ ] **Step 1: Ignore local-only files**

Append to `.gitignore`:

```
.claude/settings.local.json
.DS_Store
```

Verify the already-ignored ones still are:

Run: `git check-ignore -v storage test_storage .idea build .claude/settings.local.json frontend/dist frontend/node_modules`
Expected: one line per path, each naming the rule that ignores it. Any path missing from the output is a failure — add a rule for it.

- [ ] **Step 2: Scan history for secrets**

Run each; expected output for all three is empty or contains only obvious placeholders (`tracker`/`tracker` local DB password, `test-key`, the JWT secret in `src/test/resources/application-test.properties`):

```bash
git log --all -p | grep -inE "sk-or-|sk-ant-|ghp_|github_pat_|BEGIN (RSA|OPENSSH|EC) PRIVATE KEY"
git log --all -p -- 'http/*' | grep -inE "password|token|bearer|authorization"
git log --all -p -- '*.properties' '*.yml' '*.yaml' '*.env' | grep -inE "secret|api[-_]?key|password" | grep -v '\${'
```

If anything real turns up: stop, show the developer the file and commit (not the value), and agree whether to rotate the secret or rewrite history. Do not push until resolved.

- [ ] **Step 3: Rename the branch**

Run: `git branch -m master main`
Expected: `git branch --show-current` prints `main`.

- [ ] **Step 4: Commit the pivot docs**

```bash
git add .gitignore CLAUDE.md docs .claude
git status --short
```

Expected: only `.gitignore`, `CLAUDE.md`, files under `docs/`, and `.claude/{ROADMAP,SPEC,DECISIONS,NOTES}.md` + `.claude/archive/` are staged. `.claude/settings.local.json` is NOT listed. Then:

```bash
git commit -m "pivot to AI factory: design, instructions, roadmap, prototype"
```

- [ ] **Step 5: Confirm the suite is green before anything goes public**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL`, no failed tests. Note the test count from `build/reports/tests/test/index.html` — Task 3 compares CI against it.

Run: `cd frontend && yarn install --frozen-lockfile && yarn lint && yarn build`
Expected: lint exits 0 (warnings allowed, errors not), build writes `frontend/dist`.

---

### Task 2: Create the private GitHub repo and push

**Files:** none.

**Interfaces:**
- Consumes: branch `main` from Task 1.
- Produces: remote `origin` → `github.com/<owner>/activity-tracker`, private, default branch `main`. Task 3 opens PRs against it.

- [ ] **Step 1: Ask the developer — outward-facing action**

Ask and wait for an explicit answer:
- Which GitHub account owns the repo? (`gh` is logged in as one account; confirm it is the personal one meant for this project, not a work org.)
- Repo name — default `activity-tracker`.
- Confirm: private.

- [ ] **Step 2: Create and push**

```bash
gh repo create <owner>/activity-tracker --private --source=. --remote=origin --push
```

Expected: prints the repo URL; `git remote -v` shows `origin`.

- [ ] **Step 3: Verify what actually landed**

```bash
gh repo view --json visibility,defaultBranchRef --jq '.visibility, .defaultBranchRef.name'
gh api repos/{owner}/{repo}/contents/.claude --jq '.[].name'
```

Expected: `PRIVATE`, `main`; the `.claude` listing shows the four docs and `archive`, and does not show `settings.local.json`.

---

### Task 3: CI workflow

**Files:**
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: `origin` from Task 2.
- Produces: two status checks named exactly `backend` and `frontend`. F2's deploy workflow and any merge rule refer to these names.

- [ ] **Step 1: Work on a branch**

Run: `git switch -c ci`

- [ ] **Step 2: Write the workflow**

`.github/workflows/ci.yml`, with these decisions fixed:

- `name: CI`
- Triggers: `pull_request` (all branches) and `push` to `main`.
- `concurrency: { group: ci-${{ github.ref }}, cancel-in-progress: true }` — a newer push cancels the older run.
- `permissions: { contents: read }`.
- Job `backend` — `runs-on: ubuntu-latest`, `timeout-minutes: 15`:
  1. checkout
  2. `actions/setup-java` — `distribution: temurin`, `java-version: 26`
  3. `gradle/actions/setup-gradle` (handles Gradle caching)
  4. `./gradlew build` (runs compile + the full test suite)
  5. on failure only: upload `build/reports/tests/test` as artifact `backend-test-report`, `retention-days: 7`
- Job `frontend` — `runs-on: ubuntu-latest`, `timeout-minutes: 10`, `defaults.run.working-directory: frontend`:
  1. checkout
  2. `actions/setup-node` — `node-version: 22`, `cache: yarn`, `cache-dependency-path: frontend/yarn.lock`
  3. `yarn install --frozen-lockfile`
  4. `yarn lint`
  5. `yarn build`
- Action versions: use the current major of each action at execution time (check with `gh api repos/actions/checkout/releases/latest --jq .tag_name`, same for `actions/setup-java`, `actions/setup-node`, `actions/upload-artifact`, `gradle/actions`).
- No secrets, no env vars: the `test` profile already supplies `jwt.secret`, `storage.root-path`, and `openrouter.*`.

- [ ] **Step 3: Validate the YAML before pushing**

Run: `python3 -c "import yaml,sys; d=yaml.safe_load(open('.github/workflows/ci.yml')); print(sorted(d['jobs']))"`
Expected: `['backend', 'frontend']`

- [ ] **Step 4: Open the PR**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: build and test backend and frontend on every PR"
git push -u origin ci
gh pr create --base main --head ci --title "CI: build and test on every PR" --body "Adds the CI workflow. See docs/superpowers/plans/2026-11-12-f1-repo-and-ci.md."
```

- [ ] **Step 5: Verify green, and that the tests really ran**

Run: `gh pr checks --watch`
Expected: `backend` pass, `frontend` pass.

Run: `gh run view --log --job <backend job id> | grep -iE "tests completed|BUILD SUCCESSFUL|Container postgres"`
Expected: `BUILD SUCCESSFUL`, and evidence the Postgres container started. Compare the test count with the local number from Task 1 Step 5 (download the report with `gh run download` if the log does not show it; temporarily make the upload step unconditional for this one check if needed, then revert). A lower count than local means tests were skipped — that is a failure of this task.

If Temurin 26 is not available to `setup-java`, stop and report; do not silently lower the Java version.

- [ ] **Step 6: Negative controls — prove red is possible**

Backend: on a throwaway branch `ci-red-backend` off `ci`, add a test `src/test/java/dev/eugenlinde/activitytracker/CiRedProbeTest.java` (match the real base package — check `src/test/java` first) with one test that calls `org.junit.jupiter.api.Assertions.fail("ci negative control")`. Push, open a draft PR against `ci`.
Expected: `backend` check FAILS, `frontend` passes, the `backend-test-report` artifact exists.

Frontend: on a throwaway branch `ci-red-frontend` off `ci`, add `frontend/src/ciRedProbe.ts` containing `export const probe: number = 'not a number'`. Push, open a draft PR against `ci`.
Expected: `frontend` check FAILS at `yarn build` (tsc), `backend` passes.

Then close both PRs and delete both branches (`gh pr close <n> --delete-branch`). Confirm with `git ls-remote --heads origin` that only `main` and `ci` remain.

- [ ] **Step 7: Hand the PR to the developer**

Do not merge. Tell the developer the PR is ready, with the two green checks and the two red-probe results as evidence. The developer merges.

---

### Task 4: Decide how `main` is protected

**Files:**
- Append: `.claude/DECISIONS.md`

**Interfaces:**
- Consumes: check names `backend`, `frontend` from Task 3.
- Produces: a recorded decision F2 relies on when it wires deploy-on-merge.

- [ ] **Step 1: Find out what the plan allows**

Run:

```bash
gh api -X PUT repos/{owner}/{repo}/branches/main/protection \
  -f 'required_status_checks[strict]=true' \
  -f 'required_status_checks[contexts][]=backend' \
  -f 'required_status_checks[contexts][]=frontend' \
  -F 'enforce_admins=false' \
  -F 'required_pull_request_reviews=null' \
  -F 'restrictions=null'
```

Expected, one of:
- HTTP 200 → protection is on. Verify: `gh api repos/{owner}/{repo}/branches/main/protection --jq '.required_status_checks.contexts'` prints `["backend","frontend"]`. Done; record it.
- HTTP 403 mentioning an upgrade → the free plan does not offer branch protection on private repos. Go to Step 2.

- [ ] **Step 2: If protection is unavailable, put the choice to the developer**

It is their decision (money or visibility). Present exactly these, with the default first:

1. **Convention + pipeline gate (default, €0):** nobody pushes to `main` by rule (`CLAUDE.md` already says so), and F2's deploy job only runs after `backend` and `frontend` pass in the same run. A bad push to `main` cannot reach prod, but GitHub will not physically block the push.
2. **Make the repo public (€0):** branch protection and unlimited Actions minutes; code and issues visible to everyone.
3. **GitHub Pro (about $4/month):** protection on a private repo; outside "bare minimum".

- [ ] **Step 3: Record the outcome**

Append to `.claude/DECISIONS.md` under a `## <date> — Protecting main` heading: what the API returned, which option was chosen, by whom.

---

### Task 5: Document, explain, check off

**Files:**
- Modify: `docs/ARCHITECTURE.md`
- Modify: `.claude/ROADMAP.md`
- Append: `.claude/NOTES.md`

**Interfaces:**
- Consumes: everything above.
- Produces: F1 closed; F2 can start.

- [ ] **Step 1: Update `docs/ARCHITECTURE.md`**

- "Last updated" line → today, "repo and CI".
- "What exists today" table: add rows `Code | private GitHub repo <owner>/activity-tracker, default branch main | GitHub` and `CI | .github/workflows/ci.yml — jobs backend, frontend, on every PR and push to main | GitHub Actions runners`.
- Replace "How a change reaches users today" with "Path of a change": branch → PR → `backend` + `frontend` checks → developer merges → (nothing deploys yet). One sentence each on what the two jobs do and what a red check means (the change broke a test, a type, or a lint rule — look at the job log; for backend, the `backend-test-report` artifact).
- Add a short "What CI costs" note: free-plan minutes, what consumes them, where to see usage (repo → Insights → Actions, or account billing).
- State how `main` is protected, per Task 4.

- [ ] **Step 2: Commit the docs on the `ci` branch (or a follow-up PR if `ci` is merged)**

```bash
git add docs/ARCHITECTURE.md .claude
git commit -m "docs: architecture and roadmap after F1"
git push
```

- [ ] **Step 3: Explain-back checkpoint**

Ask the developer, one at a time, and listen for their own words:
1. "You open a PR. What happens in the next five minutes, and where?"
2. "The `backend` check is red. What are the possible reasons, and where do you look first?"
3. "Why does the test suite need Docker on the CI machine?"
4. "What stops a broken change from landing on `main` today — and what does not?"

If an answer is off, explain again more simply or fix the docs, then re-ask. Only then:

- [ ] **Step 4: Check off F1**

In `.claude/ROADMAP.md`, tick the F1 items that are true (leave "Developer: check the Claude Code subscription" open unless they have reported back) and mark F2 as current. Append a dated F1 entry to `.claude/NOTES.md`: what was built, the negative-control results, the explain-back outcome, open items.

---

## Not in this plan

- The developer's own to-do, in parallel: check whether the Claude Code subscription allows unattended runs in GitHub Actions (decides plan A vs. B in F4), and apply for Garmin developer-program access.
- Dockerfiles, deploy, VPS, secrets, closed signup — F2.
- Agents in Actions — F4.
