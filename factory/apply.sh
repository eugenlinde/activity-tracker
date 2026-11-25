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

failed=0

on_issue() {
  [ "$issue" -gt 0 ] || return 0
  gh issue comment "$issue" --repo "$repo" --body "**Review gate:** pull request #$pr $1" || failed=1
}

gh pr comment "$pr" --repo "$repo" --body "$summary"
case $decision in
  PASS)
    gh pr edit "$pr" --repo "$repo" --remove-label needs-fix --add-label review-passed
    on_issue "is ready for you ($summary). Staging runs its newest green commit; try it there, then merge or close it."
    ;;
  FIX)
    gh pr edit "$pr" --repo "$repo" --remove-label "review-round-$round" --add-label "review-round-$((round + 1))" --add-label needs-fix --add-label building
    ;;
  SCRAP)
    gh pr close "$pr" --repo "$repo" --comment "Scrapped by the review gate; the branch is deleted. This pull request keeps the diff and every review."
    gh api -X DELETE "repos/$repo/git/refs/heads/factory/issue-$issue" > /dev/null || failed=1
    gh issue edit "$issue" --repo "$repo" --add-label needs-human || failed=1
    on_issue "was scrapped ($summary). Its branch is deleted; the closed pull request keeps the diff and every review. The automatic analysis and rebuild do not exist yet, so the factory stops on this issue."
    ;;
  NEEDS_HUMAN)
    gh pr edit "$pr" --repo "$repo" --add-label needs-human
    if [ "$issue" -gt 0 ]; then
      gh issue edit "$issue" --repo "$repo" --add-label needs-human || failed=1
    fi
    on_issue "needs you ($summary). The factory does nothing more on it."
    ;;
esac

[ "$failed" -eq 0 ]
