#!/usr/bin/env bash
set -uo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
REVIEW=$(dirname "$HERE")/review
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

PASSED=0
FAILED=0
CASE=""
CASE_OK=1
STATUS=0
OUTPUT=""

begin() {
  CASE=$1
  CASE_OK=1
  CASE_DIR="$WORK/$RANDOM$RANDOM"
  mkdir -p "$CASE_DIR/out" "$CASE_DIR/code"
  : > "$CASE_DIR/stub.log"
  unset STUB_PR_JSON STUB_ISSUE_JSON STUB_GH_FAIL_ON STUB_PR_DIFF STUB_PR_FILES STUB_CLAUDE_OUTPUTS STUB_CLAUDE_EXIT FACTORY_AUTHORS REVIEW_MAX_DIFF_BYTES GH_TOKEN GITHUB_TOKEN CLAUDE_CODE_OAUTH_TOKEN
  export STUB_LOG="$CASE_DIR/stub.log" STUB_CLAUDE_COUNT="$CASE_DIR/claude-count"
}

run() {
  OUTPUT=$(PATH="$HERE/stubs:$PATH" "$@" 2>&1)
  STATUS=$?
}

fail() {
  CASE_OK=0
  echo "  FAIL [$CASE] $1"
}

expect_status() {
  if [ "$STATUS" -ne "$1" ]; then fail "exit status $STATUS, expected $1"; fi
}

expect_output() {
  if ! printf '%s\n' "$OUTPUT" | grep -qxF -- "$1"; then fail "expected output line: $1"; fi
}

expect_in_output() {
  case "$OUTPUT" in
    *"$1"*) ;;
    *) fail "output lacks: $1" ;;
  esac
}

expect_not_in_output() {
  case "$OUTPUT" in
    *"$1"*) fail "output must not contain: $1" ;;
  esac
}

expect_log() {
  if ! grep -qF -- "$1" "$STUB_LOG"; then fail "expected call: $1"; fi
}

expect_no_log() {
  if grep -qF -- "$1" "$STUB_LOG"; then fail "unexpected call: $1"; fi
}

end() {
  if [ "$CASE_OK" -eq 1 ]; then
    PASSED=$((PASSED + 1))
    echo "  ok   $CASE"
  else
    FAILED=$((FAILED + 1))
    printf '%s\n' "$OUTPUT" | while IFS= read -r line; do echo "       | $line"; done
  fi
}

