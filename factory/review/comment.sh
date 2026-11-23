#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: comment.sh --head SHA --model MODEL (--findings FILE | --unreadable | --not-run failed|too-large) [--turns N --cost C --seconds S]" >&2
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
    *) usage ;;
  esac
done
[[ $head =~ ^[0-9a-f]{40}$ ]] && [[ $model =~ ^[A-Za-z0-9._:-]+$ ]] && [ -n "$mode" ] || usage
[[ $turns =~ ^[0-9]*$ ]] && [[ $cost =~ ^[0-9.]*$ ]] && [[ $seconds =~ ^[0-9]*$ ]] || usage

short=${head:0:7}
echo "<!-- factory-review -->"

footer() {
  local usage_line="model \`$model\`"
  [ -n "$turns" ] && usage_line+=", $turns turns"
  [ -n "$seconds" ] && usage_line+=", $seconds s"
  [ -n "$cost" ] && usage_line+=", estimated \$$cost (the subscription is not billed per run)"
  echo
  echo "<sub>Report only: nothing was changed and nothing is blocked. An automatic reviewer can miss a defect or report one that is not there. $usage_line.</sub>"
}

case $mode in
  not-run)
    case $reason in
      failed) why="the reviewer could not be run (token, usage limit or time limit). The run's log has the details" ;;
      too-large) why="the change is too large to review in one go" ;;
      *) usage ;;
    esac
    echo "**Review of \`$short\` did not run:** $why. This says nothing about the change itself."
    footer
    ;;
  unreadable)
    echo "**Review of \`$short\` could not be read:** the reviewer answered twice in a form the script does not accept. This says nothing about the change itself."
    footer
    ;;
  findings)
    [ -f "$findings" ] || usage
    jq -r --arg short "$short" '
      def plain: tostring
        | gsub("[\\r\\n\\t]+"; " ")
        | gsub("&"; "&amp;") | gsub("<"; "&lt;") | gsub(">"; "&gt;")
        | gsub("@"; "@​")
        | gsub("(?<c>[\\\\`*_\\[\\]#|~])"; "\\\(.c)");
      def code: tostring | gsub("[`\\r\\n\\t]"; " ");
      (.findings | map(select(.severity == "critical")) | length) as $critical
      | (.findings | map(select(.severity == "major")) | length) as $major
      | (if ($critical + $major) == 0
         then "**Review of `\($short)`:** No critical or major findings."
         else "**Review of `\($short)`:** \($critical) critical, \($major) major."
         end),
        "",
        (.summary | plain),
        (.findings | sort_by(if .severity == "critical" then 0 else 1 end) | .[]
          | "",
            "- **\(.severity)** · \(.rule) · `\(.file | code)\(if .line then ":\(.line)" else "" end)`",
            "  \(.what | plain)",
            "  Why: \(.why | plain)")
    ' "$findings"
    footer
    ;;
esac
