#!/usr/bin/env bash
set -uo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
IMPLEMENT=$(dirname "$HERE")/implement
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
  mkdir -p "$CASE_DIR/out" "$CASE_DIR/gh"
  : > "$CASE_DIR/stub.log"
  unset FACTORY_AUTHORS GH_TOKEN GITHUB_TOKEN CLAUDE_CODE_OAUTH_TOKEN
  export STUB_LOG="$CASE_DIR/stub.log" STUB_GH_DIR="$CASE_DIR/gh"
}

run() {
  OUTPUT=$(PATH="$HERE/stubs:$PATH" "$@" 2>&1)
  STATUS=$?
}

fail() {
  CASE_OK=0
  echo "  FAIL [$CASE] $1"
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

expect_status() {
  if [ "$STATUS" -ne "$1" ]; then fail "exit status $STATUS, expected $1"; fi
}

expect_output() {
  if ! printf '%s\n' "$OUTPUT" | grep -qxF -- "$1"; then fail "expected output line: $1"; fi
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

expect_no_calls() {
  if [ -s "$STUB_LOG" ]; then fail "GitHub was called: $(head -n 1 "$STUB_LOG")"; fi
}

expect_file_has() {
  if ! grep -qF -- "$2" "$1" 2>/dev/null; then fail "$(basename "$1") lacks: $2"; fi
}

expect_file_lacks() {
  if grep -qF -- "$2" "$1" 2>/dev/null; then fail "$(basename "$1") must not contain: $2"; fi
}

line_of() {
  grep -nF -- "$1" "$STUB_LOG" | head -n 1 | cut -d: -f1
}

expect_order() {
  local first second
  first=$(line_of "$1")
  second=$(line_of "$2")
  if [ -z "$first" ] || [ -z "$second" ] || [ "$first" -ge "$second" ]; then fail "expected '$1' before '$2'"; fi
}

body_of() {
  local file
  file=$(grep -F -- "$1" "$STUB_LOG" | grep -oE -- '--body-file [^ ]+' | head -n 1 | cut -d' ' -f2)
  if [ -n "$file" ] && [ -f "$file" ]; then cat "$file"; fi
}

issue() {
  local state=${1:-OPEN} author=${2:-owner} labels=${3:-approved} title=${4-Steps for one day} body=${5-Add GET /api/steps/{date\}.}
  jq -cn --arg state "$state" --arg author "$author" --arg labels "$labels" --arg title "$title" --arg body "$body" \
    '{number: 7, state: $state, author: {login: $author}, title: $title, body: $body, labels: ($labels | split(",") | map(select(. != "") | {name: .}))}' \
    > "$STUB_GH_DIR/issue-view"
  echo '[]' > "$STUB_GH_DIR/pr-list"
}

check_issue() {
  run "$IMPLEMENT/check-issue.sh" --repo owner/repo --issue "${1-7}" --sender "${2-owner}" --out "$CASE_DIR/out"
}

echo "check-issue.sh"

begin "the owner's label on the owner's open issue is built"
issue
check_issue
expect_status 0
expect_output "build=yes"
expect_output "branch=factory/issue-7"
expect_file_has "$CASE_DIR/out/spec.md" "Steps for one day"
expect_file_has "$CASE_DIR/out/spec.md" "Add GET /api/steps/{date}."
expect_file_has "$CASE_DIR/out/prompt.md" "Add GET /api/steps/{date}."
expect_file_has "$CASE_DIR/out/prompt.md" "commit"
expect_file_has "$CASE_DIR/out/title.txt" "Steps for one day"
end

begin "a label added by someone else builds nothing and reads nothing"
issue
check_issue 7 stranger
expect_status 0
expect_output "build=no"
expect_output "reason=not-owner-label"
expect_no_calls
if [ -e "$CASE_DIR/out/spec.md" ]; then fail "a spec was written"; fi
end

begin "the owner's login is compared without regard to case"
issue OPEN Owner
check_issue 7 OWNER
expect_output "build=yes"
end

begin "an issue written by someone else is not built"
issue OPEN stranger
check_issue
expect_output "build=no"
expect_output "reason=not-owner-issue"
if [ -e "$CASE_DIR/out/prompt.md" ]; then fail "a prompt was written"; fi
end

begin "extra review authors do not make their issues buildable"
issue OPEN app/github-actions
export FACTORY_AUTHORS="app/github-actions, stranger"
check_issue
expect_output "reason=not-owner-issue"
end

begin "a closed issue is not built"
issue CLOSED
check_issue
expect_output "build=no"
expect_output "reason=closed"
end

begin "an issue without the approved label is not built"
issue OPEN owner "bug,enhancement"
check_issue
expect_output "build=no"
expect_output "reason=no-label"
end

begin "an open factory pull request for the issue stops a second build"
issue
echo '[{"number": 41, "isCrossRepository": false}]' > "$STUB_GH_DIR/pr-list"
check_issue
expect_output "build=no"
expect_output "reason=pr-open"
expect_output "pr=41"
expect_log "pr list --repo owner/repo --head factory/issue-7 --state open"
end

begin "a pull request from a fork with the same branch name does not stop a build"
issue
echo '[{"number": 55, "isCrossRepository": true}]' > "$STUB_GH_DIR/pr-list"
check_issue
expect_output "build=yes"
end

begin "of a fork's and an own pull request the own one is named"
issue
echo '[{"number": 55, "isCrossRepository": true}, {"number": 41, "isCrossRepository": false}]' > "$STUB_GH_DIR/pr-list"
check_issue
expect_output "reason=pr-open"
expect_output "pr=41"
end

begin "an issue that cannot be read is failed, not built"
issue
echo 1 > "$STUB_GH_DIR/issue-view.exit"
check_issue
expect_status 0
expect_output "build=no"
expect_output "reason=failed"
end

begin "a pull request list that cannot be read is failed, not built"
issue
echo 1 > "$STUB_GH_DIR/pr-list.exit"
check_issue
expect_output "build=no"
expect_output "reason=failed"
end

begin "an answer that is not JSON is failed"
issue
echo 'not json' > "$STUB_GH_DIR/issue-view"
check_issue
expect_output "reason=failed"
end

begin "an issue with no title is not built"
issue OPEN owner approved "   "
check_issue
expect_output "build=no"
expect_output "reason=empty-spec"
end

begin "an issue with a title and no text is built from the title"
jq -cn '{number: 7, state: "OPEN", author: {login: "owner"}, title: "Only a title", body: null, labels: [{name: "approved"}]}' > "$CASE_DIR/gh/issue-view"
echo '[]' > "$CASE_DIR/gh/pr-list"
check_issue
expect_output "build=yes"
expect_file_has "$CASE_DIR/out/spec.md" "Only a title"
expect_file_lacks "$CASE_DIR/out/spec.md" "null"
end

begin "the spec's text is copied, never run"
# shellcheck disable=SC2016
issue OPEN owner approved 'Title $(touch '"$CASE_DIR"'/ran-title)' 'Body `touch '"$CASE_DIR"'/ran-body` $(touch '"$CASE_DIR"'/ran-body2)'
check_issue
expect_output "build=yes"
if [ -e "$CASE_DIR/ran-title" ] || [ -e "$CASE_DIR/ran-body" ] || [ -e "$CASE_DIR/ran-body2" ]; then fail "text from the issue was executed"; fi
# shellcheck disable=SC2016
expect_file_has "$CASE_DIR/out/spec.md" '$(touch'
end

begin "the title for the pull request is one line of at most 200 characters"
issue OPEN owner approved "$(printf 'First line\nsecond line %0300d' 0)"
check_issue
expect_output "build=yes"
if [ "$(wc -l < "$CASE_DIR/out/title.txt" | tr -d ' ')" -gt 1 ]; then fail "the title has more than one line"; fi
if [ "$(wc -c < "$CASE_DIR/out/title.txt" | tr -d ' ')" -gt 201 ]; then fail "the title is longer than 200 characters"; fi
end

for NUMBER in "7;id" "0" "" "-1" "07" "12345678"; do
  begin "refused issue number: '$NUMBER'"
  issue
  check_issue "$NUMBER"
  expect_status 2
  expect_no_calls
  end
done

begin "a repository name that is not owner/name is refused"
issue
run "$IMPLEMENT/check-issue.sh" --repo "owner" --issue 7 --sender owner --out "$CASE_DIR/out"
expect_status 2
expect_no_calls
end

echo "open-pr.sh"

MAIN_SHA=1111111111111111111111111111111111111111
TREE_SHA=2222222222222222222222222222222222222222
NEW_SHA=3333333333333333333333333333333333333333

github_ready() {
  jq -cn --arg sha "$MAIN_SHA" '{ref: "refs/heads/main", object: {sha: $sha}}' > "$STUB_GH_DIR/api-ref-main"
  echo '{"message": "Not Found"}' > "$STUB_GH_DIR/api-ref-branch"
  echo 1 > "$STUB_GH_DIR/api-ref-branch.exit"
  jq -cn --arg sha "$MAIN_SHA" --arg tree "$TREE_SHA" '{sha: $sha, tree: {sha: $tree}}' > "$STUB_GH_DIR/api-commit"
  jq -cn --arg sha "$NEW_SHA" '{sha: $sha}' > "$STUB_GH_DIR/api-create-commit"
  echo '{}' > "$STUB_GH_DIR/api-create-ref"
  echo '{"number": 41, "html_url": "https://github.com/owner/repo/pull/41"}' > "$STUB_GH_DIR/api-create-pull"
  echo '[]' > "$STUB_GH_DIR/api-add-labels"
  printf 'Steps for one day\n' > "$CASE_DIR/out/title.txt"
}

open_pr() {
  run "$IMPLEMENT/open-pr.sh" --repo owner/repo --issue "${1-7}" --title-file "$CASE_DIR/out/title.txt"
}

begin "a branch with an empty commit on main, then the pull request"
github_ready
open_pr
expect_status 0
expect_output "pr=41"
expect_output "branch=factory/issue-7"
expect_log "api -X POST repos/owner/repo/git/commits"
expect_log "tree=$TREE_SHA"
expect_log "parents[]=$MAIN_SHA"
expect_log "ref=refs/heads/factory/issue-7"
expect_log "sha=$NEW_SHA"
expect_log "api -X POST repos/owner/repo/pulls"
expect_log "head=factory/issue-7"
expect_log "base=main"
expect_log "title=Steps for one day"
expect_log "api -X POST repos/owner/repo/issues/41/labels"
expect_log "labels[]=factory"
expect_log "labels[]=building"
expect_order "git/refs" "repos/owner/repo/pulls"
expect_order "repos/owner/repo/pulls" "issues/41/labels"
expect_no_log "-X DELETE"
expect_file_has "$(grep -oE 'body=@[^ ]+' "$STUB_LOG" | head -n 1 | cut -d@ -f2)" "Closes #7"
end

begin "a leftover branch that cannot be removed stops everything"
github_ready
jq -cn '{ref: "refs/heads/factory/issue-7", object: {sha: "4444444444444444444444444444444444444444"}}' > "$STUB_GH_DIR/api-ref-branch"
rm "$STUB_GH_DIR/api-ref-branch.exit"
echo 1 > "$STUB_GH_DIR/api-delete-ref.exit"
open_pr
if [ "$STATUS" -eq 0 ]; then fail "exit status 0"; fi
expect_no_log "git/commits -f"
expect_no_log "repos/owner/repo/pulls"
end

begin "when the branch cannot be created no pull request is opened"
github_ready
echo 1 > "$STUB_GH_DIR/api-create-ref.exit"
open_pr
if [ "$STATUS" -eq 0 ]; then fail "exit status 0"; fi
expect_no_log "repos/owner/repo/pulls"
end

begin "when the labels cannot be set the branch is removed, which closes the pull request"
github_ready
echo 1 > "$STUB_GH_DIR/api-add-labels.exit"
open_pr
if [ "$STATUS" -eq 0 ]; then fail "exit status 0"; fi
expect_log "api -X DELETE repos/owner/repo/git/refs/heads/factory/issue-7"
expect_not_in_output "pr="
end

begin "a branch left over from a closed pull request is replaced"
github_ready
jq -cn '{ref: "refs/heads/factory/issue-7", object: {sha: "4444444444444444444444444444444444444444"}}' > "$STUB_GH_DIR/api-ref-branch"
rm "$STUB_GH_DIR/api-ref-branch.exit"
open_pr
expect_status 0
expect_output "pr=41"
expect_log "api -X DELETE repos/owner/repo/git/refs/heads/factory/issue-7"
expect_order "-X DELETE" "ref=refs/heads/factory/issue-7"
end

begin "when the commit cannot be made no pull request is opened"
github_ready
echo 1 > "$STUB_GH_DIR/api-create-commit.exit"
open_pr
if [ "$STATUS" -eq 0 ]; then fail "exit status 0"; fi
expect_no_log "repos/owner/repo/pulls"
expect_no_log "git/refs -f"
end

begin "when the pull request cannot be opened the new branch is removed"
github_ready
echo 1 > "$STUB_GH_DIR/api-create-pull.exit"
open_pr
if [ "$STATUS" -eq 0 ]; then fail "exit status 0"; fi
expect_log "api -X DELETE repos/owner/repo/git/refs/heads/factory/issue-7"
expect_not_in_output "pr="
end

begin "an answer without a pull request number is an error"
github_ready
echo '{"number": "41; id"}' > "$STUB_GH_DIR/api-create-pull"
open_pr
if [ "$STATUS" -eq 0 ]; then fail "exit status 0"; fi
expect_not_in_output "pr="
end

begin "main's commit id must look like one"
github_ready
echo '{"object": {"sha": "main; id"}}' > "$STUB_GH_DIR/api-ref-main"
open_pr
if [ "$STATUS" -eq 0 ]; then fail "exit status 0"; fi
expect_no_log "git/commits"
end

begin "a missing title file is refused before GitHub is called"
github_ready
rm "$CASE_DIR/out/title.txt"
open_pr
expect_status 2
expect_no_calls
end

echo "check-branch.sh"

compare() {
  local files=()
  local entry
  for entry in "$@"; do
    files+=("$(jq -cn --arg name "${entry%%<-*}" --arg previous "$([ "$entry" != "${entry%%<-*}" ] && echo "${entry#*<-}")" \
      '{filename: $name, status: "modified"} + (if $previous != "" then {previous_filename: $previous, status: "renamed"} else {} end)')")
  done
  if [ ${#files[@]} -eq 0 ]; then
    echo '{"commits": [{"sha": "1111111111111111111111111111111111111111"}], "files": []}' > "$STUB_GH_DIR/api-compare"
  else
    printf '%s\n' "${files[@]}" | jq -cs '{commits: [{sha: "1111111111111111111111111111111111111111"}, {sha: "2222222222222222222222222222222222222222"}], files: .}' > "$STUB_GH_DIR/api-compare"
  fi
  printf 'diff --git a/x b/x\n+ordinary line\n' > "$STUB_GH_DIR/api-compare-diff"
  printf 'diff --git a/x b/x\n+ordinary line\n' > "$STUB_GH_DIR/api-commit-diff"
  echo '{"number": 41, "state": "open"}' > "$STUB_GH_DIR/api-pull"
}

check_branch() {
  run "$IMPLEMENT/check-branch.sh" --repo owner/repo --branch "${1-factory/issue-7}" --pr "${2-41}" --out "$CASE_DIR/out"
}

begin "a change inside the allowed places is ok"
compare "src/main/java/A.java" "src/test/java/ATest.java" "frontend/src/App.tsx" "http/steps.http" "docs/a/b.md"
check_branch
expect_status 0
expect_output "result=ok"
expect_output "files=5"
expect_log "api repos/owner/repo/compare/main...factory/issue-7"
end

begin "a branch with no changed file is empty"
compare
check_branch
expect_output "result=empty"
expect_output "files=0"
end

for OUTSIDE in ".github/workflows/ci.yml" "infra/deploy.sh" "factory/gate.sh" "CLAUDE.md" ".claude/settings.json" ".claude/NOTES.md" \
  "frontend/package.json" "frontend/package-lock.json" "frontend/vite.config.ts" "gradlew" "gradle/wrapper/gradle-wrapper.properties" \
  "settings.gradle" "docker-compose.yml" "README.md" "src/../infra/deploy.sh" "srcx/A.java" "docs" "src" "frontend/srcs/a.ts" "/src/a" "build.gradle.kts" \
  "build.gradle" "src/CLAUDE.md" "docs/CLAUDE.md" "docs/sub/claude.md" "src/.claude/skills/x/SKILL.md" "src/main/resources/.mcp.json" "docs/.github/workflows/x.yml" "src/.git/config" "src/AGENTS.md"; do
  begin "outside the allowed places: $OUTSIDE"
  compare "src/main/java/A.java" "$OUTSIDE"
  check_branch
  expect_output "result=outside"
  expect_file_has "$CASE_DIR/out/outside.txt" "$OUTSIDE"
  expect_file_lacks "$CASE_DIR/out/outside.txt" "src/main/java/A.java"
  end
done

begin "a file moved out of a forbidden place counts as outside"
compare "src/deploy.sh<-infra/deploy.sh"
check_branch
expect_output "result=outside"
expect_file_has "$CASE_DIR/out/outside.txt" "infra/deploy.sh"
end

begin "a file name with a line break in it cannot pass as two allowed names"
jq -cn '{commits: [{sha: "a"}, {sha: "b"}], files: [{filename: "infra/x\nsrc/evil.sh", status: "added"}]}' > "$CASE_DIR/gh/api-compare"
printf '+x\n' > "$CASE_DIR/gh/api-compare-diff"
printf '+x\n' > "$CASE_DIR/gh/api-commit-diff"
echo '{"number": 41, "state": "open"}' > "$CASE_DIR/gh/api-pull"
check_branch
expect_output "result=outside"
if [ "$(wc -l < "$CASE_DIR/out/outside.txt" | tr -d ' ')" -ne 1 ]; then fail "the name was split over lines"; fi
end

begin "a file name with a line break in it is outside even under an allowed place"
jq -cn '{commits: [{sha: "a"}, {sha: "b"}], files: [{filename: "src/a\nb.java", status: "added"}]}' > "$CASE_DIR/gh/api-compare"
printf '+x\n' > "$CASE_DIR/gh/api-compare-diff"
printf '+x\n' > "$CASE_DIR/gh/api-commit-diff"
echo '{"number": 41, "state": "open"}' > "$CASE_DIR/gh/api-pull"
check_branch
expect_output "result=outside"
end

begin "a pull request closed while the agent worked is reported as closed"
compare "src/main/java/A.java"
echo '{"number": 41, "state": "closed"}' > "$STUB_GH_DIR/api-pull"
check_branch
expect_output "result=closed"
end

begin "a pull request whose state cannot be read is failed"
compare "src/main/java/A.java"
echo 1 > "$STUB_GH_DIR/api-pull.exit"
check_branch
expect_output "result=failed"
end

begin "the token in a commit that a later commit took out again is still found"
compare "src/main/java/A.java"
printf '+String key = "claude-secret-value";\n' > "$STUB_GH_DIR/api-commit-diff"
export CLAUDE_CODE_OAUTH_TOKEN="claude-secret-value"
check_branch
expect_output "result=token"
expect_log "api repos/owner/repo/commits/"
end

begin "a commit that cannot be read is failed, not ok"
compare "src/main/java/A.java"
echo 1 > "$STUB_GH_DIR/api-commit-diff.exit"
export CLAUDE_CODE_OAUTH_TOKEN="claude-secret-value"
check_branch
expect_output "result=failed"
end

for BROKEN in $'claude-secret\n-value' $'claude-secret-value\n\n' "claude secret"; do
  begin "a token with a space or line break in it is failed, never a false alarm ($(printf '%s' "$BROKEN" | tr -d 'a-z-' | od -An -c | tr -d ' '))"
  compare
  export CLAUDE_CODE_OAUTH_TOKEN="$BROKEN"
  check_branch
  expect_output "result=failed"
  end
done

begin "more commits than can be read one by one is not ok"
jq -cn '{commits: [range(0; 60) | {sha: ("c" + (. | tostring))}], files: [{filename: "src/A.java", status: "added"}]}' > "$CASE_DIR/gh/api-compare"
printf '+x\n' > "$CASE_DIR/gh/api-compare-diff"
printf '+x\n' > "$CASE_DIR/gh/api-commit-diff"
echo '{"number": 41, "state": "open"}' > "$CASE_DIR/gh/api-pull"
export CLAUDE_CODE_OAUTH_TOKEN="claude-secret-value"
check_branch
expect_output "result=too-many"
end

begin "the Claude token's text in the change is found, and not repeated"
compare "src/main/java/A.java"
printf 'diff --git a/src/A b/src/A\n+String key = "claude-secret-value";\n' > "$STUB_GH_DIR/api-compare-diff"
export CLAUDE_CODE_OAUTH_TOKEN="claude-secret-value"
check_branch
expect_output "result=token"
expect_not_in_output "claude-secret-value"
end

begin "the token is looked for before the places"
compare ".github/workflows/ci.yml"
printf '+claude-secret-value\n' > "$STUB_GH_DIR/api-compare-diff"
export CLAUDE_CODE_OAUTH_TOKEN="claude-secret-value"
check_branch
expect_output "result=token"
end

begin "without a token to look for the change is judged by its places"
compare "src/main/java/A.java"
check_branch
expect_output "result=ok"
end

begin "a comparison that cannot be read is failed"
compare "src/main/java/A.java"
echo 1 > "$STUB_GH_DIR/api-compare.exit"
check_branch
expect_status 0
expect_output "result=failed"
end

begin "a diff that cannot be read is failed, not ok"
compare "src/main/java/A.java"
echo 1 > "$STUB_GH_DIR/api-compare-diff.exit"
export CLAUDE_CODE_OAUTH_TOKEN="claude-secret-value"
check_branch
expect_output "result=failed"
end

begin "three hundred files is more than GitHub lists and is not ok"
jq -cn '{commits: [{sha: "a"}, {sha: "b"}], files: [range(0; 300) | {filename: ("src/F" + (. | tostring) + ".java"), status: "added"}]}' > "$CASE_DIR/gh/api-compare"
printf '+x\n' > "$CASE_DIR/gh/api-compare-diff"
printf '+x\n' > "$CASE_DIR/gh/api-commit-diff"
echo '{"number": 41, "state": "open"}' > "$CASE_DIR/gh/api-pull"
check_branch
expect_output "result=too-many"
end

for BRANCH in "main" "factory/issue-7; id" "factory/issue-" "factory/issue-07" "probe/x" ""; do
  begin "refused branch name: '$BRANCH'"
  compare "src/main/java/A.java"
  check_branch "$BRANCH"
  expect_status 2
  expect_no_calls
  end
done

for NUMBER in "0" "41; id" ""; do
  begin "refused pull request number: '$NUMBER'"
  compare "src/main/java/A.java"
  check_branch factory/issue-7 "$NUMBER"
  expect_status 2
  expect_no_calls
  end
done

echo "run-numbers.sh"

begin "the numbers of a run and the tools it called"
cat > "$CASE_DIR/run.json" <<'JSON'
[{"type":"system","subtype":"init"},
 {"type":"assistant","message":{"content":[{"type":"text","text":"x"},{"type":"tool_use","name":"Read","input":{}}]}},
 {"type":"assistant","message":{"content":[{"type":"tool_use","name":"Read","input":{}},{"type":"tool_use","name":"mcp__github_file_ops__commit_files","input":{}}]}},
 {"type":"result","num_turns":14,"duration_ms":93400,"total_cost_usd":0.4179,"permission_denials":[{"tool_name":"Read"},{"tool_name":"Write"}]}]
JSON
run "$IMPLEMENT/run-numbers.sh" "$CASE_DIR/run.json"
expect_status 0
expect_output "turns=14"
expect_output "seconds=93"
expect_output "cost=0.42"
expect_output "denials=2"
expect_output "tools=Read.2,mcp__github_file_ops__commit_files.1"
end

begin "no record of the run gives zeros, not an error"
run "$IMPLEMENT/run-numbers.sh" "$CASE_DIR/missing.json"
expect_status 0
expect_output "turns=0"
expect_output "cost=0"
expect_output "denials=0"
expect_output "tools=none"
end

begin "a record with junk in it gives zeros and plain tool names"
cat > "$CASE_DIR/junk.json" <<'JSON'
[{"type":"assistant","message":{"content":[{"type":"tool_use","name":"Bad; $(id)\nname"}]}},
 {"type":"result","num_turns":"many","duration_ms":null,"total_cost_usd":"free","permission_denials":"none"}]
JSON
run "$IMPLEMENT/run-numbers.sh" "$CASE_DIR/junk.json"
expect_status 0
expect_output "turns=0"
expect_output "seconds=0"
expect_output "cost=0"
expect_output "denials=0"
expect_not_in_output ";"
expect_not_in_output "\$("
end

echo "report.sh"

report() {
  run "$IMPLEMENT/report.sh" "$@"
}

for REASON in not-owner-label not-owner-issue closed no-label empty-spec failed; do
  begin "a refusal is said on the issue: $REASON"
  report refuse --repo owner/repo --issue 7 --reason "$REASON"
  expect_status 0
  expect_log "issue comment 7 --repo owner/repo --body-file"
  if [ -z "$(body_of 'issue comment 7')" ]; then fail "the comment is empty"; fi
  end
done

begin "a refusal because a pull request is open points to it"
report refuse --repo owner/repo --issue 7 --reason pr-open --pr 41
expect_status 0
case "$(body_of 'issue comment 7')" in *"#41"*) ;; *) fail "the comment does not name the pull request" ;; esac
end

begin "an unknown refusal reason is refused"
report refuse --repo owner/repo --issue 7 --reason 'whatever; id'
expect_status 2
expect_no_calls
end

begin "no change: the pull request is closed, the branch deleted, the issue told"
report empty --repo owner/repo --issue 7 --pr 41 --branch factory/issue-7 --agent failure
expect_status 0
expect_log "pr close 41 --repo owner/repo"
expect_log "api -X DELETE repos/owner/repo/git/refs/heads/factory/issue-7"
expect_log "issue comment 7 --repo owner/repo"
BODY=$(body_of 'issue comment 7')
case "$BODY" in *"no change"*) ;; *) fail "the issue is not told that nothing was written" ;; esac
case "$BODY" in *"ended with an error"*) ;; *) fail "the issue is not told that the agent's run failed" ;; esac
end

