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
  mkdir -p "$CASE_DIR/out" "$CASE_DIR/code" "$CASE_DIR/work/inputs" "$CASE_DIR/result"
  : > "$CASE_DIR/stub.log"
  unset STUB_PR_JSON STUB_ISSUE_JSON STUB_GH_FAIL_ON STUB_PR_DIFF STUB_PR_FILES STUB_CLAUDE_OUTPUTS STUB_CLAUDE_EXIT STUB_CODE_DIR FACTORY_AUTHORS REVIEW_MAX_DIFF_BYTES GH_TOKEN GITHUB_TOKEN CLAUDE_CODE_OAUTH_TOKEN SOME_OTHER_SECRET
  export STUB_LOG="$CASE_DIR/stub.log" STUB_CLAUDE_COUNT="$CASE_DIR/claude-count"
  export REVIEW_KEEP_ENV="STUB_LOG STUB_CLAUDE_COUNT STUB_CLAUDE_OUTPUTS STUB_CLAUDE_EXIT STUB_CODE_DIR"
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

expect_log_line() {
  if ! grep -qxF -- "$1" "$STUB_LOG"; then fail "expected log line: $1"; fi
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
  printf 'src/main/java/A.java\t\nfrontend/src/B.tsx\t\n' > "$CASE_DIR/files"
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
expect_no_log "gh api"
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
printf 'docs/ARCHITECTURE.md\t\ndocs/superpowers/plans/x.md\t\n.claude/NOTES.md\t\n.claude/DECISIONS.md\t\nREADME.md\t\nfrontend/README.md\t\n' > "$CASE_DIR/files"
collect
expect_output "review=no"
expect_output "reason=docs-only"
expect_no_log "gh pr diff"
end

begin "documentation next to code is reviewed"
pull_request
printf 'docs/ARCHITECTURE.md\t\ninfra/deploy.sh\t\n' > "$CASE_DIR/files"
collect
expect_output "review=yes"
end

for PATH_IN_PR in 'CLAUDE.md' 'factory/review/prompt.md' 'factory/review/rubric.md' '.claude/settings.json' '.claude/hooks/pre.sh' \
  'docs/run.sh' '.github/workflows/notes.md' 'docs/../infra/deploy.sh' 'src/main/resources/notes.md' '.claude/CLAUDE.md'; do
  begin "not documentation: $PATH_IN_PR"
  pull_request
  printf '%s\t\n' "$PATH_IN_PR" > "$CASE_DIR/files"
  collect
  expect_output "review=yes"
  end
done

begin "a file moved into docs is judged by where it came from too"
pull_request
printf 'docs/V7__weights.md\tsrc/main/resources/db/changelog/V7__weights.xml\n' > "$CASE_DIR/files"
collect
expect_output "review=yes"
end

begin "the list of changed files not arriving is a review that did not run"
pull_request
export STUB_GH_FAIL_ON="api"
collect
expect_status 0
expect_output "review=no"
expect_output "reason=failed"
expect_output "head=0123456789abcdef0123456789abcdef01234567"
end

begin "the diff not arriving is a review that did not run"
pull_request
export STUB_GH_FAIL_ON="pr diff"
collect
expect_status 0
expect_output "review=no"
expect_output "reason=failed"
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

for BODY in "Part of #7: the code." "Implements #7" "fixes #7" "Resolves #7." "Text first. Closes #7"; do
  begin "issue reference form: $BODY"
  pull_request OPEN false owner "$BODY"
  collect
  expect_output "issue=7"
  end
done

for BODY in "see the prefix #7" "leaves unresolved #7 alone" "disclose #7" "fixes #12345678" "fixes other/repo#7" "no number here"; do
  begin "not an issue reference: $BODY"
  pull_request OPEN false owner "$BODY"
  collect
  expect_output "review=yes"
  expect_output "issue=none"
  end
done

begin "an issue that cannot be read is no spec, not a failed review"
pull_request
export STUB_GH_FAIL_ON="issue view"
collect
expect_status 0
expect_output "review=yes"
expect_output "issue=none"
end

begin "every decision names the commit"
pull_request MERGED
collect
expect_output "head=0123456789abcdef0123456789abcdef01234567"
end

begin "FACTORY_AUTHORS may be written with spaces"
pull_request OPEN false "second"
export FACTORY_AUTHORS="first, second"
collect
expect_output "review=yes"
end

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

GOOD='{"reviewed":true,"summary":"Read the change and its tests.","findings":[{"severity":"critical","rule":"security","file":"src/main/java/A.java","line":12,"what":"One user can read another user'"'"'s entries.","why":"The query does not filter by owner."},{"severity":"major","rule":"no-test","file":"src/main/java/B.java","line":null,"what":"New behaviour has no test.","why":"Nothing exercises the new branch."}]}'

begin "a well-formed answer is accepted"
run "$REVIEW/check-findings.sh" "$(answer "$GOOD")"
expect_status 0
end

begin "an answer with no findings is accepted"
run "$REVIEW/check-findings.sh" "$(answer '{"reviewed":true,"summary":"Nothing found.","findings":[]}')"
expect_status 0
end

F='"severity":"major","rule":"no-test","file":"a","line":1,"what":"w","why":"y"'
for BAD in \
  '' \
  'not json' \
  '[]' \
  '{"reviewed":true,"findings":[]}' \
  '{"reviewed":true,"summary":"x"}' \
  '{"summary":"x","findings":[]}' \
  '{"reviewed":false,"summary":"I could not open the diff.","findings":[]}' \
  '{"reviewed":"true","summary":"x","findings":[]}' \
  '{"reviewed":true,"summary":"x","findings":"none"}' \
  '{"reviewed":true,"summary":"x","findings":[{"severity":"minor","rule":"no-test","file":"a","line":1,"what":"w","why":"y"}]}' \
  '{"reviewed":true,"summary":"x","findings":[{"severity":"major","rule":"made-up-rule","file":"a","line":1,"what":"w","why":"y"}]}' \
  '{"reviewed":true,"summary":"x","findings":[{"severity":"major","rule":["no-test"],"file":"a","line":1,"what":"w","why":"y"}]}' \
  '{"reviewed":true,"summary":"x","findings":[{"severity":"critical","rule":"no-test","file":"a","line":1,"what":"w","why":"y"}]}' \
  '{"reviewed":true,"summary":"x","findings":[{"severity":"major","rule":"security","file":"a","line":1,"what":"w","why":"y"}]}' \
  '{"reviewed":true,"summary":"x","findings":[{"severity":"major","rule":"no-test","file":"a","line":"12","what":"w","why":"y"}]}' \
  '{"reviewed":true,"summary":"x","findings":[{"severity":"major","rule":"no-test","file":"a","line":0,"what":"w","why":"y"}]}' \
  '{"reviewed":true,"summary":"x","findings":[{"severity":"major","rule":"no-test","file":"a","line":1.5,"what":"w","why":"y"}]}' \
  '{"reviewed":true,"summary":"x","findings":[{"severity":"major","rule":"no-test","file":"a","line":1e999,"what":"w","why":"y"}]}' \
  '{"reviewed":true,"summary":"x","findings":[{"severity":"major","rule":"no-test","line":1,"what":"w","why":"y"}]}' \
  '{"reviewed":true,"summary":"x","findings":[{"severity":"major","rule":"no-test","file":"","line":1,"what":"w","why":"y"}]}' \
  '{"reviewed":true,"summary":"x","findings":[{"severity":"major","rule":"no-test","file":"a","line":1,"what":"   ","why":"y"}]}' \
  '{"reviewed":true,"summary":"x","findings":[{"severity":"major","rule":"no-test","file":"a","line":1,"what":"w","why":"y","extra":true}]}' \
  '{"reviewed":true,"summary":"x","findings":[],"note":"extra"}' \
  '{"reviewed":true,"summary":5,"findings":[]}' \
  '{"reviewed":true,"summary":"x","findings":[null]}' \
  "false {\"reviewed\":true,\"summary\":\"x\",\"findings\":[]}" \
  "{\"reviewed\":true,\"summary\":\"x\",\"findings\":[]} {\"reviewed\":true,\"summary\":\"x\",\"findings\":[{$F}]}"; do
  begin "refused answer: ${BAD:0:70}"
  run "$REVIEW/check-findings.sh" "$(answer "$BAD")"
  expect_status 1
  end
done

begin "a whole-number line written with a decimal point is refused"
run "$REVIEW/check-findings.sh" "$(answer '{"reviewed":true,"summary":"x","findings":[{"severity":"major","rule":"no-test","file":"a","line":12.0,"what":"w","why":"y"}]}')"
expect_status 1
end

begin "exactly fifty findings are accepted"
FIFTY=$(jq -cn '{reviewed: true, summary: "x", findings: [range(50) | {severity: "major", rule: "no-test", file: "a", line: 1, what: "w", why: "y"}]}')
run "$REVIEW/check-findings.sh" "$(answer "$FIFTY")"
expect_status 0
end

begin "every rule is accepted with its own severity"
PAIRS=$(jq -cn '{reviewed: true, summary: "x", findings: [
  (["data-loss", "security", "cannot-start", "opposite-of-spec"][] | {severity: "critical", rule: ., file: "a", line: 1, what: "w", why: "y"}),
  (["wrong-main-path", "criterion-not-met", "no-test", "unhandled-error", "project-rule"][] | {severity: "major", rule: ., file: "a", line: null, what: "w", why: "y"})]}')
