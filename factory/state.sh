#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: state.sh --repo OWNER/NAME --pr N" >&2
  exit 2
}

broken() {
  echo "error=$1"
  exit 3
}

repo=""
pr=""
while [ $# -gt 0 ]; do
  case $1 in
    --repo) repo=${2:-}; shift 2 ;;
    --pr) pr=${2:-}; shift 2 ;;
    *) usage ;;
  esac
done
[ -n "$repo" ] && [[ $pr =~ ^[0-9]+$ ]] || usage

pr_json=$(gh pr view "$pr" --repo "$repo" --json state,labels,closingIssuesReferences)

if ! jq -e 'any(.labels[]; .name == "factory")' <<< "$pr_json" > /dev/null; then
  echo "factory=false"
  exit 0
fi
echo "factory=true"

if [ "$(jq -r .state <<< "$pr_json")" != OPEN ]; then
  echo "open=false"
  exit 0
fi
echo "open=true"

rounds=$(jq -r '[.labels[].name | select(test("^review-round-[123]$")) | ltrimstr("review-round-")] | join(" ")' <<< "$pr_json")
read -r -a round_list <<< "$rounds"
case ${#round_list[@]} in
  0) broken no-round-label ;;
  1) echo "round=${round_list[0]}" ;;
  *) broken many-round-labels ;;
esac

own_issues=$(jq -c --arg prefix "https://github.com/$repo/issues/" '[.closingIssuesReferences[] | select(.url | startswith($prefix)) | .number]' <<< "$pr_json")
[ "$(jq length <<< "$own_issues")" -eq 1 ] || broken no-linked-issue
issue=$(jq -r '.[0]' <<< "$own_issues")
echo "issue=$issue"

issue_json=$(gh issue view "$issue" --repo "$repo" --json labels)
attempts=$(jq -r '[.labels[].name | select(test("^attempt-[12]$")) | ltrimstr("attempt-")] | join(" ")' <<< "$issue_json")
read -r -a attempt_list <<< "$attempts"
case ${#attempt_list[@]} in
  0) echo "attempt=1" ;;
  1) echo "attempt=${attempt_list[0]}" ;;
  *) broken many-attempt-labels ;;
esac
