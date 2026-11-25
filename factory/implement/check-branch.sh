#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: check-branch.sh --repo OWNER/NAME --branch factory/issue-N --pr N --out DIR [--before SHA]" >&2
  exit 2
}

repo=""
branch=""
pr=""
out=""
before=""
while [ $# -gt 0 ]; do
  case $1 in
    --repo) repo=${2:-}; shift 2 ;;
    --branch) branch=${2:-}; shift 2 ;;
    --pr) pr=${2:-}; shift 2 ;;
    --out) out=${2:-}; shift 2 ;;
    --before) before=${2:-}; shift 2 ;;
    *) usage ;;
  esac
done
[[ $repo =~ ^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$ ]] && [[ $branch =~ ^factory/issue-[1-9][0-9]{0,6}$ ]] \
  && [[ $pr =~ ^[1-9][0-9]{0,6}$ ]] && [ -n "$out" ] || usage
[ -z "$before" ] || [[ $before =~ ^[0-9a-f]{40}$ ]] || usage

mkdir -p "$out"
: > "$out/outside.txt"

result() {
  echo "result=$1"
  echo "files=${2:-0}"
  exit 0
}

token=${CLAUDE_CODE_OAUTH_TOKEN:-}
if [[ $token =~ [[:space:][:cntrl:]] ]]; then
  result failed
fi

json=$(gh api "repos/$repo/compare/main...$branch") || result failed
jq -e '(.files | type) == "array" and (.commits | type) == "array"' <<< "$json" > /dev/null 2>&1 || result failed
count=$(jq '.files | length' <<< "$json")
commits=$(jq '.commits | length' <<< "$json")

if [ -n "$token" ]; then
  [ "$commits" -le 50 ] || result too-many "$count"
  if grep -qF -- "$token" <<< "$json"; then
    result token "$count"
  fi
  diff=$(gh api "repos/$repo/compare/main...$branch" -H "Accept: application/vnd.github.diff") || result failed
  if grep -qF -- "$token" <<< "$diff"; then
    result token "$count"
  fi
  while IFS= read -r commit; do
    [[ $commit =~ ^[0-9a-f]{7,40}$ ]] || continue
    diff=$(gh api "repos/$repo/commits/$commit" -H "Accept: application/vnd.github.diff") || result failed
    if grep -qF -- "$token" <<< "$diff"; then
      result token "$count"
    fi
  done <<< "$(jq -r '.commits[].sha // empty' <<< "$json")"
fi

pull=$(gh api "repos/$repo/pulls/$pr") || result failed
state=$(jq -r '.state // ""' <<< "$pull" 2>/dev/null) || result failed
case $state in
  open) ;;
  closed) result closed "$count" ;;
  *) result failed ;;
esac

if [ -n "$before" ]; then
  [ "$(jq -r '.commits[-1].sha // ""' <<< "$json")" != "$before" ] || result unchanged "$count"
  newest=$(jq -r '.commits[-1] | (.parents[0].sha // "") + " " + (.author.login // "")' <<< "$json")
  [ "$newest" = "$before claude[bot]" ] || result moved "$count"
fi
[ "$count" -gt 0 ] || result empty 0
[ "$count" -lt 300 ] || result too-many "$count"

jq -r '
  def inside:
    (contains("\n") | not)
    and (startswith("src/") or startswith("frontend/src/") or startswith("http/") or startswith("docs/"))
    and (split("/") | all(. as $part
      | $part != "" and $part != ".."
      and ((["claude.md", "agents.md", ".claude", ".mcp.json", ".github", ".git"] | index($part | ascii_downcase)) == null)));
  [.files[] | .filename, (.previous_filename // empty)]
  | map(select(type == "string" and (inside | not)))
  | .[] | gsub("[\\x00-\\x1f]"; " ")
' <<< "$json" > "$out/outside.txt" || result failed

if [ -s "$out/outside.txt" ]; then
  result outside "$count"
fi
result ok "$count"
