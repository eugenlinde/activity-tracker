#!/usr/bin/env bash
set -uo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
QUEUE=$(dirname "$HERE")/queue
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
  mkdir -p "$CASE_DIR/gh/args"
  : > "$CASE_DIR/stub.log"
  export STUB_LOG="$CASE_DIR/stub.log" STUB_GH_DIR="$CASE_DIR/gh" FACTORY_WAIT_SECONDS=0
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

expect_log() {
  if ! grep -qxF -- "$1" "$STUB_LOG"; then fail "expected call: $1"; fi
}

expect_no_log() {
  if grep -qF -- "$1" "$STUB_LOG"; then fail "unexpected call: $1"; fi
}

expect_count() {
  local actual
  actual=$(grep -cxF -- "$1" "$STUB_LOG")
  if [ "$actual" -ne "$2" ]; then fail "call made $actual times, expected $2: $1"; fi
}

expect_order() {
  local first second
  first=$(grep -nxF -- "$1" "$STUB_LOG" | head -n 1 | cut -d: -f1)
  second=$(grep -nxF -- "$2" "$STUB_LOG" | head -n 1 | cut -d: -f1)
  if [ -z "$first" ] || [ -z "$second" ] || [ "$first" -ge "$second" ]; then fail "expected '$1' before '$2'"; fi
}

answer() {
  printf '%s\n' "$2" > "$STUB_GH_DIR/args/$(printf '%s' "$1" | tr -c 'A-Za-z0-9._-' '_' | cut -c1-200)"
}

answers() {
  local key n=0 text
  key="$STUB_GH_DIR/args/$(printf '%s' "$1" | tr -c 'A-Za-z0-9._-' '_' | cut -c1-200)"
  shift
  for text in "$@"; do
    n=$((n + 1))
    printf '%s\n' "$text" > "$key.$n"
  done
}

fails() {
  local key
  key="$STUB_GH_DIR/args/$(printf '%s' "$1" | tr -c 'A-Za-z0-9._-' '_' | cut -c1-200)"
  : > "$key"
  echo 1 > "$key.exit"
}

PULLS="api --paginate repos/o/r/pulls?state=open&per_page=100"

pr() {
  local number=$1
  local labels=${2:-factory,review-round-1} branch=${3:-factory/issue-$((number - 5))} fork=${4:-false}
  jq -cn --argjson number "$number" --arg labels "$labels" --arg branch "$branch" --argjson fork "$fork" \
    '{number: $number, head: {ref: $branch, repo: {full_name: (if $fork then "other/r" else "o/r" end)}}, labels: ($labels | split(",") | map(select(. != "") | {name: .}))}'
}

prs() {
  answer "$PULLS" "$(jq -cs . <<< "$(printf '%s\n' "$@")")"
}

view() {
  local state=$1 mergeable=$2 labels=${3:-factory,review-round-1}
  jq -cn --arg state "$state" --arg mergeable "$mergeable" --arg labels "$labels" \
    '{state: $state, mergeable: $mergeable, labels: ($labels | split(",") | map(select(. != "") | {name: .}))}'
}

mergeable() {
  local number=$1 answer list=()
  shift
  for answer in "$@"; do
    list+=("$(view OPEN "$answer")")
  done
  answers "pr view $number --repo o/r --json state,mergeable,labels" "${list[@]}"
}

commit() {
  local author=$1 verified=${2:-true} parents=${3:-1}
  jq -cn --arg author "$author" --argjson verified "$verified" --argjson parents "$parents" \
    '{author: (if $author == "" then null else {login: $author} end), commit: {verification: {verified: $verified}}, parents: [range($parents) | {sha: "x"}]}'
}

commits() {
  local number=$1
  shift
  answer "api --paginate repos/o/r/pulls/$number/commits" "$(jq -cs . <<< "$(printf '%s\n' "$@")")"
}

labels_of_issue() {
  local issue=$1 labels=${2:-}
  answer "issue view $issue --repo o/r --json labels" "$(jq -cn --arg labels "$labels" '{labels: ($labels | split(",") | map(select(. != "") | {name: .}))}')"
}

app_only() {
  commits "$1" "$(commit github-actions[bot])" "$(commit claude[bot])"
}

conflicts() {
  run "$QUEUE/conflicts.sh" --repo o/r "$@"
}