begin "no change after a normal run does not speak of an error"
report empty --repo owner/repo --issue 7 --pr 41 --branch factory/issue-7 --agent success
expect_status 0
BODY=$(body_of 'issue comment 7')
case "$BODY" in *"ended normally"*) ;; *) fail "the issue is not told the run ended normally" ;; esac
case "$BODY" in *"ended with an error"*) fail "the issue is told of an error that did not happen" ;; esac
end

begin "files outside the allowed places: needs-human and the files named safely"
# shellcheck disable=SC2016
printf '%s\n' '.github/workflows/ci.yml' 'infra/`x` @someone <b>.sh' > "$CASE_DIR/outside.txt"
report outside --repo owner/repo --issue 7 --pr 41 --files "$CASE_DIR/outside.txt"
expect_status 0
expect_log "pr edit 41 --repo owner/repo --add-label needs-human"
expect_log "pr comment 41 --repo owner/repo"
BODY=$(body_of 'pr comment 41')
case "$BODY" in *".github/workflows/ci.yml"*) ;; *) fail "the comment does not name the file" ;; esac
# shellcheck disable=SC2016
case "$BODY" in *'`x`'*|*"<b>"*) fail "a file name was not made harmless" ;; esac
if printf '%s\n' "$BODY" | grep -qE '(^|[^`a-zA-Z0-9/])@someone'; then fail "a file name became a mention"; fi
expect_log "issue comment 7 --repo owner/repo"
expect_log "pr edit 41 --repo owner/repo --remove-label building"
end

