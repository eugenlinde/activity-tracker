#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: collect.sh --repo OWNER/NAME --issue N --event opened|comment|dispatch --sender LOGIN --out DIR" >&2
  exit 2
}

repo=""
issue=""
event=""
sender=""
out=""
while [ $# -gt 0 ]; do
  case $1 in
    --repo) repo=${2:-}; shift 2 ;;
    --issue) issue=${2:-}; shift 2 ;;
    --event) event=${2:-}; shift 2 ;;
    --sender) sender=${2:-}; shift 2 ;;
    --out) out=${2:-}; shift 2 ;;
    *) usage ;;
  esac
done
[[ $repo =~ ^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$ ]] && [[ $issue =~ ^[1-9][0-9]{0,6}$ ]] && [ -n "$sender" ] && [ -n "$out" ] || usage
case "$event" in opened|comment|dispatch) ;; *) usage ;; esac

MAX_RUNS=5
MAX_ORIGINAL_BYTES=40000
START='<!-- factory-original:start -->'
END='<!-- factory-original:end -->'
MARK='<!-- factory-spec -->'
ORIGINAL_SUMMARY='<details><summary>Your original request (superseded by the spec above)</summary>'

owner=${repo%%/*}

no() {
  echo "act=no"
  echo "reason=$1"
  exit 0
}

stop() {
  echo "act=stop"
  echo "reason=$1"
  echo "run=$2"
  exit 0
}

lower() {
  printf '%s' "$1" | tr '[:upper:]' '[:lower:]'
}

fingerprint() {
  if command -v sha256sum > /dev/null; then sha256sum; else shasum -a 256; fi | cut -d' ' -f1
}

rm -rf "$out/work"
rm -f "$out/original.md"
mkdir -p "$out/work/inputs"

[ "$(lower "$sender")" = "$(lower "$owner")" ] || no not-owner

json=$(gh issue view "$issue" --repo "$repo" --json number,url,state,title,body,author,labels,comments) || no unreadable
jq -e 'type == "object" and (.state | type) == "string"' <<< "$json" > /dev/null 2>&1 || no unreadable
jq -e '(.url // "") | test("/issues/[0-9]+$")' <<< "$json" > /dev/null || no pull-request

[ "$(jq -r '.state' <<< "$json")" = OPEN ] || no closed
[ "$(lower "$(jq -r '.author.login // ""' <<< "$json")")" = "$(lower "$owner")" ] || no not-owner-issue

has_label() { jq -e --arg name "$1" 'any(.labels[]?; .name == $name)' <<< "$json" > /dev/null; }
for label in approved building needs-human; do
  has_label "$label" && no "$label"
done
if [ "$event" = comment ] && ! has_label needs-info && ! has_label spec-ready; then
  no not-waiting
fi

body=$(jq -r '.body // ""' <<< "$json" | tr -d '\r')
if [ "$event" = opened ] && [[ $(printf '%s' "$body" | sed '/^[[:space:]]*$/d' | head -n 1) == '**Decision needed:**'* ]]; then
  no written-by-hand
fi

runs=$(jq --arg mark "$MARK" '[.comments[]? | select((.author.login // "") == "github-actions" or (.author.login // "") == "github-actions[bot]") | select((.body // "") | contains($mark))] | length' <<< "$json")
run=$((runs + 1))
[ "$runs" -lt "$MAX_RUNS" ] || stop limit "$run"

if printf '%s\n' "$body" | grep -qxF -- "$START" && printf '%s\n' "$body" | grep -qxF -- "$END"; then
  printf '%s\n' "$body" | awk -v start="$START" -v end="$END" '
    !begun && $0 == start { begun = 1; next }
    begun { lines[++n] = $0; if ($0 == end) last = n }
    END { for (i = 1; i < last; i++) print lines[i] }' > "$out/original.md"
  printf '%s\n' "$body" | awk -v summary="$ORIGINAL_SUMMARY" '
    { lines[++n] = $0; if ($0 == summary) cut = n }
    END { if (!cut) cut = n + 1; for (i = 1; i < cut; i++) print lines[i] }' > "$out/work/inputs/current-spec.md"
else
  printf '%s\n' "$body" > "$out/original.md"
  echo "(none yet)" > "$out/work/inputs/current-spec.md"
fi
[ -s "$out/work/inputs/current-spec.md" ] || echo "(none yet)" > "$out/work/inputs/current-spec.md"

[ "$(wc -c < "$out/original.md")" -le "$MAX_ORIGINAL_BYTES" ] || stop too-long "$run"

{
  echo "# $(jq -r '.title // ""' <<< "$json" | tr '\n\r\t' '   ' | cut -c1-300)"
  echo
  echo "## The developer's original text"
  echo
  cat "$out/original.md"
} > "$out/work/inputs/issue.md"

jq -r --arg owner "$(lower "$owner")" --arg mark "$MARK" '
  [.comments[]?
    | (.author.login // "") as $login
    | if ($login | ascii_downcase) == $owner then {who: "The developer", body: (.body // "")}
      elif ($login == "github-actions" or $login == "github-actions[bot]") and ((.body // "") | contains($mark)) then {who: "The spec agent", body: ((.body // "") | gsub($mark; ""))}
      else empty end]
  | .[-20:]
  | if length == 0 then "(no comments yet)"
    else map("### " + .who + "\n\n" + (.body | .[0:8000] | sub("^\\s+"; ""))) | join("\n\n") end' <<< "$json" > "$out/work/inputs/conversation.md"

echo "act=yes"
echo "run=$run"
echo "body=$(printf '%s' "$body" | fingerprint)"
