#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: run-review.sh --work DIR --code DIR --model MODEL" >&2
  exit 2
}

HERE=$(cd "$(dirname "$0")" && pwd)

work=""
code=""
model=""
while [ $# -gt 0 ]; do
  case $1 in
    --work) work=${2:-}; shift 2 ;;
    --code) code=${2:-}; shift 2 ;;
    --model) model=${2:-}; shift 2 ;;
    *) usage ;;
  esac
done
[ -d "$work" ] && [ -d "$code" ] && [[ $model =~ ^[A-Za-z0-9._:-]+$ ]] || usage

max_usd=${REVIEW_MAX_USD:-5}
schema=$(jq -c . "$HERE/findings-schema.json")
prompt="$(cat "$HERE/prompt.md")

The repository with the change applied is in: $code"
rm -f "$work/findings.json"

turns=0
cost=0
seconds=0
denials=0
outcome=unreadable

for attempt in 1 2; do
  status=0
  (
    cd "$work"
    env -u GH_TOKEN -u GITHUB_TOKEN claude -p "$prompt" \
      --restricted --tools "Read,Grep,Glob" --strict-mcp-config --permission-mode dontAsk \
      --no-session-persistence --output-format json --json-schema "$schema" \
      --model "$model" --add-dir "$code" --max-budget-usd "$max_usd" < /dev/null
  ) > "$work/result-$attempt.json" 2> "$work/stderr-$attempt.txt" || status=$?

  if [ "$status" -ne 0 ] || ! jq -e 'type == "object" and .is_error == false' "$work/result-$attempt.json" >/dev/null 2>&1; then
    outcome=failed
    break
  fi

  turns=$((turns + $(jq -r '.num_turns // 0 | floor' "$work/result-$attempt.json")))
  cost=$(jq -rn --argjson before "$cost" --slurpfile result "$work/result-$attempt.json" '$before + ($result[0].total_cost_usd // 0)')
  seconds=$((seconds + $(jq -r '(.duration_ms // 0) / 1000 | floor' "$work/result-$attempt.json")))
  denials=$((denials + $(jq -r '.permission_denials // [] | length' "$work/result-$attempt.json")))

  jq -c '.structured_output // empty' "$work/result-$attempt.json" > "$work/candidate.json"
  if "$HERE/check-findings.sh" "$work/candidate.json" 2>/dev/null; then
    mv "$work/candidate.json" "$work/findings.json"
    outcome=findings
    break
  fi
  rm -f "$work/candidate.json"
  echo "attempt $attempt: the answer was not in the required shape"
done

echo "outcome=$outcome"
echo "turns=$turns"
echo "cost=$(jq -rn --argjson cost "$cost" '($cost * 100 | round) / 100')"
echo "seconds=$seconds"
echo "denials=$denials"
