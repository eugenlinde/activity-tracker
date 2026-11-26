#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: write.sh --repo OWNER/NAME --issue N --run K --model M --turns N --seconds N --cost N --out DIR (--reply FILE --original FILE [--body-sha SHA256] | --stop limit|too-long|failed|unreadable)" >&2
  exit 2
}

HERE=$(cd "$(dirname "$0")" && pwd)

repo=""
issue=""
run=""
model=""
turns=0
seconds=0
cost=0
out=""
reply=""
original=""
stop=""
body_sha=""
while [ $# -gt 0 ]; do
  case $1 in
    --repo) repo=${2:-}; shift 2 ;;
    --issue) issue=${2:-}; shift 2 ;;
    --run) run=${2:-}; shift 2 ;;
    --model) model=${2:-}; shift 2 ;;
    --turns) turns=${2:-}; shift 2 ;;
    --seconds) seconds=${2:-}; shift 2 ;;
    --cost) cost=${2:-}; shift 2 ;;
    --out) out=${2:-}; shift 2 ;;
    --reply) reply=${2:-}; shift 2 ;;
    --original) original=${2:-}; shift 2 ;;
    --stop) stop=${2:-}; shift 2 ;;
    --body-sha) body_sha=${2:-}; shift 2 ;;
    *) usage ;;
  esac
done
[[ $repo =~ ^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$ ]] && [[ $issue =~ ^[1-9][0-9]{0,6}$ ]] && [[ $run =~ ^[1-9][0-9]?$ ]] && [ -n "$out" ] || usage
[[ $model =~ ^[A-Za-z0-9][A-Za-z0-9._:-]*$ ]] || usage
[[ $turns =~ ^[0-9]{1,9}$ ]] || turns=0
[[ $seconds =~ ^[0-9]{1,9}$ ]] || seconds=0
[[ $cost =~ ^[0-9]{1,6}(\.[0-9]{1,4})?$ ]] || cost=0
[ -z "$body_sha" ] || [[ $body_sha =~ ^[0-9a-f]{64}$ ]] || usage
if [ -n "$stop" ]; then
  [ -z "$reply" ] && [ -z "$original" ] && [ -z "$body_sha" ] || usage
  case "$stop" in limit|too-long|failed|unreadable) ;; *) usage ;; esac
else
  [ -f "$reply" ] && [ -f "$original" ] || usage
  [ "$run" -le 5 ] || usage
fi

MARK='<!-- factory-spec -->'
START='<!-- factory-original:start -->'
END='<!-- factory-original:end -->'

mkdir -p "$out"
rm -f "$out/body.md" "$out/comment.md"

if [ -z "$stop" ]; then
  "$HERE/check-reply.sh" "$reply"
fi

json=$(gh issue view "$issue" --repo "$repo" --json url,state,labels,body)
jq -e 'type == "object" and (.state | type) == "string"' <<< "$json" > /dev/null 2>&1 || { echo "the issue could not be read" >&2; exit 1; }
has_label() { jq -e --arg name "$1" 'any(.labels[]?; .name == $name)' <<< "$json" > /dev/null; }
fingerprint() {
  if command -v sha256sum > /dev/null; then sha256sum; else shasum -a 256; fi | cut -d' ' -f1
}

skip() {
  echo "written=no"
  echo "reason=$1"
  exit 0
}
jq -e '(.url // "") | test("/issues/[0-9]+$")' <<< "$json" > /dev/null || skip pull-request
[ "$(jq -r .state <<< "$json")" = OPEN ] || skip closed
for label in approved building needs-human; do
  has_label "$label" && skip "$label"
done

numbers="<sub>Run $run of 5 · model \`$model\`, $turns turns, $seconds s, estimated \$$cost (the subscription is not billed per run).</sub>"
if [ "$stop" = limit ] || [ "$stop" = too-long ]; then
  numbers=""
elif [ "$turns" = 0 ]; then
  numbers="<sub>Run $run of 5.</sub>"
fi

labels=()
add() { labels+=(--add-label "$1"); }
remove() { if has_label "$1"; then labels+=(--remove-label "$1"); fi; }

