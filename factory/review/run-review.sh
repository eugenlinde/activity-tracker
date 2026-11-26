#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: run-review.sh --work DIR --out DIR --code DIR --model MODEL" >&2
  exit 2
}

HERE=$(cd "$(dirname "$0")" && pwd)

work=""
out=""
code=""
model=""
while [ $# -gt 0 ]; do
  case $1 in
    --work) work=${2:-}; shift 2 ;;
    --out) out=${2:-}; shift 2 ;;
    --code) code=${2:-}; shift 2 ;;
    --model) model=${2:-}; shift 2 ;;
    *) usage ;;
  esac
done
[ -d "$work" ] && [ -d "$code" ] && [ -n "$out" ] && [[ $model =~ ^[A-Za-z0-9][A-Za-z0-9._:-]*$ ]] || usage

mkdir -p "$out"
rm -f "$out/findings.json"
answer=$(AGENT_KEEP_ENV="${REVIEW_KEEP_ENV:-}" "$(dirname "$HERE")/run-agent.sh" --work "$work" --out "$out" --code "$code" --model "$model" \
  --prompt "$HERE/prompt.md" --schema "$HERE/findings-schema.json" --check "$HERE/check-findings.sh" \
  --code-label "with the change applied" --max-usd "${REVIEW_MAX_USD:-5}")
if [ -f "$out/answer.json" ]; then
  mv "$out/answer.json" "$out/findings.json"
fi
printf '%s\n' "$answer" | sed 's/^outcome=answer$/outcome=findings/'