CLOSE_12="gh pr close 12 --repo o/r --comment Closed: in conflict with \`main\`. The issue is built again from its spec on the current \`main\`; this pull request keeps the diff and every review."

echo "conflicts.sh"

begin "a pull request that can be merged is left alone"
prs "$(pr 12)"
mergeable 12 MERGEABLE
conflicts
expect_status 0
expect_output "pr=12 result=mergeable"
expect_no_log "pr close"
expect_no_log "pr edit"
expect_no_log "issue"
end

begin "a conflict with only the factory's commits closes the pull request and puts the issue first in line"
prs "$(pr 12)"
mergeable 12 CONFLICTING
app_only 12
labels_of_issue 7 "approved,enhancement"
conflicts --merged 9
expect_status 0
expect_output "pr=12 result=rebuild"
expect_log "$CLOSE_12"
expect_log "gh api -X DELETE repos/o/r/git/refs/heads/factory/issue-7"
expect_log "gh issue edit 7 --repo o/r --remove-label attempt-1 --add-label attempt-2 --add-label queued"
expect_log "gh issue comment 7 --repo o/r --body **Conflict check:** pull request #12 was in conflict with \`main\` after #9 was merged. Built again from the spec, first in line."
end

begin "without a merged pull request the comment leaves out which merge caused it"
prs "$(pr 12)"
mergeable 12 CONFLICTING
app_only 12
labels_of_issue 7 "approved"
conflicts
expect_output "pr=12 result=rebuild"
expect_log "gh issue comment 7 --repo o/r --body **Conflict check:** pull request #12 was in conflict with \`main\`. Built again from the spec, first in line."
end

begin "a conflict on a pull request with the developer's commit asks for the developer"
prs "$(pr 12)"
mergeable 12 CONFLICTING
commits 12 "$(commit github-actions[bot])" "$(commit claude[bot])" "$(commit eugenlinde)"
labels_of_issue 7 "approved"
conflicts --merged 9
expect_status 0
expect_output "pr=12 result=own-commits"
expect_log "gh pr edit 12 --repo o/r --add-label needs-human"
expect_log "gh issue edit 7 --repo o/r --add-label needs-human"
expect_log "gh issue comment 7 --repo o/r --body **Conflict check:** pull request #12 is in conflict with \`main\` and has your own commits, so the factory does not rebuild it. Resolve the conflict, or close it and remove and add \`approved\` to build it again."
expect_no_log "pr close"
expect_no_log "DELETE"
expect_no_log "queued"
end

begin "a merge commit from Update branch is not the developer's work"
prs "$(pr 12)"
mergeable 12 CONFLICTING
commits 12 "$(commit github-actions[bot])" "$(commit claude[bot])" "$(commit eugenlinde true 2)"
labels_of_issue 7 "approved"
conflicts
expect_output "pr=12 result=rebuild"
end

begin "an unsigned commit in the app's name counts as someone else's"
prs "$(pr 12)"
mergeable 12 CONFLICTING
commits 12 "$(commit github-actions[bot])" "$(commit claude[bot] false)"
labels_of_issue 7 "approved"
conflicts
expect_output "pr=12 result=own-commits"
expect_no_log "pr close"
end

begin "a commit with no GitHub account counts as someone else's"
prs "$(pr 12)"
mergeable 12 CONFLICTING
commits 12 "$(commit github-actions[bot])" "$(commit "")"
labels_of_issue 7 "approved"
conflicts
expect_output "pr=12 result=own-commits"
end

begin "a second conflict after the rebuild stops the factory on the issue"
prs "$(pr 12)"
mergeable 12 CONFLICTING
app_only 12
labels_of_issue 7 "approved,attempt-2"
conflicts --merged 9
expect_status 0
expect_output "pr=12 result=limit"
expect_log "gh pr edit 12 --repo o/r --add-label needs-human"
expect_log "gh issue edit 7 --repo o/r --add-label needs-human"
expect_log "gh issue comment 7 --repo o/r --body **Conflict check:** pull request #12 is in conflict with \`main\` again after its rebuild. The factory stops here."
expect_no_log "pr close"
end

begin "an answer that stays unknown is left for the next run"
prs "$(pr 12)"
mergeable 12 UNKNOWN
conflicts
expect_status 0
expect_output "pr=12 result=unknown"
expect_count "gh pr view 12 --repo o/r --json state,mergeable,labels" 6
expect_no_log "pr close"
expect_no_log "pr edit"
expect_no_log "issue"
end

