#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: apply.sh --repo OWNER/NAME --pr N --issue N --round 1|2|3 --decision D --summary TEXT" >&2
  echo "       --pr 0 applies a rebuild decision (REBUILD or NEEDS_HUMAN) to the issue only" >&2
  exit 2
}

repo=""
pr=""
issue=""
round=""
decision=""
summary=""
while [ $# -gt 0 ]; do
  case $1 in
    --repo) repo=${2:-}; shift 2 ;;
    --pr) pr=${2:-}; shift 2 ;;
    --issue) issue=${2:-}; shift 2 ;;
    --round) round=${2:-}; shift 2 ;;
    --decision) decision=${2:-}; shift 2 ;;
    --summary) summary=${2:-}; shift 2 ;;
    *) usage ;;
  esac
done
[ -n "$repo" ] && [ -n "$summary" ] || usage
[[ $pr =~ ^[0-9]+$ ]] && [[ $issue =~ ^[0-9]+$ ]] || usage
case $round in 1 | 2 | 3) ;; *) usage ;; esac

if [ "$pr" -eq 0 ]; then
  [ "$issue" -gt 0 ] || usage
  case $decision in
    REBUILD)
      gh issue comment "$issue" --repo "$repo" --body "$summary"
      gh issue edit "$issue" --repo "$repo" --remove-label needs-analysis --remove-label attempt-1 --add-label attempt-2
      ;;
    NEEDS_HUMAN)
      gh issue comment "$issue" --repo "$repo" --body "$summary"
      gh issue edit "$issue" --repo "$repo" --remove-label needs-analysis --add-label needs-human
      ;;
    *) usage ;;
  esac
  exit 0
fi

case $decision in
  PASS | RERUN_REVIEW | NEEDS_HUMAN) ;;
  FIX) [ "$round" -lt 3 ] || usage ;;
  SCRAP) [ "$issue" -gt 0 ] || usage ;;
  *) usage ;;
esac

gh pr comment "$pr" --repo "$repo" --body "$summary"
case $decision in
  PASS)
    gh pr edit "$pr" --repo "$repo" --remove-label needs-fix --add-label review-passed
    ;;
  FIX)
    gh pr edit "$pr" --repo "$repo" --remove-label "review-round-$round" --add-label "review-round-$((round + 1))" --add-label needs-fix
    ;;
  SCRAP)
    gh pr close "$pr" --repo "$repo" --delete-branch --comment "Scrapped by the review gate. Analysis follows on #$issue."
    gh issue edit "$issue" --repo "$repo" --add-label needs-analysis
    ;;
  NEEDS_HUMAN)
    gh pr edit "$pr" --repo "$repo" --add-label needs-human
    if [ "$issue" -gt 0 ]; then
      gh issue edit "$issue" --repo "$repo" --add-label needs-human
    fi
    ;;
esac
