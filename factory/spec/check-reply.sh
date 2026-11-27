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
  def plain: (test("(^|[^A-Za-z0-9._%+-])@[A-Za-z0-9]") | not) and (test("<[A-Za-z!/?]") | not);
  def shown($max): line($max) and plain;
  def facts: type == "object" and (keys | sort) == ["access", "existing", "schema"] and all(.[]; type == "boolean");
  def none: .assumed == [] and .workaround == "" and (.facts | facts) and all(.facts[]; . == false);
  length == 1 and (.[0]
    | type == "object"
    and (keys | sort) == ["assumed", "changes", "facts", "kind", "questions", "reason", "size", "spec", "type", "what", "why", "workaround"]
    and (.questions | type == "array")
    and (
      (.kind == "questions"
        and (.questions | length >= 1 and length <= 5)
        and all(.questions[]; shown(300))
        and none)
      or (.kind == "spec"
        and (.type == "bug" or .type == "feature")
        and (.what | shown(200))
        and (.why | shown(200))
        and (.changes | shown(200))
        and (.size == "S" or .size == "M" or .size == "L")
        and (.spec | text(20000))
        and (.spec | contains("factory-original") | not)
        and (.assumed | type == "array" and length <= 8 and all(.[]; shown(150) and (contains(" · ") | not)))
        and (.workaround | type == "string" and (. == "" or shown(300)))
        and (.facts | facts))
      or (.kind == "out-of-reach"
        and (.reason | text(1000) and plain)
        and none)
    ))' "$file" >/dev/null 2>&1; then
  exit 0
fi

echo "the reply is not in the required shape" >&2
exit 1
