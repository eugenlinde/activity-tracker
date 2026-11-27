#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: report.sh refuse --repo R --issue N --reason REASON [--pr N] [--open \"N N\"]" >&2
  echo "       report.sh empty --repo R --issue N --pr N --branch factory/issue-N --agent success|failure" >&2
  echo "       report.sh outside --repo R --issue N --pr N --files FILE" >&2
  echo "       report.sh too-many --repo R --issue N --pr N" >&2
  echo "       report.sh unchecked --repo R --issue N --pr N" >&2
  echo "       report.sh token --repo R --issue N --pr N --branch factory/issue-N" >&2
  echo "       report.sh closed --repo R --issue N --pr N --branch factory/issue-N" >&2
  echo "       report.sh finished --repo R --issue N --pr N --model M --agent success|failure --turns N --cost C --seconds N --denials N" >&2
  echo "       report.sh fixed --repo R --issue N --pr N --model M --agent success|failure --turns N --cost C --seconds N --denials N" >&2
  echo "       report.sh unchanged --repo R --issue N --pr N --agent success|failure" >&2
  echo "       report.sh moved --repo R --issue N --pr N" >&2
  echo "       outside, too-many, unchecked, token and closed take --role fix after a fix" >&2
  exit 2
}

what=${1:-}
[ $# -gt 0 ] && shift

repo=""
issue=""
pr=""
branch=""
reason=""
files=""
model=""
agent=""
turns=""
cost=""
seconds=""
denials=""
role=build
open=""
while [ $# -gt 0 ]; do
  case $1 in
    --repo) repo=${2:-}; shift 2 ;;
    --issue) issue=${2:-}; shift 2 ;;
    --pr) pr=${2:-}; shift 2 ;;
    --branch) branch=${2:-}; shift 2 ;;
    --reason) reason=${2:-}; shift 2 ;;
    --files) files=${2:-}; shift 2 ;;
    --model) model=${2:-}; shift 2 ;;
    --agent) agent=${2:-}; shift 2 ;;
    --turns) turns=${2:-}; shift 2 ;;
    --cost) cost=${2:-}; shift 2 ;;
    --seconds) seconds=${2:-}; shift 2 ;;
    --denials) denials=${2:-}; shift 2 ;;
    --role) role=${2:-}; shift 2 ;;
    --open) open=${2:-}; shift 2 ;;
    *) usage ;;
  esac
done

id='^[1-9][0-9]{0,6}$'
count='^[0-9]{1,9}$'

[[ $repo =~ ^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$ ]] && [[ $issue =~ $id ]] || usage
[ -z "$pr" ] || [[ $pr =~ $id ]] || usage
[ -z "$open" ] || [[ $open =~ ^[1-9][0-9]{0,6}( [1-9][0-9]{0,6})*$ ]] || usage
[ -z "$branch" ] || [[ $branch =~ ^factory/issue-[1-9][0-9]{0,6}$ ]] || usage
[ -z "$model" ] || [[ $model =~ ^[A-Za-z0-9][A-Za-z0-9._:-]*$ ]] || usage
case $agent in '' | success | failure) ;; *) usage ;; esac
case $role in
  build) who="Implementation agent" ;;
  fix) who="Fix agent" ;;
  *) usage ;;
esac
[ -z "$turns" ] || [[ $turns =~ $count ]] || usage
[ -z "$seconds" ] || [[ $seconds =~ $count ]] || usage
[ -z "$denials" ] || [[ $denials =~ $count ]] || usage
[ -z "$cost" ] || [[ $cost =~ ^[0-9]{1,6}(\.[0-9]{1,4})?$ ]] || usage

marker='<!-- factory-implement -->'

on_issue() {
  local file
  file=$(mktemp)
  printf '%s\n%s\n' "$marker" "$1" > "$file"
  gh issue comment "$issue" --repo "$repo" --body-file "$file"
}

on_pr() {
  local file
  file=$(mktemp)
  printf '%s\n%s\n' "$marker" "$1" > "$file"
  gh pr comment "$pr" --repo "$repo" --body-file "$file"
}

failed=0

try() {
  "$@" || failed=1
}

remove_branch() {
  gh api -X DELETE "repos/$repo/git/refs/heads/$branch" > /dev/null
}

again="To try again, remove the label \`approved\` and add it again."