begin "a long list of files outside is cut short"
for N in $(seq 1 80); do echo "infra/file-$N.sh"; done > "$CASE_DIR/many.txt"
report outside --repo owner/repo --issue 7 --pr 41 --files "$CASE_DIR/many.txt"
BODY=$(body_of 'pr comment 41')
if [ "$(printf '%s\n' "$BODY" | grep -c 'infra/file-')" -gt 30 ]; then fail "more than 30 files listed"; fi
case "$BODY" in *"50 more"*) ;; *) fail "the rest is not counted" ;; esac
end

begin "the token in the change: the branch goes first, then the words"
report token --repo owner/repo --issue 7 --pr 41 --branch factory/issue-7
expect_status 0
expect_log "api -X DELETE repos/owner/repo/git/refs/heads/factory/issue-7"
expect_order "-X DELETE" "issue comment 7"
case "$(body_of 'issue comment 7')" in *"token"*) ;; *) fail "the issue is not told about the token" ;; esac
end

begin "a finished run is reported on the issue with its numbers"
report finished --repo owner/repo --issue 7 --pr 41 --model claude-sonnet-5-5 --agent success --turns 14 --cost 0.42 --seconds 93 --denials 2
expect_status 0
BODY=$(body_of 'issue comment 7')
for PART in "#41" "claude-sonnet-5-5" "14 turns" "93 s" "0.42" "2 refused"; do
  case "$BODY" in *"$PART"*) ;; *) fail "the comment lacks: $PART" ;; esac
