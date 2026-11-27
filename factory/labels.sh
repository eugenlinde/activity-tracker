#!/usr/bin/env bash
set -euo pipefail

[ "${1:-}" = --repo ] && [ -n "${2:-}" ] || {
  echo "usage: labels.sh --repo OWNER/NAME" >&2
  exit 2
}
repo=$2

label() {
  gh label create "$1" --repo "$repo" --force --color "$2" --description "$3"
}

label approved 0e8a16 "Spec approved by the owner: the implementation agent builds it"
label factory 5319e7 "Factory PR: written by the implementation agent"
label building fef2c0 "Factory PR: the check after the agent has not judged it yet"
label review-round-1 c5def5 "Review loop: round 1 of 3"
label review-round-2 fbca04 "Review loop: round 2 of 3"
label review-round-3 d93f0b "Review loop: round 3 of 3, last"
label needs-fix e99695 "Review gate asked the fix agent to work"
label review-passed 0e8a16 "No major or critical findings; ready for the developer"
label attempt-1 bfdadc "First build of this issue"
label attempt-2 d4c5f9 "Rebuild after a scrap or a conflict; no further automatic rebuild"
label needs-analysis f9d0c4 "PR was scrapped; analysis agent should run"
label needs-human b60205 "Factory stopped; the developer decides"
label needs-info d876e3 "Spec agent asks questions or a confirmation; your comment starts the next run"
label queued c2e0c6 "Approved, waiting for a free factory slot; starts by itself"
label spec-ready 0e8a16 "Spec agent wrote the spec into the issue; add approved or comment what to change"