begin "an answer that becomes known is used"
prs "$(pr 12)"
mergeable 12 UNKNOWN UNKNOWN CONFLICTING
app_only 12
labels_of_issue 7 "approved"
conflicts
expect_output "pr=12 result=rebuild"
expect_count "gh pr view 12 --repo o/r --json state,mergeable,labels" 3
end

begin "a pull request waiting for the developer or for an agent is skipped"
prs "$(pr 12)" "$(pr 13)"
answer "pr view 12 --repo o/r --json state,mergeable,labels" "$(view OPEN CONFLICTING factory,review-round-2,needs-human)"
answer "pr view 13 --repo o/r --json state,mergeable,labels" "$(view OPEN CONFLICTING factory,review-round-1,building)"
conflicts
expect_status 0
expect_output "pr=12 result=skipped"
expect_output "pr=13 result=skipped"
expect_no_log "pr close"
expect_no_log "issue"
expect_count "gh pr view 12 --repo o/r --json state,mergeable,labels" 1
end

begin "the labels read with the pull request count, not those in the list"
prs "$(pr 12 factory,review-round-1,needs-human)"
mergeable 12 CONFLICTING
app_only 12
labels_of_issue 7 "approved"
conflicts
expect_output "pr=12 result=rebuild"
end

begin "a pull request that is closed by the time it is read is left alone"
prs "$(pr 12)"
answer "pr view 12 --repo o/r --json state,mergeable,labels" "$(view CLOSED CONFLICTING)"
conflicts
expect_status 0
expect_output "pr=12 result=closed"
expect_no_log "pr close"
expect_no_log "issue"
end

begin "a pull request closed while its answer is still unknown is read once"
prs "$(pr 12)"
answer "pr view 12 --repo o/r --json state,mergeable,labels" "$(view CLOSED UNKNOWN)"
conflicts
expect_output "pr=12 result=closed"
expect_count "gh pr view 12 --repo o/r --json state,mergeable,labels" 1
end

begin "open pull requests over several pages are all looked at"
printf '%s\n%s\n' "[$(pr 12)]" "[$(pr 14)]" > "$STUB_GH_DIR/args/$(printf '%s' "$PULLS" | tr -c 'A-Za-z0-9._-' '_' | cut -c1-200)"
mergeable 12 MERGEABLE
mergeable 14 MERGEABLE
conflicts
expect_output "pr=12 result=mergeable"
expect_output "pr=14 result=mergeable"
end

begin "a pull request without the factory label in the list is ignored"
prs "$(pr 12 enhancement)"
conflicts
expect_status 0
expect_no_log "pr view"
end

begin "a pull request from a fork or on another branch is ignored"
prs "$(pr 12 factory,review-round-1 factory/issue-7 true)" "$(pr 13 factory,review-round-1 feature/x)" "$(pr 14 factory,review-round-1 'factory/issue-7;id')"
conflicts
expect_status 0
expect_no_log "pr view"
end

begin "of two pull requests only the one in conflict is rebuilt"
prs "$(pr 12)" "$(pr 14)"
mergeable 12 MERGEABLE
mergeable 14 CONFLICTING
app_only 14
labels_of_issue 9 "approved"
conflicts --merged 3
expect_output "pr=12 result=mergeable"
expect_output "pr=14 result=rebuild"
expect_log "gh api -X DELETE repos/o/r/git/refs/heads/factory/issue-9"
expect_no_log "pr close 12"
end

begin "the issue is put in line before the pull request is closed"
prs "$(pr 12)"
mergeable 12 CONFLICTING
app_only 12
labels_of_issue 7 "approved"
conflicts
expect_order "gh issue edit 7 --repo o/r --remove-label attempt-1 --add-label attempt-2 --add-label queued" "$CLOSE_12"
end

begin "when the pull request cannot be closed the issue is taken out of line again"
prs "$(pr 12)"
mergeable 12 CONFLICTING
app_only 12
labels_of_issue 7 "approved"
fails "pr close 12 --repo o/r --comment Closed: in conflict with \`main\`. The issue is built again from its spec on the current \`main\`; this pull request keeps the diff and every review."
conflicts
expect_status 1
expect_log "gh issue edit 7 --repo o/r --remove-label attempt-2 --remove-label queued"
expect_no_log "DELETE"
expect_no_log "issue comment"
end