done
expect_no_log "needs-human"
expect_log "pr edit 41 --repo owner/repo --remove-label building"
end

begin "a pull request closed while the agent worked: branch deleted, the issue told"
report closed --repo owner/repo --issue 7 --pr 41 --branch factory/issue-7
expect_status 0
expect_log "api -X DELETE repos/owner/repo/git/refs/heads/factory/issue-7"
expect_log "issue comment 7 --repo owner/repo"
case "$(body_of 'issue comment 7')" in *"closed"*) ;; *) fail "the issue is not told" ;; esac
end

begin "the token: when the branch cannot be deleted the issue is still told, and the report fails"
echo 1 > "$STUB_GH_DIR/api-delete-ref.exit"
report token --repo owner/repo --issue 7 --pr 41 --branch factory/issue-7
if [ "$STATUS" -eq 0 ]; then fail "exit status 0"; fi
expect_log "issue comment 7 --repo owner/repo"
case "$(body_of 'issue comment 7')" in *"could not be deleted"*) ;; *) fail "the issue is not told the branch is still there" ;; esac
end

begin "files outside: when the label cannot be set the files are still named, and the report fails"
printf '%s\n' 'infra/deploy.sh' > "$CASE_DIR/outside.txt"
echo 1 > "$STUB_GH_DIR/pr-edit.exit"
report outside --repo owner/repo --issue 7 --pr 41 --files "$CASE_DIR/outside.txt"
if [ "$STATUS" -eq 0 ]; then fail "exit status 0"; fi
case "$(body_of 'pr comment 41')" in *"infra/deploy.sh"*) ;; *) fail "the files are not named" ;; esac
expect_log "issue comment 7 --repo owner/repo"
end