run "$REVIEW/check-findings.sh" "$(answer "$PAIRS")"
expect_status 0
end

begin "more than fifty findings are refused"
MANY=$(jq -cn '{reviewed: true, summary: "x", findings: [range(51) | {severity: "major", rule: "no-test", file: "a", line: 1, what: "w", why: "y"}]}')
run "$REVIEW/check-findings.sh" "$(answer "$MANY")"
expect_status 1
end

begin "a missing file is refused"
run "$REVIEW/check-findings.sh" "$CASE_DIR/no-such-file.json"
expect_status 1
end

begin "the refusal does not repeat the answer"
run "$REVIEW/check-findings.sh" "$(answer '{"reviewed":true,"summary":"x","findings":[{"severity":"minor","rule":"no-test","file":"SECRETFILE","line":1,"what":"SECRETWHAT","why":"y"}]}')"
expect_not_in_output "SECRET"
end

echo "run-review.sh"

result() {
  local file="$CASE_DIR/result-$RANDOM.json" turns=${2:-7}
  jq -cn --argjson structured "$1" --argjson turns "$turns" '{type: "result", subtype: "success", is_error: false, result: "done", num_turns: $turns, duration_ms: 41200, total_cost_usd: 0.4312, permission_denials: [], structured_output: $structured}' > "$file"
  echo "$file"
}

