#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: report.sh refuse --repo R --issue N --reason REASON [--pr N]" >&2
  echo "       report.sh empty --repo R --issue N --pr N --branch factory/issue-N --agent success|failure" >&2
  echo "       report.sh outside --repo R --issue N --pr N --files FILE" >&2
  echo "       report.sh too-many --repo R --issue N --pr N" >&2
  echo "       report.sh unchecked --repo R --issue N --pr N" >&2
  echo "       report.sh token --repo R --issue N --pr N --branch factory/issue-N" >&2
  echo "       report.sh finished --repo R --issue N --pr N --model M --agent success|failure --turns N --cost C --seconds N --denials N" >&2
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
    *) usage ;;
  esac
done

id='^[1-9][0-9]{0,6}$'
count='^[0-9]{1,9}$'

[[ $repo =~ ^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$ ]] && [[ $issue =~ $id ]] || usage
[ -z "$pr" ] || [[ $pr =~ $id ]] || usage
[ -z "$branch" ] || [[ $branch =~ ^factory/issue-[1-9][0-9]{0,6}$ ]] || usage
[ -z "$model" ] || [[ $model =~ ^[A-Za-z0-9][A-Za-z0-9._:-]*$ ]] || usage
case $agent in '' | success | failure) ;; *) usage ;; esac
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
      failed) text="The issue could not be read from GitHub, or its pull request could not be opened. Nothing was built; the run's log has the reason. $again" ;;
      *) usage ;;
    esac
    on_issue "**Implementation agent:** $text"
    ;;

  empty)
    [ -n "$pr" ] && [ -n "$branch" ] && [ -n "$agent" ] || usage
    how="The agent's run ended normally."
    if [ "$agent" = failure ]; then
      how="The agent's run ended with an error (token, usage limit, time limit or the agent itself); the run's log has the reason."
    fi
    gh pr close "$pr" --repo "$repo" --comment "The implementation agent committed no change. Closed; the branch is deleted."
    gh api -X DELETE "repos/$repo/git/refs/heads/$branch" > /dev/null
    on_issue "**Implementation agent:** no change was written. $how Pull request #$pr is closed and its branch deleted. $again"
    ;;

  outside)
    [ -n "$pr" ] && [ -f "$files" ] || usage
    total=$(grep -c . "$files" || true)
    list=$(grep . "$files" | head -n 30 | tr -d '`<>\000-\010\013-\037' | sed 's/@/(at)/g' | cut -c1-200 | sed "s/^/- \`/; s/\$/\`/")
    more=""
    if [ "$total" -gt 30 ]; then
      more=$'\n'"- and $((total - 30)) more"
    fi
    gh pr edit "$pr" --repo "$repo" --add-label needs-human
    on_pr "**Implementation agent:** this change touches files outside the places the factory may write (\`src/\`, \`frontend/src/\`, \`http/\`, \`docs/\`, \`build.gradle\`):"$'\n\n'"$list$more"$'\n\n'"The pull request is left as it is for the developer to look at. Do not merge it without reading those files."
    on_issue "**Implementation agent:** pull request #$pr was written, but it touches files outside the places the factory may write. It is marked \`needs-human\`."
    ;;

  too-many)
    [ -n "$pr" ] || usage
    gh pr edit "$pr" --repo "$repo" --add-label needs-human
    on_pr "**Implementation agent:** this change has 300 files or more, more than GitHub lists, so it could not be checked. It is left as it is for the developer to look at."
    on_issue "**Implementation agent:** pull request #$pr was written, but it is too large to check. It is marked \`needs-human\`."
    ;;

  unchecked)
    [ -n "$pr" ] || usage
    gh pr edit "$pr" --repo "$repo" --add-label needs-human
    on_pr "**Implementation agent:** what the agent committed could not be read back from GitHub, so it was not checked against the places the factory may write. It is left as it is for the developer to look at."
    on_issue "**Implementation agent:** pull request #$pr could not be checked after the agent's run. It is marked \`needs-human\`; the run's log has the reason."
    ;;

  token)
    [ -n "$pr" ] && [ -n "$branch" ] || usage
    gh api -X DELETE "repos/$repo/git/refs/heads/$branch" > /dev/null
    on_issue "**Implementation agent:** the change the agent committed contained the text of the Claude token. The branch was deleted at once, which closed pull request #$pr. **Treat the token as public:** GitHub keeps a closed pull request's commits readable. Make a new token with \`claude setup-token\` and store it in the \`factory\` environment, and switch \`FACTORY_ENABLED\` off until the cause is known."
    ;;

  finished)
    [ -n "$pr" ] && [ -n "$model" ] && [ -n "$agent" ] && [ -n "$turns" ] && [ -n "$cost" ] && [ -n "$seconds" ] && [ -n "$denials" ] || usage
    numbers="model \`$model\`, $turns turns, $seconds s, estimated \$$cost (the subscription is not billed per run), $denials refused tool calls"
    if [ "$agent" = failure ]; then
      gh pr edit "$pr" --repo "$repo" --add-label needs-human
      on_issue "**Implementation agent:** pull request #$pr has a commit, but the agent's run ended with an error after it, so the work may be incomplete. It is marked \`needs-human\`."$'\n\n'"<sub>$numbers.</sub>"
    else
      on_issue "**Implementation agent:** pull request #$pr holds the change. CI, staging and the review agent take it from here; nothing was compiled or tested by the agent."$'\n\n'"<sub>$numbers.</sub>"
    fi
    ;;

  *) usage ;;
esac
