#!/usr/bin/env bash
set -uo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
FACTORY=$(dirname "$HERE")
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
  mkdir -p "$CASE_DIR"
  : > "$CASE_DIR/stub.log"
  unset STUB_PR_JSON STUB_ISSUE_JSON STUB_GH_FAIL_ON
  export STUB_LOG="$CASE_DIR/stub.log"
}

run() {
  OUTPUT=$(PATH="$HERE/stubs:$PATH" "$@" 2>&1)
  STATUS=$?
}

fail() {
  CASE_OK=0
  echo "  FAIL [$CASE] $1"
}

findings() {
  local file="$CASE_DIR/findings-$RANDOM.json"
  printf '%s' "$1" > "$file"
  echo "$file"
}

repeat_severity() {
  local severity=$1 count=$2 json="[" i
  for ((i = 0; i < count; i++)); do
    [ "$i" -gt 0 ] && json+=","
    json+="{\"severity\":\"$severity\"}"
  done
  echo "$json]"
}

expect_status() {
  if [ "$STATUS" -ne "$1" ]; then fail "exit status $STATUS, expected $1"; fi
}

expect_line() {
  local actual
  actual=$(printf '%s\n' "$OUTPUT" | sed -n "${1}p")
  if [ "$actual" != "$2" ]; then fail "line $1 is '$actual', expected '$2'"; fi
}