case $what in
  refuse)
    case $reason in
      not-owner-label) text="The label \`approved\` starts a build only when the repository owner adds it. Nothing was built." ;;
      not-owner-issue) text='The factory builds only issues written by the repository owner. Nothing was built.' ;;
      closed) text='This issue is closed. Nothing was built.' ;;
      no-label) text="This issue does not carry the label \`approved\`. Nothing was built." ;;
      empty-spec) text='This issue has no title to build from. Nothing was built.' ;;
      pr-open)
        [ -n "$pr" ] || usage
        text="Pull request #$pr for this issue is already open. Nothing was built. Close it first to build again."
        ;;
      full)
        [ -n "$open" ] || usage
        read -r -a open_prs <<< "$open"
        named=$(printf '#%s, ' "${open_prs[@]}")
        text="Waiting: ${#open_prs[@]} factory pull requests are open (${named%, }). Starts by itself when one is merged or closed. Remove \`approved\` to take it out of the line."
        try gh issue edit "$issue" --repo "$repo" --add-label queued
        ;;
      failed) text="The issue could not be read from GitHub, or its pull request could not be opened. Nothing was built; the run's log has the reason. $again" ;;
      *) usage ;;
    esac
    try on_issue "**$who:** $text"
    ;;

  empty)
    [ -n "$pr" ] && [ -n "$branch" ] && [ -n "$agent" ] || usage
    how="The agent's run ended normally."
    if [ "$agent" = failure ]; then
      how="The agent's run ended with an error (token, usage limit, time limit or the agent itself); the run's log has the reason."
    fi
    try gh pr close "$pr" --repo "$repo" --comment "The implementation agent committed no change. Closed; the branch is deleted."
    try remove_branch
    try on_issue "**$who:** no change was written. $how Pull request #$pr is closed and its branch deleted. $again"
    ;;

  outside)
    [ -n "$pr" ] && [ -f "$files" ] || usage
    total=$(grep -c . "$files" || true)
    list=$(grep . "$files" | head -n 30 | tr -d '`<>\000-\010\013-\037' | sed 's/@/(at)/g' | cut -c1-200 | sed "s/^/- \`/; s/\$/\`/")
    more=""
    if [ "$total" -gt 30 ]; then
      more=$'\n'"- and $((total - 30)) more"
    fi
    try on_pr "**$who:** this change touches files outside the places the factory may write (\`src/\`, \`frontend/src/\`, \`http/\`, \`docs/\`):"$'\n\n'"$list$more"$'\n\n'"The pull request is left as it is for the developer to look at. Do not merge it without reading those files."
    try on_issue "**$who:** pull request #$pr was written, but it touches files outside the places the factory may write. It is marked \`needs-human\`."
    try gh pr edit "$pr" --repo "$repo" --add-label needs-human
    gh pr edit "$pr" --repo "$repo" --remove-label building > /dev/null 2>&1 || true
    ;;

  too-many)
    [ -n "$pr" ] || usage
    try on_pr "**$who:** this change has more files or commits than can be read back, so it could not be checked. It is left as it is for the developer to look at."
    try on_issue "**$who:** pull request #$pr was written, but it is too large to check. It is marked \`needs-human\`."
    try gh pr edit "$pr" --repo "$repo" --add-label needs-human
    gh pr edit "$pr" --repo "$repo" --remove-label building > /dev/null 2>&1 || true
    ;;

  unchecked)
    [ -n "$pr" ] || usage
    try on_pr "**$who:** what the agent committed could not be read back from GitHub, so it was not checked against the places the factory may write. It is left as it is for the developer to look at."
    try on_issue "**$who:** pull request #$pr could not be checked after the agent's run. It is marked \`needs-human\`; the run's log has the reason."
    try gh pr edit "$pr" --repo "$repo" --add-label needs-human
    ;;

  token)
    [ -n "$pr" ] && [ -n "$branch" ] || usage
    gone="The branch was deleted at once, which closed pull request #$pr."
    if ! remove_branch; then
      failed=1
      gone="**The branch could not be deleted: delete \`$branch\` by hand now.** Pull request #$pr is still open."
    fi
    try on_issue "**$who:** the change the agent committed contained the text of the Claude token. $gone **Treat the token as public:** GitHub keeps a pull request's commits readable after the branch is gone. Make a new token with \`claude setup-token\`, store it in the \`factory\` environment, and switch \`FACTORY_ENABLED\` off until the cause is known."
    ;;

  closed)
    [ -n "$pr" ] && [ -n "$branch" ] || usage
    pr_head=$(gh pr view "$pr" --repo "$repo" --json headRefOid --jq .headRefOid || true)
    branch_head=$(gh api "repos/$repo/git/ref/heads/$branch" --jq .object.sha || true)
    if [[ $pr_head =~ ^[0-9a-f]{40}$ ]] && [ "$pr_head" = "$branch_head" ]; then
      try remove_branch
      try on_issue "**$who:** pull request #$pr was closed while the agent worked. Its branch is deleted and nothing more was done. $again"
    else
      try on_issue "**$who:** pull request #$pr was closed while the agent worked. Its branch has moved on (a newer pull request may use it), so it is kept, and nothing more was done."
    fi
    ;;

  fixed)
    [ -n "$pr" ] && [ -n "$model" ] && [ -n "$agent" ] && [ -n "$turns" ] && [ -n "$cost" ] && [ -n "$seconds" ] && [ -n "$denials" ] || usage
    numbers="model \`$model\`, $turns turns, $seconds s, estimated \$$cost (the subscription is not billed per run), $denials refused tool calls"
    if [ "$agent" = failure ]; then
      try on_pr "**Fix agent:** committed a fix, but its run ended with an error after it, so the fix may be incomplete. Marked \`needs-human\`; the loop stops here."$'\n\n'"<sub>$numbers.</sub>"
      try gh pr edit "$pr" --repo "$repo" --add-label needs-human
      try gh issue edit "$issue" --repo "$repo" --add-label needs-human
    else
      try on_pr "**Fix agent:** committed a fix. CI and the review take it from here; that is the next round."$'\n\n'"<sub>$numbers.</sub>"
    fi
    gh pr edit "$pr" --repo "$repo" --remove-label building --remove-label needs-fix > /dev/null 2>&1 || true
    ;;

  unchanged)
    [ -n "$pr" ] && [ -n "$agent" ] || usage
    how="Its run ended normally."
    if [ "$agent" = failure ]; then
      how="Its run ended with an error (token, usage limit, time limit or the agent itself); the run's log has the reason."
    fi
    try on_pr "**Fix agent:** committed nothing. $how Without a new commit there is no next round, so this pull request is marked \`needs-human\`."
    try gh pr edit "$pr" --repo "$repo" --add-label needs-human
    try gh issue edit "$issue" --repo "$repo" --add-label needs-human
    try on_issue "**Fix agent:** pull request #$pr was not fixed: the agent committed nothing. It is marked \`needs-human\`."
    gh pr edit "$pr" --repo "$repo" --remove-label building --remove-label needs-fix > /dev/null 2>&1 || true
    ;;

  moved)
    [ -n "$pr" ] || usage
    try on_pr "**Fix agent:** while it worked, the branch moved: its newest commit is not the fix agent's commit on top of the commit it was given. A commit by someone else may be in between, and the fix may have written over it. Marked \`needs-human\`; read the newest commits before going on."
    try gh pr edit "$pr" --repo "$repo" --add-label needs-human
    try gh issue edit "$issue" --repo "$repo" --add-label needs-human
    try on_issue "**Fix agent:** pull request #$pr changed while the fix agent worked. It is marked \`needs-human\`."
    ;;

  finished)
    [ -n "$pr" ] && [ -n "$model" ] && [ -n "$agent" ] && [ -n "$turns" ] && [ -n "$cost" ] && [ -n "$seconds" ] && [ -n "$denials" ] || usage
    numbers="model \`$model\`, $turns turns, $seconds s, estimated \$$cost (the subscription is not billed per run), $denials refused tool calls"
    if [ "$agent" = failure ]; then
      try on_issue "**$who:** pull request #$pr has a commit, but the agent's run ended with an error after it, so the work may be incomplete. It is marked \`needs-human\`."$'\n\n'"<sub>$numbers.</sub>"
      try gh pr edit "$pr" --repo "$repo" --add-label needs-human
    else
      try on_issue "**$who:** pull request #$pr holds the change. CI, staging and the review agent take it from here; nothing was compiled or tested by the agent."$'\n\n'"<sub>$numbers.</sub>"
    fi
    gh pr edit "$pr" --repo "$repo" --remove-label building > /dev/null 2>&1 || true
    ;;

  *) usage ;;
esac

[ "$failed" -eq 0 ]
