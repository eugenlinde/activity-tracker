#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: conflicts.sh --repo OWNER/NAME [--merged N]" >&2
  exit 2
}

repo=""
merged=""
while [ $# -gt 0 ]; do
  case $1 in
    --repo) repo=${2:-}; shift 2 ;;
    --merged) merged=${2:-}; shift 2 ;;
    *) usage ;;
  esac
done
[[ $repo =~ ^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$ ]] || usage
[ -z "$merged" ] || [[ $merged =~ ^[1-9][0-9]{0,6}$ ]] || usage

wait_seconds=${FACTORY_WAIT_SECONDS:-10}
[[ $wait_seconds =~ ^[0-9]{1,3}$ ]] || wait_seconds=10
tries=6

after=""
if [ -n "$merged" ]; then
  after=" after #$merged was merged"
fi

say() {
  gh issue comment "$1" --repo "$repo" --body "**Conflict check:** $2"
}

read_pr() {
  local pr=$1 try view=""
  for ((try = 1; try <= tries; try++)); do
    view=$(gh pr view "$pr" --repo "$repo" --json state,mergeable,labels) || return 1
    jq -e '.state != "OPEN" or any(.labels[]?; .name == "needs-human" or .name == "building") or .mergeable != "UNKNOWN"' <<< "$view" > /dev/null 2>&1 && break
    if [ "$try" -lt "$tries" ]; then sleep "$wait_seconds"; fi
  done
  jq -r 'if .state != "OPEN" then "CLOSED" elif any(.labels[]?; .name == "needs-human" or .name == "building") then "SKIPPED" else .mergeable end' <<< "$view"
}

others_commits() {
  gh api --paginate "repos/$repo/pulls/$1/commits" |
    jq -s 'add // [] | map(select((.parents | length) == 1 and ((.author.login // "") as $who | ($who == "claude[bot]" and .commit.verification.verified == true) or $who == "github-actions[bot]" | not))) | length'
}

stop_on() {
  local pr=$1 issue=$2 text=$3
  gh pr edit "$pr" --repo "$repo" --add-label needs-human || return 1
  gh issue edit "$issue" --repo "$repo" --add-label needs-human || return 1
  say "$issue" "$text"
}

rebuild() {
  local pr=$1 issue=$2 deleted=0
  gh issue edit "$issue" --repo "$repo" --remove-label attempt-1 --add-label attempt-2 --add-label queued || return 1
  if ! gh pr close "$pr" --repo "$repo" --comment "Closed: in conflict with \`main\`. The issue is built again from its spec on the current \`main\`; this pull request keeps the diff and every review."; then
    gh issue edit "$issue" --repo "$repo" --remove-label attempt-2 --remove-label queued || true
    return 1
  fi
  gh api -X DELETE "repos/$repo/git/refs/heads/factory/issue-$issue" > /dev/null || deleted=1
  say "$issue" "pull request #$pr was in conflict with \`main\`$after. Built again from the spec, first in line." || return 1
  return "$deleted"
}

check() {
  local pr=$1 issue=$2 state others labels
  state=$(read_pr "$pr") || return 1
  case $state in
    CONFLICTING) ;;
    MERGEABLE) echo "pr=$pr result=mergeable"; return 0 ;;
    CLOSED) echo "pr=$pr result=closed"; return 0 ;;
    SKIPPED) echo "pr=$pr result=skipped"; return 0 ;;
    *) echo "pr=$pr result=unknown"; return 0 ;;
  esac
  others=$(others_commits "$pr") || return 1
  [[ $others =~ ^[0-9]+$ ]] || return 1
  labels=$(gh issue view "$issue" --repo "$repo" --json labels) || return 1
  jq -e 'type == "object"' <<< "$labels" > /dev/null 2>&1 || return 1
  if [ "$others" -gt 0 ]; then
    stop_on "$pr" "$issue" "pull request #$pr is in conflict with \`main\` and has your own commits, so the factory does not rebuild it. Resolve the conflict, or close it and remove and add \`approved\` to build it again." || return 1
    echo "pr=$pr result=own-commits"
  elif jq -e 'any(.labels[]?; .name == "attempt-2")' <<< "$labels" > /dev/null; then
    stop_on "$pr" "$issue" "pull request #$pr is in conflict with \`main\` again after its rebuild. The factory stops here." || return 1
    echo "pr=$pr result=limit"
  else
    rebuild "$pr" "$issue" || return 1
    echo "pr=$pr result=rebuild"
  fi
}

list=$(gh api --paginate "repos/$repo/pulls?state=open&per_page=100")
rows=$(jq -rs --arg repo "$repo" 'add // [] | .[] | select(.head.repo.full_name == $repo and any(.labels[]?; .name == "factory")) | [(.number | tostring), .head.ref] | @tsv' <<< "$list")

status=0
while IFS=$'\t' read -r pr branch; do
  [ -n "$pr" ] || continue
  [[ $pr =~ ^[1-9][0-9]{0,6}$ ]] || continue
  [[ $branch =~ ^factory/issue-([1-9][0-9]{0,6})$ ]] || continue
  issue=${BASH_REMATCH[1]}
  if ! check "$pr" "$issue"; then
    echo "pr=$pr result=failed"
    status=1
  fi
done <<< "$rows"

exit "$status"