review() {
  export STUB_CODE_DIR="$CASE_DIR/code"
  run "$REVIEW/run-review.sh" --work "$CASE_DIR/work" --out "$CASE_DIR/result" --code "$CASE_DIR/code" --model "${1-test-model}"
}

calls() {
  cat "$STUB_CLAUDE_COUNT" 2>/dev/null || echo 0
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
if [ "$(calls)" != "1" ]; then fail "expected exactly one call"; fi
if [ "$(jq -r '.findings | length' "$CASE_DIR/result/findings.json" 2>/dev/null)" != "2" ]; then fail "findings.json does not hold the two findings"; fi
end

begin "the reviewer is called restricted, read-only, in the work directory"
STUB_CLAUDE_OUTPUTS="$(result "$GOOD")"
export STUB_CLAUDE_OUTPUTS
review
for argument in "-p" "--restricted" "--tools" "Read,Grep,Glob" "--strict-mcp-config" "--permission-mode" "dontAsk" "--no-session-persistence" "--output-format" "json" "--json-schema" "--model" "test-model" "--add-dir" "$CASE_DIR/code" "--max-budget-usd"; do
  expect_log_line "claude-arg: $argument"
done
expect_log_line "claude-cwd: work"
if grep -E '^claude-arg: ' "$STUB_LOG" | grep -qE 'bypassPermissions|acceptEdits|^claude-arg: (Bash|Edit|Write|WebFetch|WebSearch)'; then fail "the reviewer was given more than read-only"; fi
end

begin "the reviewer's environment holds the Claude token and nothing else secret"
STUB_CLAUDE_OUTPUTS="$(result "$GOOD")"
export STUB_CLAUDE_OUTPUTS GH_TOKEN=ghs_secret GITHUB_TOKEN=ghs_secret CLAUDE_CODE_OAUTH_TOKEN=claude-secret SOME_OTHER_SECRET=other
review
ENVIRONMENT=$(sed -n 's/^claude-env: //p' "$STUB_LOG" | head -n 1)
for name in GH_TOKEN GITHUB_TOKEN SOME_OTHER_SECRET; do
  case " $ENVIRONMENT " in *" $name "*) fail "$name reached the reviewer" ;; esac
