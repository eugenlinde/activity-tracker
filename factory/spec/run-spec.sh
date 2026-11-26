#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: run-spec.sh --work DIR --out DIR --code DIR --model MODEL" >&2
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
rm -f "$out/reply.json"
answer=$(AGENT_KEEP_ENV="${SPEC_KEEP_ENV:-}" "$(dirname "$HERE")/run-agent.sh" --work "$work" --out "$out" --code "$code" --model "$model" \
  --prompt "$HERE/prompt.md" --schema "$HERE/reply-schema.json" --check "$HERE/check-reply.sh" \
  --code-label "(main, as it is now)" --max-usd "${SPEC_MAX_USD:-5}")
if [ -f "$out/answer.json" ]; then
  mv "$out/answer.json" "$out/reply.json"
fi
printf '%s\n' "$answer" | sed 's/^outcome=answer$/outcome=reply/'
