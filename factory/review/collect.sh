#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: collect.sh --repo OWNER/NAME --pr N --out DIR" >&2
  exit 2
}

repo=""
pr=""
out=""
while [ $# -gt 0 ]; do
  case $1 in
    --repo) repo=${2:-}; shift 2 ;;
    --pr) pr=${2:-}; shift 2 ;;
    --out) out=${2:-}; shift 2 ;;
    *) usage ;;
  esac
done
[[ $repo =~ ^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$ ]] && [[ $pr =~ ^[1-9][0-9]{0,6}$ ]] && [ -n "$out" ] || usage

owner=${repo%%/*}
max_bytes=${REVIEW_MAX_DIFF_BYTES:-400000}
mkdir -p "$out"

allowed_author() {
  local login=$1 extra
  [ "$login" = "$owner" ] && return 0
  while IFS= read -r extra; do
    [ -n "$extra" ] && [ "$login" = "$extra" ] && return 0
  done <<< "$(printf '%s' "${FACTORY_AUTHORS:-}" | tr ',' '\n')"
  return 1
}

no() {
  echo "review=no"
  echo "reason=$1"
  exit 0
}

pull=$(gh pr view "$pr" --repo "$repo" --json number,state,isCrossRepository,author,headRefOid,title,body)
[ -n "$pull" ] || { echo "GitHub gave no answer for the pull request" >&2; exit 1; }

head=$(jq -r '.headRefOid // ""' <<< "$pull")
[[ $head =~ ^[0-9a-f]{40}$ ]] || { echo "GitHub gave no commit for the pull request" >&2; exit 1; }
echo "head=$head"

[ "$(jq -r '.state' <<< "$pull")" = "OPEN" ] || no closed
[ "$(jq -r '.isCrossRepository' <<< "$pull")" = "false" ] || no fork
allowed_author "$(jq -r '.author.login // ""' <<< "$pull")" || no not-owner

files=$(gh pr diff "$pr" --repo "$repo" --name-only)
code=0
while IFS= read -r path; do
  [ -n "$path" ] || continue
  case $path in
    *..*) code=1 ;;
    docs/*|.claude/*|*.md) ;;
    *) code=1 ;;
  esac
done <<< "$files"
[ "$code" -eq 1 ] || no docs-only

gh pr diff "$pr" --repo "$repo" > "$out/diff.patch"
[ "$(wc -c < "$out/diff.patch")" -le "$max_bytes" ] || no too-large

issue=none
reference=$(jq -r '.body // ""' <<< "$pull" \
  | grep -ioE '(close[sd]?|fix(e[sd])?|resolve[sd]?|part of|implements?)[[:space:]]+#[0-9]{1,7}' | head -n 1 | grep -oE '[0-9]+$' || true)
issue_text=""
if [ -n "$reference" ]; then
  issue_json=$(gh issue view "$reference" --repo "$repo" --json number,title,body,author)
  if [ -n "$issue_json" ] && allowed_author "$(jq -r '.author.login // ""' <<< "$issue_json")"; then
    issue=$reference
    issue_text=$(jq -r '"# Issue " + (.number | tostring) + ": " + (.title // "") + "\n\n" + (.body // "")' <<< "$issue_json")
  fi
fi

{
  jq -r '"# Pull request " + (.number | tostring) + ": " + (.title // "") + "\n\n" + (.body // "")' <<< "$pull"
  echo
  if [ "$issue" = none ]; then
    echo "# Issue"
    echo
    echo "This pull request names no issue that can be used as its spec. Judge it against its own description."
  else
    printf '%s\n' "$issue_text"
  fi
} > "$out/spec.md"

echo "issue=$issue"
echo "review=yes"
