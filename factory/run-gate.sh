#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: run-gate.sh --repo OWNER/NAME --pr N --ci success|failure --findings PATH --review-try 1|2" >&2
  exit 2
}

HERE=$(cd "$(dirname "$0")" && pwd)

repo=""
pr=""
ci=""
findings=""
try=1
while [ $# -gt 0 ]; do
  case $1 in
    --repo) repo=${2:-}; shift 2 ;;
    --pr) pr=${2:-}; shift 2 ;;
    --ci) ci=${2:-}; shift 2 ;;
    --findings) findings=${2:-}; shift 2 ;;
    --review-try) try=${2:-}; shift 2 ;;
    *) usage ;;
  esac
done
[ -n "$repo" ] && [ -n "$pr" ] && [ -n "$ci" ] || usage

field() {
  sed -n "s/^$1=//p" <<< "$state"
}

status=0
state=$("$HERE/state.sh" --repo "$repo" --pr "$pr") || status=$?
printf '%s\n' "$state"

if [ "$status" -eq 3 ]; then
  issue=$(field issue)
  round=$(field round)
  "$HERE/apply.sh" --repo "$repo" --pr "$pr" --issue "${issue:-0}" --round "${round:-1}" \
    --decision NEEDS_HUMAN --summary "Factory state is broken ($(field error)) → needs human"
  exit 1
fi
[ "$status" -eq 0 ] || exit "$status"

if [ "$(field factory)" != true ]; then
  echo "Not a factory PR, nothing to do"
  exit 0
fi

round=$(field round)
issue=$(field issue)
decision=$("$HERE/gate.sh" review --round "$round" --ci "$ci" --findings "$findings" --review-try "$try")
printf '%s\n' "$decision"

"$HERE/apply.sh" --repo "$repo" --pr "$pr" --issue "$issue" --round "$round" \
  --decision "$(sed -n 1p <<< "$decision")" --summary "$(sed -n 2p <<< "$decision")"
