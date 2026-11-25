#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: prompt.sh --repo OWNER/NAME --issue N --out DIR (--findings FILE | --ci-log FILE --ci-job NAME)" >&2
  exit 2
}

HERE=$(cd "$(dirname "$0")" && pwd)

repo=""
issue=""
out=""
findings=""
log=""
job=""
while [ $# -gt 0 ]; do
  case $1 in
    --repo) repo=${2:-}; shift 2 ;;
    --issue) issue=${2:-}; shift 2 ;;
    --out) out=${2:-}; shift 2 ;;
    --findings) findings=${2:-}; shift 2 ;;
    --ci-log) log=${2:-}; shift 2 ;;
    --ci-job) job=${2:-}; shift 2 ;;
    *) usage ;;
  esac
done
[[ $repo =~ ^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$ ]] && [[ $issue =~ ^[1-9][0-9]{0,6}$ ]] && [ -n "$out" ] || usage
if [ -n "$findings" ]; then
  [ -z "$log" ] && [ -z "$job" ] || usage
else
  [ -n "$log" ] && [[ $job =~ ^[A-Za-z0-9][A-Za-z0-9\ ._-]{0,60}$ ]] || usage
fi

owner=${repo%%/*}
mkdir -p "$out"
rm -f "$out/prompt.md"

no() {
  echo "ready=no"
  echo "reason=$1"
  exit 0
}

lower() {
  printf '%s' "$1" | tr '[:upper:]' '[:lower:]'
}

json=$(gh issue view "$issue" --repo "$repo" --json number,title,body,author) || no issue-unreadable
jq -e 'type == "object"' <<< "$json" > /dev/null 2>&1 || no issue-unreadable
[ "$(lower "$(jq -r '.author.login // ""' <<< "$json")")" = "$(lower "$owner")" ] || no not-owner-issue

problem=$(mktemp)
if [ -n "$findings" ]; then
  jq -e 'type == "object" and (.findings | type == "array")' "$findings" > /dev/null 2>&1 || no findings-unreadable
  {
    echo "## The problem: the reviewer's findings"
    echo
    jq -r '.findings | to_entries[] | .value as $f
      | "### Finding \(.key + 1): \($f.severity // "?") (`\($f.rule // "?")`)\n\n"
      + "- Where: `\($f.file // "?")`" + (if ($f.line | type) == "number" then ", line \($f.line)" else "" end) + "\n"
      + "- What: \($f.what // "")\n"
      + "- Why: \($f.why // "")\n"' "$findings"
  } > "$problem"
else
  [ -f "$log" ] || no log-unreadable
  {
    echo "## The problem: CI is red"
    echo
    echo "The job \`$job\` failed. The last 300 lines of its failed step:"
    echo
    echo '~~~~ text'
    tail -n 300 "$log" | LC_ALL=C tr -d '\000-\010\013-\037\177' | sed 's/\[[0-9;]*m//g'
    echo '~~~~'
  } > "$problem"
fi

{
  cat "$HERE/prompt.md"
  echo
  cat "$problem"
  echo
  echo "## The spec (issue $issue)"
  echo
  jq -r '"# " + (.title // "") + "\n\n" + (.body // "")' <<< "$json"
} > "$out/prompt.md"
rm -f "$problem"

echo "ready=yes"
