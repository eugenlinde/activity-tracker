#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: run-agent.sh --work DIR --out DIR --code DIR --model MODEL --prompt FILE --schema FILE --check SCRIPT [--code-label TEXT] [--max-usd N]" >&2
  exit 2
}

work=""
out=""
code=""
model=""
prompt_file=""
schema_file=""
check=""
code_label=""
max_usd=5
while [ $# -gt 0 ]; do
  case $1 in
    --work) work=${2:-}; shift 2 ;;
    --out) out=${2:-}; shift 2 ;;
    --code) code=${2:-}; shift 2 ;;
    --model) model=${2:-}; shift 2 ;;
    --prompt) prompt_file=${2:-}; shift 2 ;;
    --schema) schema_file=${2:-}; shift 2 ;;
    --check) check=${2:-}; shift 2 ;;
    --code-label) code_label=" ${2:-}"; shift 2 ;;
    --max-usd) max_usd=${2:-}; shift 2 ;;
    *) usage ;;
  esac
done
[ -d "$work" ] && [ -d "$code" ] && [ -n "$out" ] && [[ $model =~ ^[A-Za-z0-9][A-Za-z0-9._:-]*$ ]] || usage
[ -f "$prompt_file" ] && [ -f "$schema_file" ] && [ -x "$check" ] && [[ $max_usd =~ ^[0-9]{1,3}(\.[0-9]{1,2})?$ ]] || usage

schema=$(jq -c . "$schema_file")
prompt="$(cat "$prompt_file")

The repository$code_label is in: $code"
mkdir -p "$out"
rm -f "$out/answer.json"

find "$code" -type l -delete

turns=0
cost=0
seconds=0
denials=0
outcome=unreadable
detail=""

number() {
  jq -r "$1" "$2" 2>/dev/null | grep -Ex '[0-9]+(\.[0-9]+)?' || echo 0
}

keep=(PATH="$PATH" HOME="${HOME:-/tmp}" CLAUDE_CODE_OAUTH_TOKEN="${CLAUDE_CODE_OAUTH_TOKEN:-}")
for name in ${AGENT_KEEP_ENV:-}; do
  [[ $name =~ ^[A-Z_][A-Z0-9_]*$ ]] && keep+=("$name=${!name:-}")
done

attempts="1 2"
if [[ ${CLAUDE_CODE_OAUTH_TOKEN:-} =~ [[:space:][:cntrl:]] ]]; then
  attempts=""
  outcome=failed
  detail="token-has-a-line-break-or-a-space"
fi

for attempt in $attempts; do
  result="$out/result-$attempt.json"
  status=0
  (
    cd "$work" && env -i "${keep[@]}" claude -p "$prompt" \
      --restricted --tools "Read,Grep,Glob" --strict-mcp-config --permission-mode dontAsk \
      --no-session-persistence --output-format json --json-schema "$schema" \
      --model "$model" --add-dir "$code" --max-budget-usd "$max_usd" < /dev/null
  ) > "$result" 2> "$out/stderr-$attempt.txt" || status=$?

  if ! jq -es 'length == 1 and (.[0] | type == "object" and (.is_error | type == "boolean")
        and (.num_turns | type == "number") and (.permission_denials | type == "array"))' "$result" >/dev/null 2>&1; then
    outcome=failed
    detail="no-result"
    [ "$status" -ne 0 ] && detail="exit-$status"
    break
  fi

  turns=$((turns + $(number '.num_turns | floor' "$result")))
  cost=$(jq -rn --arg before "$cost" --arg now "$(number '.total_cost_usd' "$result")" '($before | tonumber) + ($now | tonumber)')
  seconds=$((seconds + $(number '(.duration_ms // 0) / 1000 | floor' "$result")))
  denials=$((denials + $(number '.permission_denials | length' "$result")))

  if [ "$status" -ne 0 ] || [ "$(jq -r '.is_error' "$result")" != "false" ]; then
    outcome=failed
    detail=$(jq -r '"error-" + ((.subtype // "none") | tostring) + "-" + ((.terminal_reason // "none") | tostring) + "-" + ((.api_error_status // "none") | tostring)' "$result" \
      | tr -cd 'A-Za-z0-9_.-' | cut -c1-80)
    if [ "$(jq -r '.terminal_reason // ""' "$result")" = "api_error" ] \
      && ! { [ -n "${CLAUDE_CODE_OAUTH_TOKEN:-}" ] && grep -qF -- "$CLAUDE_CODE_OAUTH_TOKEN" "$result"; }; then
      said=$(jq -r '.result // "" | tostring' "$result" | tr -s '[:space:]' '-' | tr -cd 'A-Za-z0-9_.-' | tr -s '-' | cut -c1-100 | sed 's/^-//; s/-$//')
      [ -n "$said" ] && detail="$detail-$said"
    fi
    break
  fi

  jq -c '.structured_output // empty' "$result" > "$out/candidate.json"
  if [ -n "${CLAUDE_CODE_OAUTH_TOKEN:-}" ] && grep -qF -- "$CLAUDE_CODE_OAUTH_TOKEN" "$result"; then
    rm -f "$out/candidate.json" "$result"
    outcome=failed
    detail="answer-held-the-token"
    break
  fi
  if [ "$(number '.num_turns | floor' "$result")" -ge 2 ] && "$check" "$out/candidate.json" 2>/dev/null; then
    mv "$out/candidate.json" "$out/answer.json"
    outcome=answer
    break
  fi
  rm -f "$out/candidate.json"
  echo "attempt $attempt: the answer was not in the required shape, or nothing was read"
done

echo "outcome=$outcome"
echo "turns=$turns"
echo "cost=$(jq -rn --arg cost "$cost" '(($cost | tonumber) * 100 | round) / 100')"
echo "seconds=$seconds"
echo "denials=$denials"
if [ -n "$detail" ]; then
  echo "detail=$detail"
fi