pr_json() {
  local state=${1:-OPEN} cross=${2:-false} author=${3:-owner} body=${4:-Closes #7}
  jq -cn --arg state "$state" --argjson cross "$cross" --arg author "$author" --arg body "$body" \
    '{number: 12, state: $state, isCrossRepository: $cross, author: {login: $author}, headRefOid: "0123456789abcdef0123456789abcdef01234567", title: "Add steps goal", body: $body}'
}

issue_json() {
  local author=${1:-owner}
  jq -cn --arg author "$author" '{number: 7, title: "Steps goal", body: "The user sets a daily goal.", author: {login: $author}}'
}

pull_request() {
  STUB_PR_JSON=$(pr_json "$@")
  STUB_ISSUE_JSON=$(issue_json)
  printf 'src/main/java/A.java\nfrontend/src/B.tsx\n' > "$CASE_DIR/files"
  printf 'diff --git a/src/main/java/A.java b/src/main/java/A.java\n+class A {}\n' > "$CASE_DIR/diff"
  export STUB_PR_JSON STUB_ISSUE_JSON STUB_PR_FILES="$CASE_DIR/files" STUB_PR_DIFF="$CASE_DIR/diff"
}

collect() {
  run "$REVIEW/collect.sh" --repo owner/app --pr 12 --out "$CASE_DIR/out"
}

echo "collect.sh"

begin "an open pull request by the owner is reviewed, with its diff and its issue as the spec"
pull_request
collect
expect_status 0
expect_output "review=yes"
expect_output "head=0123456789abcdef0123456789abcdef01234567"
expect_output "issue=7"
if ! grep -q '^+class A {}$' "$CASE_DIR/out/diff.patch"; then fail "diff.patch lacks the diff"; fi
if ! grep -q 'Add steps goal' "$CASE_DIR/out/spec.md"; then fail "spec.md lacks the pull request title"; fi
if ! grep -q 'The user sets a daily goal.' "$CASE_DIR/out/spec.md"; then fail "spec.md lacks the issue text"; fi
end

begin "a closed pull request is not reviewed"
pull_request MERGED
collect
expect_status 0
expect_output "review=no"
expect_output "reason=closed"
end

begin "a pull request from a fork is not reviewed and its diff is not fetched"
pull_request OPEN true
collect
expect_output "review=no"
expect_output "reason=fork"
expect_no_log "gh pr diff"
end

begin "a pull request opened by someone else is not reviewed"
pull_request OPEN false stranger
collect
expect_output "review=no"
expect_output "reason=not-owner"
expect_no_log "gh pr diff"
end

begin "a login listed in FACTORY_AUTHORS is reviewed too"
pull_request OPEN false "factory-bot[bot]"
export FACTORY_AUTHORS="someone,factory-bot[bot]"
collect
expect_output "review=yes"
end

begin "a pull request that changes only documentation is not reviewed"
pull_request
printf 'docs/ARCHITECTURE.md\n.claude/NOTES.md\nREADME.md\nfrontend/README.md\n' > "$CASE_DIR/files"
collect
expect_output "review=no"
expect_output "reason=docs-only"
end

begin "documentation next to code is reviewed"
pull_request
printf 'docs/ARCHITECTURE.md\ninfra/deploy.sh\n' > "$CASE_DIR/files"
collect
expect_output "review=yes"
end

begin "a file that only looks like documentation is code"
pull_request
printf 'docs/../infra/deploy.sh\n' > "$CASE_DIR/files"
collect
expect_output "review=yes"
end

begin "a diff over the size limit is not reviewed"
pull_request
export REVIEW_MAX_DIFF_BYTES=10
collect
expect_output "review=no"
expect_output "reason=too-large"
end

begin "a pull request that names no issue has no issue spec"
pull_request OPEN false owner "Small fix, no issue."
collect
expect_output "review=yes"
expect_output "issue=none"
expect_no_log "gh issue view"
if ! grep -q 'names no issue' "$CASE_DIR/out/spec.md"; then fail "spec.md does not say that no issue is named"; fi
end

begin "an issue opened by someone else is not used as the spec"
pull_request
STUB_ISSUE_JSON=$(issue_json stranger)
export STUB_ISSUE_JSON
collect
expect_output "review=yes"
expect_output "issue=none"
if grep -q 'The user sets a daily goal.' "$CASE_DIR/out/spec.md"; then fail "a stranger's issue text reached spec.md"; fi
end

for BODY in "Part of #7: the code." "Implements #7" "fixes #7" "Resolves #7."; do
  begin "issue reference form: $BODY"
  pull_request OPEN false owner "$BODY"
  collect
  expect_output "issue=7"
  end
done

begin "GitHub not answering is an error, not a decision"
pull_request
export STUB_GH_FAIL_ON="pr view"
collect
expect_status 1
expect_not_in_output "review="
end

echo "check-findings.sh"

answer() {
  local file="$CASE_DIR/answer-$RANDOM.json"
  printf '%s' "$1" > "$file"
  echo "$file"
}

GOOD='{"summary":"Read the change and its tests.","findings":[{"severity":"critical","rule":"security","file":"src/main/java/A.java","line":12,"what":"One user can read another user'"'"'s entries.","why":"The query does not filter by owner."},{"severity":"major","rule":"no-test","file":"src/main/java/B.java","line":null,"what":"New behaviour has no test.","why":"Nothing exercises the new branch."}]}'

begin "a well-formed answer is accepted"
run "$REVIEW/check-findings.sh" "$(answer "$GOOD")"
expect_status 0
end

begin "an answer with no findings is accepted"
run "$REVIEW/check-findings.sh" "$(answer '{"summary":"Nothing found.","findings":[]}')"
expect_status 0
end

for BAD in \
  '' \
  'not json' \
  '[]' \
  '{"findings":[]}' \
  '{"summary":"x"}' \
  '{"summary":"x","findings":"none"}' \
  '{"summary":"x","findings":[{"severity":"minor","rule":"no-test","file":"a","line":1,"what":"w","why":"y"}]}' \
  '{"summary":"x","findings":[{"severity":"major","rule":"made-up-rule","file":"a","line":1,"what":"w","why":"y"}]}' \
  '{"summary":"x","findings":[{"severity":"major","rule":"no-test","file":"a","line":"12","what":"w","why":"y"}]}' \
  '{"summary":"x","findings":[{"severity":"major","rule":"no-test","file":"a","line":0,"what":"w","why":"y"}]}' \
  '{"summary":"x","findings":[{"severity":"major","rule":"no-test","file":"a","line":1.5,"what":"w","why":"y"}]}' \
  '{"summary":"x","findings":[{"severity":"major","rule":"no-test","line":1,"what":"w","why":"y"}]}' \
  '{"summary":"x","findings":[{"severity":"major","rule":"no-test","file":"","line":1,"what":"w","why":"y"}]}' \
  '{"summary":"x","findings":[{"severity":"major","rule":"no-test","file":"a","line":1,"what":"","why":"y"}]}' \
  '{"summary":"x","findings":[{"severity":"major","rule":"no-test","file":"a","line":1,"what":"w","why":"y","extra":true}]}' \
  '{"summary":"x","findings":[],"note":"extra"}' \
  '{"summary":5,"findings":[]}' \
  '{"summary":"x","findings":[null]}'; do
  begin "refused answer: ${BAD:0:60}"
  run "$REVIEW/check-findings.sh" "$(answer "$BAD")"
  expect_status 1
  end
done

begin "more than fifty findings are refused"
MANY=$(jq -cn '{summary: "x", findings: [range(51) | {severity: "major", rule: "no-test", file: "a", line: 1, what: "w", why: "y"}]}')
run "$REVIEW/check-findings.sh" "$(answer "$MANY")"
expect_status 1
end

begin "a missing file is refused"
run "$REVIEW/check-findings.sh" "$CASE_DIR/no-such-file.json"
expect_status 1
end

begin "the refusal does not repeat the answer"
run "$REVIEW/check-findings.sh" "$(answer '{"summary":"x","findings":[{"severity":"minor","rule":"no-test","file":"SECRETFILE","line":1,"what":"SECRETWHAT","why":"y"}]}')"
expect_not_in_output "SECRET"
end

echo "run-review.sh"

result() {
  local file="$CASE_DIR/result-$RANDOM.json"
  jq -cn --argjson structured "$1" '{type: "result", subtype: "success", is_error: false, result: "done", num_turns: 7, duration_ms: 41200, total_cost_usd: 0.4312, permission_denials: [], structured_output: $structured}' > "$file"
  echo "$file"
}

review() {
  mkdir -p "$CASE_DIR/work/inputs"
  run "$REVIEW/run-review.sh" --work "$CASE_DIR/work" --code "$CASE_DIR/code" --model test-model
}

begin "a readable answer is one call and becomes findings.json"
STUB_CLAUDE_OUTPUTS="$(result "$GOOD")"
export STUB_CLAUDE_OUTPUTS
review
expect_status 0
expect_output "outcome=findings"
expect_output "turns=7"
expect_output "cost=0.43"
expect_output "seconds=41"
expect_output "denials=0"
if [ "$(cat "$STUB_CLAUDE_COUNT")" != "1" ]; then fail "expected exactly one call"; fi
if [ "$(jq -r '.findings | length' "$CASE_DIR/work/findings.json" 2>/dev/null)" != "2" ]; then fail "findings.json does not hold the two findings"; fi
end

begin "the reviewer is called restricted, read-only, in the work directory"
STUB_CLAUDE_OUTPUTS="$(result "$GOOD")"
export STUB_CLAUDE_OUTPUTS
review
for flag in "-p" "--restricted" "--tools Read,Grep,Glob" "--strict-mcp-config" "--permission-mode dontAsk" "--no-session-persistence" "--output-format json" "--json-schema" "--model test-model" "--add-dir $CASE_DIR/code" "--max-budget-usd"; do
  expect_log "$flag"
done
expect_log "cwd=work"
expect_no_log "Bash"
expect_no_log "bypassPermissions"
end

begin "the reviewer never sees a GitHub token"
STUB_CLAUDE_OUTPUTS="$(result "$GOOD")"
export STUB_CLAUDE_OUTPUTS GH_TOKEN=ghs_secret GITHUB_TOKEN=ghs_secret CLAUDE_CODE_OAUTH_TOKEN=claude-secret
review
expect_log "claude-env GH_TOKEN=unset GITHUB_TOKEN=unset token=set"
expect_not_in_output "ghs_secret"
expect_not_in_output "claude-secret"
end

begin "an unreadable first answer is asked for once more"
STUB_CLAUDE_OUTPUTS="$(result '{"summary":"x","findings":"none"}'):$(result "$GOOD")"
export STUB_CLAUDE_OUTPUTS
review
expect_status 0
expect_output "outcome=findings"
if [ "$(cat "$STUB_CLAUDE_COUNT")" != "2" ]; then fail "expected exactly two calls"; fi
end

begin "two unreadable answers are unreadable, never no findings"
STUB_CLAUDE_OUTPUTS="$(result '{"summary":"x","findings":"none"}'):$(result 'null')"
export STUB_CLAUDE_OUTPUTS
review
expect_status 0
expect_output "outcome=unreadable"
if [ "$(cat "$STUB_CLAUDE_COUNT")" != "2" ]; then fail "expected exactly two calls"; fi
if [ -e "$CASE_DIR/work/findings.json" ]; then fail "findings.json exists for an unreadable review"; fi
end

begin "a call that fails is failed, never no findings"
export STUB_CLAUDE_OUTPUTS="" STUB_CLAUDE_EXIT=1
review
expect_status 0
expect_output "outcome=failed"
if [ -e "$CASE_DIR/work/findings.json" ]; then fail "findings.json exists for a failed review"; fi
end

begin "a call that reports an error is failed"
ERR="$CASE_DIR/err.json"
jq -cn '{type: "result", subtype: "error_max_budget", is_error: true, num_turns: 30, duration_ms: 1000, total_cost_usd: 3.1, permission_denials: []}' > "$ERR"
export STUB_CLAUDE_OUTPUTS="$ERR"
review
expect_output "outcome=failed"
end

begin "tool calls the reviewer was refused are counted"
DENIED="$CASE_DIR/denied.json"
jq -cn --argjson structured "$GOOD" '{type: "result", subtype: "success", is_error: false, num_turns: 3, duration_ms: 1000, total_cost_usd: 0.1, permission_denials: [{tool_name: "Bash"}, {tool_name: "Write"}], structured_output: $structured}' > "$DENIED"
export STUB_CLAUDE_OUTPUTS="$DENIED"
review
expect_output "denials=2"
end

echo "comment.sh"

SHA=0123456789abcdef0123456789abcdef01234567

begin "findings are listed with counts, rule, file and line"
run "$REVIEW/comment.sh" --head "$SHA" --model test-model --findings "$(answer "$GOOD")" --turns 7 --cost 0.43 --seconds 41
expect_status 0
expect_in_output "<!-- factory-review -->"
expect_in_output "1 critical, 1 major"
expect_in_output "0123456"
expect_in_output "\`src/main/java/A.java:12\`"
expect_in_output "\`src/main/java/B.java\`"
expect_in_output "security"
expect_in_output "One user can read another user's entries."
expect_in_output "test-model"
expect_in_output "7 turns"
expect_in_output "Report only"
end

begin "no findings is said in words"
run "$REVIEW/comment.sh" --head "$SHA" --model test-model --findings "$(answer '{"summary":"Read everything.","findings":[]}')"
expect_in_output "No critical or major findings"
expect_in_output "Read everything."
end

begin "reviewer text cannot mention people, add HTML or break out of its place"
EVIL=$(jq -cn '{summary: "ping @someone <img src=x onerror=alert(1)>", findings: [{severity: "major", rule: "no-test", file: "a`b.java", line: 3, what: "@team please <script>x</script>\n# Heading\n- item", why: "see [link](http://example.test) <!-- hidden -->"}]}')
run "$REVIEW/comment.sh" --head "$SHA" --model test-model --findings "$(answer "$EVIL")"
expect_status 0
expect_not_in_output "@someone"
expect_not_in_output "@team"
expect_not_in_output "<img"
expect_not_in_output "<script>"
expect_not_in_output "<!-- hidden"
if printf '%s\n' "$OUTPUT" | grep -q '^# Heading'; then fail "reviewer text became a heading"; fi
end

begin "an unreadable review says so and reports nothing as clean"
run "$REVIEW/comment.sh" --head "$SHA" --model test-model --unreadable
expect_in_output "could not be read"
expect_not_in_output "No critical or major findings"
end

for REASON in failed too-large; do
  begin "a review that did not run says why: $REASON"
  run "$REVIEW/comment.sh" --head "$SHA" --model test-model --not-run "$REASON"
  expect_status 0
  expect_in_output "did not run"
  expect_not_in_output "No critical or major findings"
  end
done

begin "an unknown reason is refused"
run "$REVIEW/comment.sh" --head "$SHA" --model test-model --not-run 'whatever; id'
expect_status 2
end

echo "$PASSED passed, $FAILED failed"
[ "$FAILED" -eq 0 ]
