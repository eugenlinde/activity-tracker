#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: check-issue.sh --repo OWNER/NAME --issue N [--sender LOGIN] --out DIR" >&2
  echo "       --sender: who sent the label event; without it, who added the label is read from the issue's history" >&2
  exit 2
}

HERE=$(cd "$(dirname "$0")" && pwd)
# shellcheck disable=SC1091
. "$HERE/../limits.sh"

repo=""
issue=""
sender=""
out=""
while [ $# -gt 0 ]; do
  case $1 in
    --repo) repo=${2:-}; shift 2 ;;
    --issue) issue=${2:-}; shift 2 ;;
    --sender) sender=${2:-}; shift 2 ;;
    --out) out=${2:-}; shift 2 ;;
    *) usage ;;
  esac
done
[[ $repo =~ ^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$ ]] && [[ $issue =~ ^[1-9][0-9]{0,6}$ ]] && [ -n "$out" ] || usage

owner=${repo%%/*}
branch="factory/issue-$issue"

no() {
  echo "build=no"
  echo "reason=$1"
  if [ -n "${2:-}" ]; then
    echo "pr=$2"
  fi
  if [ -n "${3:-}" ]; then
    echo "open=$3"
  fi
  exit 0
}

lower() {
  printf '%s' "$1" | tr '[:upper:]' '[:lower:]'
}

if [ -n "$sender" ] && [ "$(lower "$sender")" != "$(lower "$owner")" ]; then
  no not-owner-label
fi

json=$(gh issue view "$issue" --repo "$repo" --json number,state,title,body,author,labels) || no failed
jq -e 'type == "object" and (.state | type) == "string"' <<< "$json" > /dev/null 2>&1 || no failed

[ "$(jq -r '.state' <<< "$json")" = OPEN ] || no closed
[ "$(lower "$(jq -r '.author.login // ""' <<< "$json")")" = "$(lower "$owner")" ] || no not-owner-issue
jq -e 'any(.labels[]?; .name == "approved")' <<< "$json" > /dev/null || no no-label

if [ -z "$sender" ]; then
  events=$(gh api --paginate "repos/$repo/issues/$issue/events") || no failed
  labeller=$(jq -rs 'add // [] | map(select(.event == "labeled" and .label.name == "approved")) | last | .actor.login // ""' <<< "$events" 2>/dev/null) || no failed
  [ -n "$labeller" ] && [ "$(lower "$labeller")" = "$(lower "$owner")" ] || no not-owner-label
fi

title=$(jq -r '.title // ""' <<< "$json" | tr '\n\r\t' '   ' | sed 's/^ *//; s/ *$//' | cut -c1-200)
[ -n "$title" ] || no empty-spec

open=$(gh pr list --repo "$repo" --head "$branch" --state open --json number,isCrossRepository) || no failed
jq -e 'type == "array"' <<< "$open" > /dev/null 2>&1 || no failed
number=$(jq -r '[.[] | select(.isCrossRepository == false)][0].number // ""' <<< "$open")
if [ -n "$number" ]; then
  [[ $number =~ ^[1-9][0-9]{0,6}$ ]] || no failed
  no pr-open "$number"
fi

factory=$(gh api --paginate "repos/$repo/pulls?state=open&per_page=100") || no failed
open_list=$(jq -rs --arg repo "$repo" '[add // [] | .[] | select(.head.repo.full_name == $repo and any(.labels[]?; .name == "factory")) | .number | tostring] | join(" ")' <<< "$factory" 2>/dev/null) || no failed
read -r -a open_prs <<< "$open_list"
if [ "${#open_prs[@]}" -ge "$FACTORY_OPEN_LIMIT" ]; then
  no full "" "$open_list"
fi

mkdir -p "$out"
printf '%s\n' "$title" > "$out/title.txt"
jq -r '"# " + (.title // "") + "\n\n" + (.body // "")' <<< "$json" > "$out/spec.md"
{
  cat "$HERE/prompt.md"
  echo
  echo "## The spec (issue $issue)"
  echo
  cat "$out/spec.md"
} > "$out/prompt.md"

echo "build=yes"
echo "branch=$branch"