done
case " $ENVIRONMENT " in *" CLAUDE_CODE_OAUTH_TOKEN "*) ;; *) fail "the Claude token did not reach the reviewer" ;; esac
case " $ENVIRONMENT " in *" PATH "*) ;; *) fail "PATH did not reach the reviewer" ;; esac
expect_not_in_output "ghs_secret"
expect_not_in_output "claude-secret"
end

begin "links in the pull request's code are removed before the reviewer starts"
ln -s /etc/hosts "$CASE_DIR/code/outside"
mkdir -p "$CASE_DIR/code/sub"
ln -s ../.. "$CASE_DIR/code/sub/up"
echo "real" > "$CASE_DIR/code/real.txt"
STUB_CLAUDE_OUTPUTS="$(result "$GOOD")"
export STUB_CLAUDE_OUTPUTS
review
expect_log_line "claude-symlinks: 0"
if [ ! -f "$CASE_DIR/code/real.txt" ]; then fail "a real file was removed"; fi
if [ ! -f /etc/hosts ]; then fail "the link's target was removed"; fi
end

begin "an answer that carries the Claude token is a failed review"
LEAK=$(jq -cn '{reviewed: true, summary: "the environment says CLAUDE=claude-secret-value", findings: []}')
STUB_CLAUDE_OUTPUTS="$(result "$LEAK")"
export STUB_CLAUDE_OUTPUTS CLAUDE_CODE_OAUTH_TOKEN=claude-secret-value
review
expect_output "outcome=failed"
expect_not_in_output "claude-secret-value"
if [ -e "$CASE_DIR/result/findings.json" ]; then fail "findings.json holds the token"; fi
end

begin "an unreadable first answer is asked for once more"
STUB_CLAUDE_OUTPUTS="$(result '{"reviewed":true,"summary":"x","findings":"none"}'):$(result "$GOOD")"
export STUB_CLAUDE_OUTPUTS
review
expect_status 0
expect_output "outcome=findings"
expect_output "turns=14"
expect_output "cost=0.86"
if [ "$(calls)" != "2" ]; then fail "expected exactly two calls"; fi
end

begin "two unreadable answers are unreadable, never no findings"
STUB_CLAUDE_OUTPUTS="$(result '{"reviewed":true,"summary":"x","findings":"none"}'):$(result 'null')"
export STUB_CLAUDE_OUTPUTS
review
expect_status 0
expect_output "outcome=unreadable"
if [ "$(calls)" != "2" ]; then fail "expected exactly two calls"; fi
if [ -e "$CASE_DIR/result/findings.json" ]; then fail "findings.json exists for an unreadable review"; fi
end

