#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: next.sh --repo OWNER/NAME" >&2
  exit 2
}

HERE=$(cd "$(dirname "$0")" && pwd)
# shellcheck disable=SC1091
. "$HERE/../limits.sh"

repo=""
while [ $# -gt 0 ]; do
  case $1 in
    --repo) repo=${2:-}; shift 2 ;;
    *) usage ;;
  esac
done
[[ $repo =~ ^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$ ]] || usage

pulls=$(gh api --paginate "repos/$repo/pulls?state=open&per_page=100")
open=$(jq -s --arg repo "$repo" '[add // [] | .[] | select(.head.repo.full_name == $repo and any(.labels[]?; .name == "factory"))] | length' <<< "$pulls")
[[ $open =~ ^[0-9]+$ ]] || exit 1

listed=$(gh api --paginate "repos/$repo/issues?labels=queued&state=all&per_page=100")
issues=$(jq -s '[add // [] | .[] | select(has("pull_request") | not)]' <<< "$listed")
dropped=$(jq -r '.[] | select(.state != "open" or (any(.labels[]; .name == "approved") | not) or any(.labels[]; .name == "needs-human")) | .number' <<< "$issues")
waiting=$(jq -r '[.[] | select(.state == "open" and any(.labels[]; .name == "approved") and (any(.labels[]; .name == "needs-human") | not))] | sort_by((if any(.labels[]; .name == "attempt-2") then 0 else 1 end), .number) | .[].number' <<< "$issues")

status=0

for issue in $dropped; do
  [[ $issue =~ ^[1-9][0-9]{0,6}$ ]] || continue
  if gh issue edit "$issue" --repo "$repo" --remove-label queued > /dev/null; then
    echo "dropped=$issue"
  else
    status=1
  fi
done

free=$((FACTORY_OPEN_LIMIT - open))
for issue in $waiting; do
  [[ $issue =~ ^[1-9][0-9]{0,6}$ ]] || continue
  if [ "$free" -le 0 ]; then
    echo "waiting=$issue"
    continue
  fi
  if ! gh issue edit "$issue" --repo "$repo" --remove-label queued > /dev/null; then
    status=1
    continue
  fi
  if gh workflow run implement.yml --repo "$repo" --ref main -f "issue=$issue" > /dev/null; then
    echo "started=$issue"
    free=$((free - 1))
  else
    gh issue edit "$issue" --repo "$repo" --add-label queued > /dev/null || true
    status=1
  fi
done

exit "$status"