begin "when the issue cannot be put in line the pull request stays open"
prs "$(pr 12)"
mergeable 12 CONFLICTING
app_only 12
labels_of_issue 7 "approved"
fails "issue edit 7 --repo o/r --remove-label attempt-1 --add-label attempt-2 --add-label queued"
conflicts
expect_status 1
expect_no_log "pr close"
end

begin "a branch that cannot be deleted still puts the issue in line, and the run fails"
prs "$(pr 12)"
mergeable 12 CONFLICTING
app_only 12
labels_of_issue 7 "approved"
fails "api -X DELETE repos/o/r/git/refs/heads/factory/issue-7"
conflicts
expect_status 1
expect_log "gh issue edit 7 --repo o/r --remove-label attempt-1 --add-label attempt-2 --add-label queued"
expect_log "gh issue comment 7 --repo o/r --body **Conflict check:** pull request #12 was in conflict with \`main\`. Built again from the spec, first in line."
end

begin "issue labels that are not JSON are not read as a first attempt"
prs "$(pr 12)"
mergeable 12 CONFLICTING
app_only 12
answer "issue view 7 --repo o/r --json labels" 'not json'
conflicts
expect_status 1
expect_output "pr=12 result=failed"
expect_no_log "pr close"
end

begin "a failure on one pull request does not stop the next"
prs "$(pr 12)" "$(pr 14)"
mergeable 12 CONFLICTING
mergeable 14 CONFLICTING
fails "api --paginate repos/o/r/pulls/12/commits"
app_only 14
labels_of_issue 9 "approved"
conflicts
expect_status 1
expect_output "pr=12 result=failed"
expect_output "pr=14 result=rebuild"
expect_no_log "pr close 12"
end

begin "an issue whose labels cannot be read is not rebuilt"
prs "$(pr 12)"
mergeable 12 CONFLICTING
app_only 12
fails "issue view 7 --repo o/r --json labels"
conflicts
expect_status 1
expect_output "pr=12 result=failed"
expect_no_log "pr close"
end

begin "a list of pull requests that cannot be read fails"
fails "$PULLS"
conflicts
expect_status 1
expect_no_log "pr view"
end

begin "the list of factory pull requests is asked for"
prs
conflicts
expect_status 0
expect_log "gh $PULLS"
end

for MERGED in "9;id" "0" "x" "-1"; do
  begin "refused merged pull request: '$MERGED'"
  prs "$(pr 12)"
  conflicts --merged "$MERGED"
  expect_status 2
  if [ -s "$STUB_LOG" ]; then fail "GitHub was called"; fi
  end
done

begin "a repository name that is not owner/name is refused"
run "$QUEUE/conflicts.sh" --repo "o"
expect_status 2
if [ -s "$STUB_LOG" ]; then fail "GitHub was called"; fi
end

echo
echo "next.sh"

QUEUED="api --paginate repos/o/r/issues?labels=queued&state=all&per_page=100"