begin "no change: when the pull request cannot be closed the branch is still deleted and the issue told"
echo 1 > "$STUB_GH_DIR/pr-close.exit"
report empty --repo owner/repo --issue 7 --pr 41 --branch factory/issue-7 --agent success
if [ "$STATUS" -eq 0 ]; then fail "exit status 0"; fi
expect_log "api -X DELETE repos/owner/repo/git/refs/heads/factory/issue-7"
expect_log "issue comment 7 --repo owner/repo"
end

begin "a run that ended in an error after a commit is marked needs-human"
report finished --repo owner/repo --issue 7 --pr 41 --model claude-sonnet-5-5 --agent failure --turns 3 --cost 0.1 --seconds 20 --denials 0
expect_status 0
expect_log "pr edit 41 --repo owner/repo --add-label needs-human"
case "$(body_of 'issue comment 7')" in *"incomplete"*) ;; *) fail "the issue is not told the work may be incomplete" ;; esac
end

begin "a too-large change is marked needs-human"
report too-many --repo owner/repo --issue 7 --pr 41
expect_status 0
expect_log "pr edit 41 --repo owner/repo --add-label needs-human"
expect_log "issue comment 7 --repo owner/repo"
expect_log "pr edit 41 --repo owner/repo --remove-label building"
end

