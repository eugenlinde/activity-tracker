#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: comment.sh --head SHA --model MODEL (--findings FILE | --unreadable | --not-run failed|too-large) [--turns N --cost C --seconds S --denials N]" >&2
  exit 2
}

head=""
model=""
mode=""
findings=""
reason=""
turns=""
cost=""
seconds=""
denials=""
while [ $# -gt 0 ]; do
  case $1 in
    --head) head=${2:-}; shift 2 ;;
    --model) model=${2:-}; shift 2 ;;
    --findings) mode=findings; findings=${2:-}; shift 2 ;;
    --unreadable) mode=unreadable; shift ;;
    --not-run) mode=not-run; reason=${2:-}; shift 2 ;;
    --turns) turns=${2:-}; shift 2 ;;
    --cost) cost=${2:-}; shift 2 ;;
    --seconds) seconds=${2:-}; shift 2 ;;
    --denials) denials=${2:-}; shift 2 ;;
    *) usage ;;
  esac
done
[[ $head =~ ^[0-9a-f]{40}$ ]] && [[ $model =~ ^[A-Za-z0-9][A-Za-z0-9._:-]*$ ]] && [ -n "$mode" ] || usage
[[ $turns =~ ^[0-9]*$ ]] && [[ $cost =~ ^[0-9.]*$ ]] && [[ $seconds =~ ^[0-9]*$ ]] && [[ $denials =~ ^[0-9]*$ ]] || usage

short=${head:0:7}
limit=${REVIEW_COMMENT_LIMIT:-55000}
echo "<!-- factory-review -->"

footer() {
  local usage_line="model \`$model\`"
  [ -n "$turns" ] && usage_line+=", $turns turns"
  [ -n "$seconds" ] && usage_line+=", $seconds s"
  [ -n "$cost" ] && usage_line+=", estimated \$$cost (the subscription is not billed per run)"
  if [ -n "$denials" ] && [ "$denials" -gt 0 ]; then
    usage_line+=". $denials tool calls were refused: the reviewer tried something it is not allowed to do"
  fi
  echo
  echo "<sub>Report only: nothing was changed and nothing is blocked. An automatic reviewer can miss a defect or report one that is not there. $usage_line.</sub>"
}

case $mode in
  not-run)
    case $reason in
      failed) why="the reviewer could not be run, or the pull request could not be read (token, usage limit, time limit or GitHub). The run's log names the reason" ;;
      too-large) why="the change is too large to review in one go" ;;
      *) usage ;;
    esac
    echo "**Review of \`$short\` did not run:** $why. This says nothing about the change itself."
    footer
    ;;
  unreadable)
    echo "**Review of \`$short\` could not be read:** twice the reviewer either answered in a form the script does not accept or said it could not read the change. This says nothing about the change itself."
    footer
    ;;
  findings)
    [ -f "$findings" ] || usage
    jq -r --arg short "$short" --argjson limit "$limit" '
      def plain: tostring
        | gsub("[\\x{200B}-\\x{200F}\\x{202A}-\\x{202E}\\x{2066}-\\x{2069}\\x{FEFF}]"; "")
        | gsub("\\s+"; " ")
        | gsub("&"; "&amp;") | gsub("<"; "&lt;") | gsub(">"; "&gt;")
        | gsub("(?<c>[\\\\`*_\\[\\]#|~$])"; "\\\(.c)")
        | gsub("@"; "@​")
        | gsub("://"; ":​//")
        | gsub("(?<w>[Ww][Ww][Ww])\\."; "\(.w)​.")
        | gsub("(?<g>[Gg][Hh])-(?<n>[0-9])"; "\(.g)-​\(.n)");
      def code: tostring | gsub("[\\x{200B}-\\x{200F}\\x{202A}-\\x{202E}\\x{2066}-\\x{2069}\\x{FEFF}]"; "") | gsub("[`\\s]+"; " ");
      (.findings | map(select(.severity == "critical")) | length) as $critical
      | (.findings | map(select(.severity == "major")) | length) as $major
      | ([ (if ($critical + $major) == 0
            then "**Review of `\($short)`:** No critical or major findings."
            else "**Review of `\($short)`:** \($critical) critical, \($major) major."
            end),
           "",
           (.summary | plain) ] | join("\n")) as $top
      | [ .findings | sort_by(if .severity == "critical" then 0 else 1 end) | .[]
          | [ "",
              "- **\(.severity)** · \(.rule | code) · `\(.file | code)\(if .line then ":\(.line)" else "" end)`",
              "  What: \(.what | plain)",
              "  Why: \(.why | plain)" ] | join("\n") ] as $entries
      | reduce $entries[] as $entry ({shown: [], used: ($top | length), hidden: 0};
          if .hidden > 0 or (.used + ($entry | length)) > $limit
          then .hidden += 1
          else .shown += [$entry] | .used += ($entry | length)
          end)
      | $top, .shown[],
        (if .hidden > 0
         then "", "\(.hidden) more findings are not shown here: the comment would be too long. Fix the ones above and the next review lists what is left."
         else empty end)
    ' "$findings"
    footer
    ;;
esac
