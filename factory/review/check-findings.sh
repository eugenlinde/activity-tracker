#!/usr/bin/env bash
set -euo pipefail

file=${1:-}
if [ -z "$file" ] || [ ! -f "$file" ]; then
  echo "no answer to check" >&2
  exit 1
fi

if jq -e '
  def text($max): type == "string" and length > 0 and length <= $max;
  type == "object"
  and (keys | sort) == ["findings", "summary"]
  and (.summary | text(2000))
  and (.findings | type == "array" and length <= 50)
  and all(.findings[];
    type == "object"
    and (keys | sort) == ["file", "line", "rule", "severity", "what", "why"]
    and (.severity == "critical" or .severity == "major")
    and (.rule as $rule | ["data-loss", "security", "cannot-start", "opposite-of-spec",
          "wrong-main-path", "criterion-not-met", "no-test", "unhandled-error", "project-rule"] | index($rule) != null)
    and (.file | text(300))
    and (.line == null or (.line | type == "number" and . >= 1 and . == floor))
    and (.what | text(1500))
    and (.why | text(1500))
  )' "$file" >/dev/null 2>&1; then
  exit 0
fi

echo "the answer is not in the required shape" >&2
exit 1
