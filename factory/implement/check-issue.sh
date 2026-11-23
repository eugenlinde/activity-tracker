#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: check-issue.sh --repo OWNER/NAME --issue N --sender LOGIN --out DIR" >&2
  exit 2
}

HERE=$(cd "$(dirname "$0")" && pwd)

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
[[ $repo =~ ^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$ ]] && [[ $issue =~ ^[1-9][0-9]{0,6}$ ]] && [ -n "$sender" ] && [ -n "$out" ] || usage

owner=${repo%%/*}
branch="factory/issue-$issue"

no() {
  echo "build=no"
  echo "reason=$1"
  if [ -n "${2:-}" ]; then
    echo "pr=$2"
  fi
  exit 0
}

lower() {
  printf '%s' "$1" | tr '[:upper:]' '[:lower:]'
}

[ "$(lower "$sender")" = "$(lower "$owner")" ] || no not-owner-label

json=$(gh issue view "$issue" --repo "$repo" --json number,state,title,body,author,labels) || no failed
jq -e 'type == "object" and (.state | type) == "string"' <<< "$json" > /dev/null 2>&1 || no failed

[ "$(jq -r '.state' <<< "$json")" = OPEN ] || no closed
[ "$(lower "$(jq -r '.author.login // ""' <<< "$json")")" = "$(lower "$owner")" ] || no not-owner-issue
jq -e 'any(.labels[]?; .name == "approved")' <<< "$json" > /dev/null || no no-label

title=$(jq -r '.title // ""' <<< "$json" | tr '\n\r\t' '   ' | sed 's/^ *//; s/ *$//' | cut -c1-200)
[ -n "$title" ] || no empty-spec

open=$(gh pr list --repo "$repo" --head "$branch" --state open --json number,isCrossRepository) || no failed
jq -e 'type == "array"' <<< "$open" > /dev/null 2>&1 || no failed
number=$(jq -r '[.[] | select(.isCrossRepository == false)][0].number // ""' <<< "$open")
if [ -n "$number" ]; then
  [[ $number =~ ^[1-9][0-9]{0,6}$ ]] || no failed
  no pr-open "$number"
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
