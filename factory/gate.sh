#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: gate.sh review --round 1|2|3 --ci success|failure --findings PATH --review-try 1|2" >&2
  echo "       gate.sh rebuild --attempt 1|2 --cause spec|plan|implementation|review" >&2
  exit 2
}

decide() {
  printf '%s\n%s\n' "$1" "$2"
  exit 0
}

review() {
  local round="" ci="" findings="" try=1
  while [ $# -gt 0 ]; do
    case $1 in
      --round) round=${2:-}; shift 2 ;;
      --ci) ci=${2:-}; shift 2 ;;
      --findings) findings=${2:-}; shift 2 ;;
      --review-try) try=${2:-}; shift 2 ;;
      *) usage ;;
    esac
  done
  case $round in 1 | 2 | 3) ;; *) usage ;; esac
  case $ci in success | failure) ;; *) usage ;; esac
  case $try in 1 | 2) ;; *) usage ;; esac

  local head="Round $round"
  if [ "$ci" = failure ]; then
    if [ "$round" -eq 3 ]; then decide SCRAP "$head · CI red → scrap (round 3)"; fi
    decide FIX "$head · CI red → fix"
  fi
  head="$head · CI green"

  if [ -z "$findings" ] || [ ! -f "$findings" ] ||
    ! jq -e 'type == "array" and all(.[]; type == "object" and (.severity | type) == "string")' "$findings" > /dev/null 2>&1; then
    if [ "$try" -eq 1 ]; then decide RERUN_REVIEW "$head · findings unreadable → run the review again"; fi
    decide NEEDS_HUMAN "$head · findings unreadable twice → needs human"
  fi

  local critical major
  critical=$(jq '[.[] | select((.severity | ascii_downcase) == "critical")] | length' "$findings")
  major=$(jq '[.[] | select((.severity | ascii_downcase) == "major")] | length' "$findings")
  head="$head · critical $critical · major $major"

  if [ "$critical" -ge 6 ]; then decide SCRAP "$head → scrap (critical ≥ 6)"; fi
  if [ $((critical + major)) -eq 0 ]; then decide PASS "$head → pass"; fi
  if [ "$round" -eq 3 ]; then decide SCRAP "$head → scrap (round 3)"; fi
  decide FIX "$head → fix"
}

rebuild() {
  local attempt="" cause=""
  while [ $# -gt 0 ]; do
    case $1 in
      --attempt) attempt=${2:-}; shift 2 ;;
      --cause) cause=${2:-}; shift 2 ;;
      *) usage ;;
    esac
  done
  case $attempt in 1 | 2) ;; *) usage ;; esac
  case $cause in spec | plan | implementation | review) ;; *) usage ;; esac

  local head="Attempt $attempt · cause $cause"
  if [ "$cause" = review ]; then decide NEEDS_HUMAN "$head → needs human (review was wrong)"; fi
  if [ "$attempt" -eq 2 ]; then decide NEEDS_HUMAN "$head → needs human (rebuild already used)"; fi
  decide REBUILD "$head → rebuild"
}

case ${1:-} in
  review) shift; review "$@" ;;
  rebuild) shift; rebuild "$@" ;;
  *) usage ;;
esac
