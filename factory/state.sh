#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: state.sh --repo OWNER/NAME --pr N --head SHA" >&2
  exit 2
}

broken() {
  echo "error=$1"
  exit 3
}

repo=""
pr=""
head=""
while [ $# -gt 0 ]; do
  case $1 in
    --repo) repo=${2:-}; shift 2 ;;
    --pr) pr=${2:-}; shift 2 ;;
    --head) head=${2:-}; shift 2 ;;
    *) usage ;;
  esac
done
[ -n "$repo" ] && [[ $pr =~ ^[0-9]+$ ]] && [[ $head =~ ^[0-9a-f]{40}$ ]] || usage

pr_json=$(gh pr view "$pr" --repo "$repo" --json state,labels,closingIssuesReferences,headRefOid)

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

stop() {
  echo "act=no"
  echo "reason=$1"
  exit 0
}

has_label() {
  jq -e --arg name "$1" 'any(.labels[]; .name == $name)' <<< "$pr_json" > /dev/null
}

has_label needs-human && stop needs-human
has_label review-passed && stop review-passed
has_label building && stop building
[ "$(jq -r .headRefOid <<< "$pr_json")" = "$head" ] || stop moved-on
author=$(gh api "repos/$repo/commits/$head" --jq '(.author.login // "") + " " + (.commit.verification.verified | tostring)')
[ "$author" = "claude[bot] true" ] || stop not-the-app
echo "act=yes"

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