open_prs() {
  local number list=()
  for number in "$@"; do
    case $number in
      fork:*) list+=("$(pr "${number#fork:}" factory,review-round-1 factory/issue-1 true)") ;;
      plain:*) list+=("$(pr "${number#plain:}" enhancement feature/x)") ;;
      *) list+=("$(pr "$number")") ;;
    esac
  done
  if [ "${#list[@]}" -eq 0 ]; then
    answer "$PULLS" "[]"
  else
    prs "${list[@]}"
  fi
}

queued() {
  local item json="[]" number state labels
  for item in "$@"; do
    IFS=: read -r number state labels <<< "$item"
    json=$(jq -c --argjson n "$number" --arg state "$state" --arg labels "queued,$labels" \
      '. + [{number: $n, state: ($state | ascii_downcase), labels: ($labels | split(",") | map(select(. != "") | {name: .}))}]' <<< "$json")
  done
  answer "$QUEUED" "$json"
}

next() {
  run "$QUEUE/next.sh" --repo o/r
}

START_20="gh workflow run implement.yml --repo o/r --ref main -f issue=20"
START_22="gh workflow run implement.yml --repo o/r --ref main -f issue=22"
START_25="gh workflow run implement.yml --repo o/r --ref main -f issue=25"

begin "two open pull requests start nothing"
open_prs 12 14
queued 20:OPEN:approved
next
expect_status 0
expect_output "waiting=20"
expect_no_log "workflow run"
expect_no_log "issue edit"
end

begin "more open pull requests than the limit start nothing"
open_prs 12 14 16
queued 20:OPEN:approved
next
expect_status 0
expect_no_log "workflow run"
end

begin "one free slot starts the rebuild before a lower number"
open_prs 12
queued 20:OPEN:approved 25:OPEN:approved,attempt-2
next
expect_status 0
expect_output "started=25"
expect_output "waiting=20"
expect_log "gh issue edit 25 --repo o/r --remove-label queued"
expect_log "$START_25"
expect_order "gh issue edit 25 --repo o/r --remove-label queued" "$START_25"
expect_no_log "$START_20"
end

begin "two free slots start two, lowest number after the rebuilds"
open_prs
queued 22:OPEN:approved 20:OPEN:approved 25:OPEN:approved,attempt-2
next
expect_status 0
expect_output "started=25"
expect_output "started=20"
expect_output "waiting=22"
expect_log "$START_25"
expect_log "$START_20"
expect_no_log "$START_22"
expect_order "$START_25" "$START_20"
end

begin "a pull request from a fork takes no slot"
open_prs 12 fork:14
queued 20:OPEN:approved
next
expect_output "started=20"
end

begin "a pull request outside the factory takes no slot"
open_prs 12 plain:14
queued 20:OPEN:approved
next
expect_output "started=20"
end

begin "a pull request in the list of queued issues is not an issue"
open_prs
answer "$QUEUED" '[{"number": 30, "state": "open", "labels": [{"name": "queued"}, {"name": "approved"}], "pull_request": {"url": "x"}}]'
next
expect_status 0
expect_no_log "30"
end

begin "a queued issue without approved is taken out of the line"
open_prs
queued 20:OPEN:enhancement 22:OPEN:approved
next
expect_status 0
expect_output "dropped=20"
expect_output "started=22"
expect_log "gh issue edit 20 --repo o/r --remove-label queued"
expect_no_log "$START_20"
end

begin "a closed queued issue is taken out of the line"
open_prs 12 14
queued 20:CLOSED:approved
next
expect_status 0
expect_output "dropped=20"
expect_log "gh issue edit 20 --repo o/r --remove-label queued"
expect_no_log "workflow run"
end

begin "a queued issue that needs the developer is taken out of the line"
open_prs
queued 20:OPEN:approved,needs-human
next
expect_output "dropped=20"
expect_no_log "$START_20"
end

begin "a closed queued issue is not started even with a free slot"
open_prs
queued 20:CLOSED:approved
next
expect_output "dropped=20"
expect_no_log "workflow run"
end

begin "queued issues over several pages are all read"
open_prs
printf '%s\n%s\n' '[{"number": 20, "state": "open", "labels": [{"name": "queued"}, {"name": "approved"}]}]' '[{"number": 22, "state": "open", "labels": [{"name": "queued"}, {"name": "approved"}]}]' > "$STUB_GH_DIR/args/$(printf '%s' "$QUEUED" | tr -c 'A-Za-z0-9._-' '_' | cut -c1-200)"
next
expect_output "started=20"
expect_output "started=22"
end

begin "nothing queued does nothing"
open_prs
queued
next
expect_status 0
expect_no_log "issue edit"
expect_no_log "workflow run"
end

begin "the queued issues are asked for, open and closed"
open_prs
queued
next
expect_log "gh $QUEUED"
expect_log "gh $PULLS"
end

begin "when a build cannot be started the issue goes back in line"
open_prs
queued 20:OPEN:approved 22:OPEN:approved
fails "workflow run implement.yml --repo o/r --ref main -f issue=20"
next
expect_status 1
expect_log "gh issue edit 20 --repo o/r --add-label queued"
expect_output "started=22"
end

begin "a list of queued issues that cannot be read fails"
open_prs
fails "$QUEUED"
next
expect_status 1
expect_no_log "workflow run"
end

begin "a list of pull requests that cannot be read fails"
fails "$PULLS"
queued 20:OPEN:approved
next
expect_status 1
expect_no_log "workflow run"
expect_no_log "issue edit"
end

begin "a repository name that is not owner/name is refused"
run "$QUEUE/next.sh" --repo "o"
expect_status 2
if [ -s "$STUB_LOG" ]; then fail "GitHub was called"; fi
end

echo
echo "$PASSED passed, $FAILED failed"
[ "$FAILED" -eq 0 ]