expect_decision() {
  expect_status 0
  expect_line 1 "$1"
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

expect_log_exactly() {
  local expected actual
  expected=$(printf '%s\n' "$@")
  actual=$(cat "$STUB_LOG")
  if [ "$actual" != "$expected" ]; then
    fail "calls differ"
    printf '    expected:\n%s\n    actual:\n%s\n' "$expected" "$actual"
  fi
}

end() {
  if [ "$CASE_OK" -eq 1 ]; then
    PASSED=$((PASSED + 1))
    echo "  ok   [$CASE]"
  else
    FAILED=$((FAILED + 1))
    printf '    output:\n%s\n' "$OUTPUT"
  fi
}

GATE="$FACTORY/gate.sh"

review() {
  run "$GATE" review --round "$1" --ci "$2" --findings "$3" --review-try "${4:-1}"
}

begin "review_empty_round1"
review 1 success "$(findings '[]')"
expect_decision PASS
expect_line 2 "Round 1 · CI green · critical 0 · major 0 → pass"
end

begin "review_majors_round1"
review 1 success "$(findings '[{"severity":"major"},{"severity":"major"}]')"
expect_decision FIX
expect_line 2 "Round 1 · CI green · critical 0 · major 2 → fix"
end

begin "review_five_critical"
review 1 success "$(findings "$(repeat_severity critical 5)")"
expect_decision FIX
expect_line 2 "Round 1 · CI green · critical 5 · major 0 → fix"
end

begin "review_six_critical"
review 1 success "$(findings "$(repeat_severity critical 6)")"
expect_decision SCRAP
expect_line 2 "Round 1 · CI green · critical 6 · major 0 → scrap (critical ≥ 6)"
end

begin "review_six_critical_round3"
review 3 success "$(findings "$(repeat_severity critical 6)")"
expect_decision SCRAP
expect_line 2 "Round 3 · CI green · critical 6 · major 0 → scrap (critical ≥ 6)"
end

begin "review_round3_one_major"
review 3 success "$(findings '[{"severity":"major"}]')"
expect_decision SCRAP
expect_line 2 "Round 3 · CI green · critical 0 · major 1 → scrap (round 3)"
end

begin "review_round3_clean"
review 3 success "$(findings '[]')"
expect_decision PASS
expect_line 2 "Round 3 · CI green · critical 0 · major 0 → pass"
end

begin "review_round2_mixed"
review 2 success "$(findings '[{"severity":"critical"},{"severity":"major"},{"severity":"major"},{"severity":"major"}]')"
expect_decision FIX
expect_line 2 "Round 2 · CI green · critical 1 · major 3 → fix"
end

begin "review_ci_red_round1"
review 1 failure "$CASE_DIR/absent.json"
expect_decision FIX
expect_line 2 "Round 1 · CI red → fix"
end

begin "review_ci_red_round3"
review 3 failure "$CASE_DIR/absent.json"
expect_decision SCRAP
expect_line 2 "Round 3 · CI red → scrap (round 3)"
end

begin "review_case_and_minor"
review 1 success "$(findings '[{"severity":"Critical"},{"severity":"MAJOR"},{"severity":"minor"}]')"
expect_decision FIX
expect_line 2 "Round 1 · CI green · critical 1 · major 1 → fix"
end

begin "review_only_minor"
review 1 success "$(findings '[{"severity":"minor"}]')"
expect_decision PASS
expect_line 2 "Round 1 · CI green · critical 0 · major 0 → pass"
end

for bad in '' 'looks fine' '{}' 'null'; do
  begin "review_not_array_try1 '$bad'"
  review 1 success "$(findings "$bad")" 1
  expect_decision RERUN_REVIEW
  expect_line 2 "Round 1 · CI green · findings unreadable → run the review again"
  end

  begin "review_not_array_try2 '$bad'"
  review 1 success "$(findings "$bad")" 2
  expect_decision NEEDS_HUMAN
  expect_line 2 "Round 1 · CI green · findings unreadable twice → needs human"
  end
done

begin "review_findings_file_missing"
review 1 success "$CASE_DIR/absent.json" 1
expect_decision RERUN_REVIEW
end

begin "review_entry_no_severity"
review 1 success "$(findings '[{"file":"a"}]')" 1
expect_decision RERUN_REVIEW
end

begin "review_bad_round"
review 4 success "$(findings '[]')"
expect_status 2
end

begin "review_bad_ci"
review 1 maybe "$(findings '[]')"
expect_status 2
end

begin "unknown_subcommand"
run "$GATE" merge
expect_status 2
end

begin "rebuild_attempt1_impl"
run "$GATE" rebuild --attempt 1 --cause implementation
expect_decision REBUILD
expect_line 2 "Attempt 1 · cause implementation → rebuild"
end

begin "rebuild_attempt1_spec"
run "$GATE" rebuild --attempt 1 --cause spec
expect_decision REBUILD
expect_line 2 "Attempt 1 · cause spec → rebuild"
end

begin "rebuild_attempt1_review"
run "$GATE" rebuild --attempt 1 --cause review
expect_decision NEEDS_HUMAN
expect_line 2 "Attempt 1 · cause review → needs human (review was wrong)"
end

begin "rebuild_attempt2"
run "$GATE" rebuild --attempt 2 --cause plan
expect_decision NEEDS_HUMAN
expect_line 2 "Attempt 2 · cause plan → needs human (rebuild already used)"
end

begin "rebuild_bad_cause"
run "$GATE" rebuild --attempt 1 --cause other
expect_status 2
end

STATE="$FACTORY/state.sh"

pr_json() {
  local issues=$1 labels="" name
  shift
  for name in "$@"; do
    [ -n "$labels" ] && labels+=","
    labels+="{\"name\":\"$name\"}"
  done
  local refs="" n
  for n in $issues; do
    [ -n "$refs" ] && refs+=","
    refs+="{\"number\":$n}"
  done
  echo "{\"labels\":[$labels],\"closingIssuesReferences\":[$refs]}"
}

issue_json() {
  local labels="" name
  for name in "$@"; do
    [ -n "$labels" ] && labels+=","
    labels+="{\"name\":\"$name\"}"
  done
  echo "{\"labels\":[$labels]}"
}

begin "state_normal"
export STUB_PR_JSON STUB_ISSUE_JSON
STUB_PR_JSON=$(pr_json 7 factory review-round-2 enhancement)
STUB_ISSUE_JSON=$(issue_json attempt-1 bug)
run "$STATE" --repo o/r --pr 12
expect_status 0
expect_output "factory=true"
expect_output "round=2"
expect_output "issue=7"
expect_output "attempt=1"
expect_log "gh pr view 12 --repo o/r --json labels,closingIssuesReferences"
expect_log "gh issue view 7 --repo o/r --json labels"
end

begin "state_attempt_2"
export STUB_PR_JSON STUB_ISSUE_JSON
STUB_PR_JSON=$(pr_json 7 factory review-round-1)
STUB_ISSUE_JSON=$(issue_json attempt-2)
run "$STATE" --repo o/r --pr 12
expect_status 0
expect_output "attempt=2"
end

begin "state_not_factory"
export STUB_PR_JSON
STUB_PR_JSON=$(pr_json 7 review-round-1)
run "$STATE" --repo o/r --pr 12
expect_status 0
expect_output "factory=false"
expect_log_exactly "gh pr view 12 --repo o/r --json labels,closingIssuesReferences"
end

begin "state_no_round"
export STUB_PR_JSON
STUB_PR_JSON=$(pr_json 7 factory)
run "$STATE" --repo o/r --pr 12
expect_status 3
expect_output "error=no-round-label"
end

begin "state_two_rounds"
export STUB_PR_JSON
STUB_PR_JSON=$(pr_json 7 factory review-round-1 review-round-2)
run "$STATE" --repo o/r --pr 12
expect_status 3
expect_output "error=many-round-labels"
end

begin "state_no_issue"
export STUB_PR_JSON
STUB_PR_JSON=$(pr_json "" factory review-round-1)
run "$STATE" --repo o/r --pr 12
expect_status 3
expect_output "error=no-linked-issue"
end

begin "state_two_issues"
export STUB_PR_JSON
STUB_PR_JSON=$(pr_json "7 8" factory review-round-1)
run "$STATE" --repo o/r --pr 12
expect_status 3
expect_output "error=no-linked-issue"
end

begin "state_no_attempt_label"
export STUB_PR_JSON STUB_ISSUE_JSON
STUB_PR_JSON=$(pr_json 7 factory review-round-3)
STUB_ISSUE_JSON=$(issue_json)
run "$STATE" --repo o/r --pr 12
expect_status 0
expect_output "round=3"
expect_output "attempt=1"
end

begin "state_two_attempts"
export STUB_PR_JSON STUB_ISSUE_JSON
STUB_PR_JSON=$(pr_json 7 factory review-round-1)
STUB_ISSUE_JSON=$(issue_json attempt-1 attempt-2)
run "$STATE" --repo o/r --pr 12
expect_status 3
expect_output "error=many-attempt-labels"
end

begin "state_gh_fails"
export STUB_GH_FAIL_ON="pr view"
run "$STATE" --repo o/r --pr 12
expect_status 1
end

begin "state_missing_args"
run "$STATE" --pr 12
expect_status 2
end

echo
echo "$PASSED passed, $FAILED failed"
[ "$FAILED" -eq 0 ]