begin "a reviewer that says it could not read the change is not a clean review"
BLIND='{"reviewed":false,"summary":"I could not open inputs/diff.patch or any file.","findings":[]}'
STUB_CLAUDE_OUTPUTS="$(result "$BLIND"):$(result "$BLIND")"
export STUB_CLAUDE_OUTPUTS
review
expect_output "outcome=unreadable"
if [ -e "$CASE_DIR/result/findings.json" ]; then fail "findings.json exists although nothing was read"; fi
end

begin "an answer given without reading anything is not a clean review"
EMPTY='{"reviewed":true,"summary":"Nothing found.","findings":[]}'
STUB_CLAUDE_OUTPUTS="$(result "$EMPTY" 1):$(result "$EMPTY" 1)"
export STUB_CLAUDE_OUTPUTS
review
expect_output "outcome=unreadable"
if [ "$(calls)" != "2" ]; then fail "expected exactly two calls"; fi
end

begin "a call that fails is failed, never no findings, and is not repeated"
export STUB_CLAUDE_OUTPUTS="" STUB_CLAUDE_EXIT=1
review
expect_status 0
expect_output "outcome=failed"
expect_output "detail=exit-1"
if [ "$(calls)" != "1" ]; then fail "a failed call was repeated"; fi
if [ -e "$CASE_DIR/result/findings.json" ]; then fail "findings.json exists for a failed review"; fi
end

begin "a call that reports an error is failed, with the reason and its usage"
ERR="$CASE_DIR/err.json"
jq -cn '{type: "result", subtype: "success", is_error: true, terminal_reason: "max_budget", api_error_status: 429, result: "private text; rm -rf", num_turns: 30, duration_ms: 9000, total_cost_usd: 5.02, permission_denials: []}' > "$ERR"
export STUB_CLAUDE_OUTPUTS="$ERR"
review
expect_output "outcome=failed"
expect_output "detail=error-success-max_budget-429"
expect_output "cost=5.02"
expect_output "turns=30"
expect_not_in_output "private text"
end

begin "a call that exits with an error and says why is failed with that reason"
ERR="$CASE_DIR/err-exit.json"
jq -cn '{type: "result", subtype: "success", is_error: true, terminal_reason: "api_error", result: "Not logged in", num_turns: 1, duration_ms: 100, total_cost_usd: 0, permission_denials: []}' > "$ERR"
export STUB_CLAUDE_OUTPUTS="$ERR" STUB_CLAUDE_EXIT=1
review
expect_output "outcome=failed"
expect_output "detail=error-success-api_error-none"
end

begin "an unreadable answer followed by a failed call is failed"
STUB_CLAUDE_OUTPUTS="$(result 'null'):$CASE_DIR/missing.json"
export STUB_CLAUDE_OUTPUTS
review
expect_output "outcome=failed"
if [ "$(calls)" != "2" ]; then fail "expected exactly two calls"; fi
end

begin "nothing on stdout is failed"
: > "$CASE_DIR/empty.json"
export STUB_CLAUDE_OUTPUTS="$CASE_DIR/empty.json"
review
expect_output "outcome=failed"
end

begin "a result with junk numbers is failed, not unreadable"
JUNK="$CASE_DIR/junk.json"
jq -cn --argjson structured "$GOOD" '{is_error: false, num_turns: "many", duration_ms: null, total_cost_usd: "free", permission_denials: "none", structured_output: $structured}' > "$JUNK"
export STUB_CLAUDE_OUTPUTS="$JUNK"
review
expect_status 0
expect_output "outcome=failed"
end

begin "tool calls the reviewer was refused are counted"
DENIED="$CASE_DIR/denied.json"
jq -cn --argjson structured "$GOOD" '{type: "result", subtype: "success", is_error: false, num_turns: 3, duration_ms: 1000, total_cost_usd: 0.1, permission_denials: [{tool_name: "Bash"}, {tool_name: "Write"}], structured_output: $structured}' > "$DENIED"
export STUB_CLAUDE_OUTPUTS="$DENIED"
review
expect_output "denials=2"
end