begin "a change that could not be read back is marked needs-human and stays unjudged"
report unchecked --repo owner/repo --issue 7 --pr 41
expect_status 0
expect_log "pr edit 41 --repo owner/repo --add-label needs-human"
expect_no_log "--remove-label building"
expect_log "pr comment 41 --repo owner/repo"
expect_log "issue comment 7 --repo owner/repo"
end

for JUNK in "--turns 1;id" "--cost \$(id)" "--seconds 9x" "--denials -1" "--pr 0" "--pr 41;id" "--model a b" "--agent maybe"; do
  begin "refused report value: $JUNK"
  # shellcheck disable=SC2086
  report finished --repo owner/repo --issue 7 --pr 41 --model m --agent success --turns 1 --cost 0 --seconds 1 --denials 0 ${JUNK%% *} "${JUNK#* }"
  expect_status 2
  expect_no_calls
  end
done

begin "when GitHub refuses a comment the report fails"
echo 1 > "$STUB_GH_DIR/issue-comment.exit"
report refuse --repo owner/repo --issue 7 --reason closed
if [ "$STATUS" -eq 0 ]; then fail "exit status 0"; fi
end

begin "an unknown report is refused"
report celebrate --repo owner/repo --issue 7
expect_status 2
expect_no_calls
end

echo "$PASSED passed, $FAILED failed"
[ "$FAILED" -eq 0 ]
