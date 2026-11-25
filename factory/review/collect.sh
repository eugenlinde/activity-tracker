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
  done <<< "$(printf '%s' "${FACTORY_AUTHORS:-}" | tr ',' '\n' | sed 's/^[[:space:]]*//; s/[[:space:]]*$//')"
  return 1
}

documentation() {
  case $1 in
    *..*) return 1 ;;
    docs/*.md|README.md|*/README.md) return 0 ;;
    .claude/NOTES.md|.claude/DECISIONS.md|.claude/ROADMAP.md|.claude/SPEC.md) return 0 ;;
    *) return 1 ;;
  esac
}

no() {
  echo "review=no"
  echo "reason=$1"
  exit 0
}

pull=$(gh pr view "$pr" --repo "$repo" --json number,state,isCrossRepository,author,headRefOid,title,body,labels)
[ -n "$pull" ] || { echo "GitHub gave no answer for the pull request" >&2; exit 1; }

head=$(jq -r '.headRefOid // ""' <<< "$pull")
[[ $head =~ ^[0-9a-f]{40}$ ]] || { echo "GitHub gave no commit for the pull request" >&2; exit 1; }
echo "head=$head"

[ "$(jq -r '.state' <<< "$pull")" = "OPEN" ] || no closed
[ "$(jq -r '.isCrossRepository' <<< "$pull")" = "false" ] || no fork
allowed_author "$(jq -r '.author.login // ""' <<< "$pull")" || no not-owner

files=$(gh api "repos/$repo/pulls/$pr/files" --paginate --jq '.[] | [.filename, (.previous_filename // "")] | @tsv') || no failed
code=0
while IFS=$'\t' read -r path previous; do
  [ -n "$path" ] || continue
  documentation "$path" || code=1
  if [ -n "$previous" ]; then
    documentation "$previous" || code=1
  fi
done <<< "$files"
if jq -e 'any(.labels[]?; .name == "factory")' <<< "$pull" > /dev/null; then
  code=1
fi
[ "$code" -eq 1 ] || no docs-only

gh pr diff "$pr" --repo "$repo" > "$out/diff.patch" || no failed
[ "$(wc -c < "$out/diff.patch")" -le "$max_bytes" ] || no too-large

issue=none
issue_text=""
reference=$(jq -r '.body // ""' <<< "$pull" \
  | grep -ioE '(^|[^[:alnum:]_/#])(close[sd]?|fix(e[sd])?|resolve[sd]?|part of|implements?)[[:space:]]+#[0-9]{1,7}($|[^[:alnum:]_])' \
  | head -n 1 | grep -oE '#[0-9]+' | tr -d '#' || true)
if [ -n "$reference" ]; then
  issue_json=$(gh issue view "$reference" --repo "$repo" --json number,title,body,author 2>/dev/null) || issue_json=""
  if [ -n "$issue_json" ] && allowed_author "$(jq -r '.author.login // ""' <<< "$issue_json" 2>/dev/null)"; then
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