begin "what one attempt wrote is not in the reviewer's folder for the next"
STUB_CLAUDE_OUTPUTS="$(result 'null'):$(result "$GOOD")"
export STUB_CLAUDE_OUTPUTS
review
if [ -n "$(find "$CASE_DIR/work" -type f)" ]; then fail "files were written into the reviewer's folder"; fi
end

for MODEL in "--help" "-x" "a b" 'm;id' ""; do
  begin "refused model name: '$MODEL'"
  STUB_CLAUDE_OUTPUTS="$(result "$GOOD")"
  export STUB_CLAUDE_OUTPUTS
  review "$MODEL"
  expect_status 2
  if [ "$(calls)" != "0" ]; then fail "the reviewer was started"; fi
  end
done

begin "a missing work folder starts nothing"
STUB_CLAUDE_OUTPUTS="$(result "$GOOD")"
export STUB_CLAUDE_OUTPUTS
run "$REVIEW/run-review.sh" --work "$CASE_DIR/no-such" --out "$CASE_DIR/result" --code "$CASE_DIR/code" --model test-model
expect_status 2
if [ "$(calls)" != "0" ]; then fail "the reviewer was started"; fi
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
run "$REVIEW/comment.sh" --head "$SHA" --model test-model --findings "$(answer '{"reviewed":true,"summary":"Read everything.","findings":[]}')"
expect_in_output "No critical or major findings"
expect_in_output "Read everything."
end

begin "reviewer text cannot mention people, add HTML or break out of its place"
EVIL=$(jq -cn '{reviewed: true, summary: "ping @someone <img src=x onerror=alert(1)> see https://github.com/other/repo/issues/1 and www.example.test and GH-12 and #34 and $x$", findings: [{severity: "major", rule: "no-test", file: "a`b.java", line: 3, what: "@team please <script>x</script>\n# Heading\n- item\n---", why: "see [link](http://example.test) <!-- hidden --> </sub> | pipe \u202e reversed \u200b"}]}')
run "$REVIEW/comment.sh" --head "$SHA" --model test-model --findings "$(answer "$EVIL")"
expect_status 0
expect_not_in_output "@someone"
expect_not_in_output "@team"
expect_not_in_output "<img"
expect_not_in_output "<script>"
expect_not_in_output "<!-- hidden"
if printf '%s\n' "$OUTPUT" | grep -q '^# Heading'; then fail "reviewer text became a heading"; fi
if printf '%s\n' "$OUTPUT" | grep -qE '^ *(---|===)'; then fail "reviewer text became a rule or a heading underline"; fi
expect_not_in_output "https://github.com/other"
expect_not_in_output "http://example.test"
expect_not_in_output " www.example.test"
expect_not_in_output "GH-12"
expect_not_in_output " #34"
expect_not_in_output "\$x\$"
expect_not_in_output "</sub> |"
if printf '%s' "$OUTPUT" | LC_ALL=C grep -q "$(printf '\342\200\256')"; then fail "a text-direction character survived"; fi
expect_in_output "What:"
end

begin "tool calls that were refused are named in the comment"
run "$REVIEW/comment.sh" --head "$SHA" --model test-model --findings "$(answer "$GOOD")" --turns 7 --cost 0.43 --seconds 41 --denials 3
expect_in_output "3 tool calls were refused"
end

begin "the longest accepted answer still fits one comment"
LONG=$(jq -cn '{reviewed: true, summary: ("s" * 2000), findings: [range(50) | {severity: (if . < 5 then "critical" else "major" end), rule: (if . < 5 then "security" else "no-test" end), file: ("f" * 300), line: 1, what: ("*" * 1500), why: ("_" * 1500)}]}')
run "$REVIEW/comment.sh" --head "$SHA" --model test-model --findings "$(answer "$LONG")"
expect_status 0
if [ "${#OUTPUT}" -gt 60000 ]; then fail "the comment is ${#OUTPUT} characters"; fi
expect_in_output "5 critical, 45 major"
expect_in_output "more findings are not shown"
expect_in_output "Report only"
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
