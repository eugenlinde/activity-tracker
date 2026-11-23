#!/usr/bin/env bash
set -euo pipefail

file=${1:-}
if [ -z "$file" ] || [ ! -f "$file" ]; then
  echo "no answer to check" >&2
  exit 1
fi

if grep -qE '"line"[[:space:]]*:[[:space:]]*-?[0-9]*[.eE]' "$file"; then
  echo "the answer is not in the required shape" >&2
  exit 1
fi

if jq -es '
  def text($max): type == "string" and length <= $max and test("[^\\s]");
  def severity_of: {
    "data-loss": "critical", "security": "critical", "cannot-start": "critical", "opposite-of-spec": "critical",
    "wrong-main-path": "major", "criterion-not-met": "major", "no-test": "major", "unhandled-error": "major",
    "project-rule": "major"
  };
  length == 1 and (.[0]
    | type == "object"
    and (keys | sort) == ["findings", "reviewed", "summary"]
    and .reviewed == true
    and (.summary | text(2000))
    and (.findings | type == "array" and length <= 50)
    and all(.findings[];
      type == "object"
      and (keys | sort) == ["file", "line", "rule", "severity", "what", "why"]
      and (.rule | type == "string")
      and (.severity | type == "string")
      and (severity_of[.rule] == .severity)
      and (.file | text(300))
      and (.line == null or (.line | type == "number" and . >= 1 and . <= 10000000 and . == floor))
      and (.what | text(1500))
      and (.why | text(1500))
    ))' "$file" >/dev/null 2>&1; then
  exit 0
fi

echo "the answer is not in the required shape" >&2
exit 1
