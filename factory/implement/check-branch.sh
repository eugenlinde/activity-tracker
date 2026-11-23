#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: check-branch.sh --repo OWNER/NAME --branch factory/issue-N --out DIR" >&2
  exit 2
}

repo=""
branch=""
out=""
while [ $# -gt 0 ]; do
  case $1 in
    --repo) repo=${2:-}; shift 2 ;;
    --branch) branch=${2:-}; shift 2 ;;
    --out) out=${2:-}; shift 2 ;;
    *) usage ;;
  esac
done
[[ $repo =~ ^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$ ]] && [[ $branch =~ ^factory/issue-[1-9][0-9]{0,6}$ ]] && [ -n "$out" ] || usage

mkdir -p "$out"
: > "$out/outside.txt"

result() {
  echo "result=$1"
  echo "files=${2:-0}"
  exit 0
}

allowed() {
  case $1 in
    *..*|/*) return 1 ;;
    src/?*|frontend/src/?*|http/?*|docs/?*|build.gradle) return 0 ;;
  esac
  return 1
}

json=$(gh api "repos/$repo/compare/main...$branch") || result failed
jq -e '(.files | type) == "array"' <<< "$json" > /dev/null 2>&1 || result failed
count=$(jq '.files | length' <<< "$json")

if [ -n "${CLAUDE_CODE_OAUTH_TOKEN:-}" ]; then
  diff=$(gh api "repos/$repo/compare/main...$branch" -H "Accept: application/vnd.github.diff") || result failed
  if grep -qF -- "$CLAUDE_CODE_OAUTH_TOKEN" <<< "$diff" || grep -qF -- "$CLAUDE_CODE_OAUTH_TOKEN" <<< "$json"; then
    result token "$count"
  fi
fi

[ "$count" -gt 0 ] || result empty 0
[ "$count" -lt 300 ] || result too-many "$count"

while IFS= read -r path; do
  [ -n "$path" ] || continue
  allowed "$path" || printf '%s\n' "$path" >> "$out/outside.txt"
done <<< "$(jq -r '.files[] | .filename, (.previous_filename // empty)' <<< "$json")"

if [ -s "$out/outside.txt" ]; then
  result outside "$count"
fi
result ok "$count"