if [ -n "$stop" ]; then
  again="To try again, remove \`needs-human\` and start the workflow \"Spec\" by hand."
  case "$stop" in
    limit)
      why="this issue has had 5 runs of the spec agent. That is final for this issue, so that a back-and-forth cannot run forever."
      again="To go on, open a new issue with what you want."
      ;;
    too-long)
      why="your text is longer than 40,000 characters, more than the spec agent takes."
      again="To try again, shorten your text, remove \`needs-human\` and start the workflow \"Spec\" by hand."
      ;;
    failed) why="the spec agent's run failed. Nothing on this issue was changed." ;;
    unreadable) why="the spec agent gave no usable answer, twice. Nothing on this issue was changed." ;;
  esac
  {
    echo "$MARK"
    echo "**Spec agent:** stopped: $why"
    echo
    echo "The factory does nothing more on this issue. $again"
    if [ -n "$numbers" ]; then
      echo
      echo "$numbers"
    fi
  } > "$out/comment.md"
  add needs-human
  kind=stop
else
  kind=$(jq -r .kind "$reply")
  now=$(jq -r '.body // ""' <<< "$json" | tr -d '\r')
  if [ "$kind" = spec ] && [ -n "$body_sha" ] && [ "$(printf '%s' "$now" | fingerprint)" != "$body_sha" ]; then
    kind=changed
  fi
  case "$kind" in
    changed)
      {
        echo "$MARK"
        echo "**Spec agent:** the issue's text changed while I worked, so I wrote nothing over it. Comment here to start the next run, which reads the new text."
        echo
        echo "$numbers"
      } > "$out/comment.md"
      if ! has_label spec-ready; then
        add needs-info
      fi
      ;;
    questions)
      {
        echo "$MARK"
        echo "**Spec agent:** before I write the spec, I need to know:"
        echo
        jq -r '.questions | to_entries[] | "\(.key + 1). \(.value)"' "$reply"
        echo
        echo "Answer in a comment here; your answer starts the next run."
        echo
        echo "$numbers"
      } > "$out/comment.md"
      add needs-info
      remove spec-ready
      ;;
    spec)
      type=$(jq -r .type "$reply")
      label=enhancement
      other=bug
      if [ "$type" = bug ]; then
        label=bug
        other=enhancement
      fi
      {
        echo "**Decision needed:** add \`approved\` to build it, or comment what to change"
        jq -r '"**What:** " + .what, "**Why:** " + .why, "**What changes for you:** " + .changes, "**Risk:** " + .risk + " · **Size:** " + .size' "$reply"
        echo "**Source:** developer · **Written by:** spec agent, run $run"
        echo
        echo "<details><summary>Spec for the agent</summary>"
        echo
        jq -r .spec "$reply"
        echo
        echo "</details>"
        echo
        echo "<details><summary>Your original request (superseded by the spec above)</summary>"
        echo
        echo "$START"
        cat "$original"
        echo "$END"
        echo
        echo "</details>"
      } > "$out/body.md"
      {
        echo "$MARK"
        echo "**Spec agent:** the spec is in this issue's text (run $run of 5). Read the lines at the top. Add \`approved\` to build it, or comment what to change and I write it again."
        echo
        echo "$numbers"
      } > "$out/comment.md"
      add spec-ready
      add "$label"
      remove needs-info
      remove "$other"
      ;;
    out-of-reach)
      {
        echo "$MARK"
        echo "**Spec agent:** this is out of the factory's reach. $(jq -r .reason "$reply")"
        echo
        echo "The factory does nothing more on this issue; it is yours to decide."
        echo
        echo "$numbers"
      } > "$out/comment.md"
      add needs-human
      remove needs-info
      remove spec-ready
      ;;
  esac
fi

if [ -f "$out/body.md" ]; then
  gh issue edit "$issue" --repo "$repo" --body-file "$out/body.md" "${labels[@]}" > /dev/null
elif [ "${#labels[@]}" -gt 0 ]; then
  gh issue edit "$issue" --repo "$repo" "${labels[@]}" > /dev/null
fi
gh issue comment "$issue" --repo "$repo" --body-file "$out/comment.md" > /dev/null

echo "written=$kind"
