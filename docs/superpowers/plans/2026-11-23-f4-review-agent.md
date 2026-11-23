# F4 part 1 — Review agent (report only) Implementation Plan

**Goal:** After a pull request's checks are green, an independent AI reviewer reads the change and a script posts its critical and major findings on the pull request. Nothing else changes.

**Spec:** GitHub issue #28 (approved by the developer 2026-11-23). Rubric and principles: `docs/superpowers/specs/2026-11-16-review-loop-design.md`.

**Architecture:** A workflow started by a finished CI run (always the copy on `main`). Scripts collect the inputs, one headless Claude Code call in restricted mode produces a JSON list, scripts check its shape and post the comment. The reviewer has read-only file tools and no GitHub token.

**Tech:** bash, `jq`, `gh`, Claude Code CLI (`claude -p`), GitHub Actions.

## Global constraints

- Agents only produce content; scripts act.
- The reviewer's step has exactly one secret in its environment, the Claude token, and no GitHub token.
- The reviewer runs with `--restricted --tools "Read,Grep,Glob" --strict-mcp-config --permission-mode dontAsk --no-session-persistence`.
- The Claude token is a secret of the GitHub environment `factory`, usable from `main` only.
- Workflow logs are public: no token, no full tool output.
- Fork pull requests are never reviewed. Only pull requests opened by the repository owner (or a login listed in `FACTORY_AUTHORS`) are.
- No comments in scripts or workflows.

## Files

| File | Responsibility |
|---|---|
| `factory/review/collect.sh` | Decide whether a pull request is reviewed; write `diff.patch` and `spec.md` |
| `factory/review/rubric.md` | The severity rubric, each rule with an id |
| `factory/review/prompt.md` | The reviewer's instructions |
| `factory/review/findings-schema.json` | Shape of the answer |
| `factory/review/check-findings.sh` | Accept or refuse an answer by shape |
| `factory/review/run-review.sh` | Call the reviewer, at most twice; report outcome and usage |
| `factory/review/comment.sh` | Turn an outcome into the pull request comment |
| `factory/test/review.sh`, `factory/test/stubs/claude` | Tests |
| `.github/workflows/review.yml` | Trigger, limits, the steps in order |
| `.github/workflows/ci.yml` | `infra` job also lints and tests the new scripts |

## Interfaces

- `collect.sh --repo OWNER/NAME --pr N --out DIR` prints `review=yes|no`, `reason=closed|fork|not-owner|docs-only|too-large` (when no), `head=<sha>`, `issue=<n|none>`. Exit 1 only when GitHub cannot be read.
- `check-findings.sh FILE` exits 0 when FILE is `{summary, findings[]}` with every finding `{severity: critical|major, rule: <id from the rubric>, file, line: integer ≥ 1 or null, what, why}`; otherwise 1.
- `run-review.sh --work DIR --code DIR --model M` prints `outcome=findings|unreadable|failed`, `turns=`, `cost=`, `seconds=`, `denials=`; writes `DIR/findings.json` on `findings`.
- `comment.sh --head SHA --model M (--findings FILE | --unreadable | --not-run REASON) [--turns N --cost C --seconds S]` prints the comment.

## Rules pinned by tests

- Not reviewed: closed, fork, author not allowed, documentation only, diff over the size limit. An issue not opened by the owner is not used as the spec.
- An answer with an unknown severity or rule, a missing field, a non-integer line, extra top-level text, or more than 50 findings is refused.
- First answer refused → one more call; second refused → `unreadable`. A failed call is `failed`, not "no findings".
- The comment never turns reviewer text into a mention or HTML; counts match the list; an empty list says so in words.

## Review focus

1. Any way the reviewer's step could run code from the pull request or reach a GitHub token.
2. Any path where a failed or unreadable review shows up as "no findings".
3. Text from the pull request, the issue or the reviewer ending up as shell or as active markdown.
4. The trigger: fork pull requests, a moved head, pull requests not by the owner.
5. Secrets or private data in public logs.

## Order of work

1. Scripts with tests: collect, check-findings, comment, run-review (stand-in for the CLI).
2. Rubric, prompt, schema; one real run on the laptop against a planted defect.
3. Workflow; CI lint and tests.
4. Whole-branch review by a fresh reviewer; fixes test-first.
5. Docs, PR.
6. After merge: environment `factory`, the developer's token, `FACTORY_ENABLED`, the drills from the spec.
