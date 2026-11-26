#!/usr/bin/env bash
set -euo pipefail

file=${1:-}
if [ -z "$file" ] || [ ! -f "$file" ]; then
  echo "no reply to check" >&2
  exit 1
fi

if jq -es '
  def text($max): type == "string" and length <= $max and test("[^\\s]");
  def line($max): text($max) and (test("[\\n\\r]") | not);
  length == 1 and (.[0]
    | type == "object"
    and (keys | sort) == ["changes", "kind", "questions", "reason", "risk", "size", "spec", "type", "what", "why"]
    and (.questions | type == "array")
    and (
      (.kind == "questions"
        and (.questions | length >= 1 and length <= 5)
        and all(.questions[]; line(300)))
      or (.kind == "spec"
        and (.type == "bug" or .type == "feature")
        and (.what | line(200))
        and (.why | line(200))
        and (.changes | line(200))
        and (.risk == "low" or .risk == "medium" or .risk == "high")
        and (.size == "S" or .size == "M" or .size == "L")
        and (.spec | text(20000))
        and (.spec | contains("factory-original") | not))
      or (.kind == "out-of-reach"
        and (.reason | text(1000)))
    ))' "$file" >/dev/null 2>&1; then
  exit 0
fi

echo "the reply is not in the required shape" >&2
exit 1
