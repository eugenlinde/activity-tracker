#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: open-pr.sh --repo OWNER/NAME --issue N --title-file FILE" >&2
  exit 2
}

repo=""
issue=""
title_file=""
while [ $# -gt 0 ]; do
  case $1 in
    --repo) repo=${2:-}; shift 2 ;;
    --issue) issue=${2:-}; shift 2 ;;
    --title-file) title_file=${2:-}; shift 2 ;;
    *) usage ;;
  esac
done
[[ $repo =~ ^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$ ]] && [[ $issue =~ ^[1-9][0-9]{0,6}$ ]] && [ -f "$title_file" ] || usage

branch="factory/issue-$issue"
commit_id='^[0-9a-f]{40}$'

stop() {
  echo "$1" >&2
  exit 1
}

main=$(gh api "repos/$repo/git/ref/heads/main" | jq -r '.object.sha // ""')
[[ $main =~ $commit_id ]] || stop "Could not read main's commit"

if gh api "repos/$repo/git/ref/heads/$branch" > /dev/null 2>&1; then
  gh api -X DELETE "repos/$repo/git/refs/heads/$branch" > /dev/null
fi

tree=$(gh api "repos/$repo/git/commits/$main" | jq -r '.tree.sha // ""')
[[ $tree =~ $commit_id ]] || stop "Could not read main's tree"

commit=$(gh api -X POST "repos/$repo/git/commits" -f message="factory: start of issue $issue" -f tree="$tree" -f "parents[]=$main" | jq -r '.sha // ""')
[[ $commit =~ $commit_id ]] || stop "Could not make the first commit"

gh api -X POST "repos/$repo/git/refs" -f ref="refs/heads/$branch" -f sha="$commit" > /dev/null

remove_branch() {
  gh api -X DELETE "repos/$repo/git/refs/heads/$branch" > /dev/null 2>&1 || true
}

title=$(head -n 1 "$title_file" | cut -c1-200)
body=$(mktemp)
cat > "$body" <<TEXT
Closes #$issue

Written by the factory's implementation agent from the text of issue #$issue.

The first commit is empty on purpose: the pull request has to exist before the agent's commit arrives, so that the commit starts CI. The agent cannot run commands, so nothing here has been compiled or tested by it. CI is the first test of this code.
TEXT

if ! created=$(gh api -X POST "repos/$repo/pulls" -f title="$title" -f head="$branch" -f base=main -F "body=@$body"); then
  remove_branch
  stop "Could not open the pull request"
fi
number=$(jq -r '.number // "" | tostring' <<< "$created" 2>/dev/null || true)
if [[ ! $number =~ ^[1-9][0-9]{0,6}$ ]]; then
  remove_branch
  stop "The pull request's number could not be read"
fi

if ! gh api -X POST "repos/$repo/issues/$number/labels" -f "labels[]=factory" -f "labels[]=building" -f "labels[]=review-round-1" > /dev/null; then
  remove_branch
  stop "Could not label the pull request"
fi

echo "pr=$number"
echo "branch=$branch"
