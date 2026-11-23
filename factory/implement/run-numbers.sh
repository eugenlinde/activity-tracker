#!/usr/bin/env bash
set -uo pipefail

file=${1:-}
last='[.[] | select(.type == "result")] | last'

number() {
  jq -r "$last | $1" "$file" 2>/dev/null | grep -Ex '[0-9]+(\.[0-9]+)?' || echo 0
}

turns=$(number '.num_turns | floor')
seconds=$(number '(.duration_ms // 0) / 1000 | floor')
cost=$(number '((.total_cost_usd // 0) * 100 | round) / 100')
denials=$(number 'if (.permission_denials | type) == "array" then (.permission_denials | length) else 0 end')
tools=$(jq -r '[.[] | select(.type == "assistant") | .message.content[]? | select(.type == "tool_use") | .name
    | select(type == "string") | gsub("[^A-Za-z0-9_]"; "") | select(. != "")]
  | group_by(.) | map(.[0] + "." + (length | tostring)) | join(",")' "$file" 2>/dev/null) || tools=""

echo "turns=$turns"
echo "seconds=$seconds"
echo "cost=$cost"
echo "denials=$denials"
echo "tools=${tools:-none}"
