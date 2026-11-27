# Severity rubric

Report a finding only when it breaks one of the rules below. Name the rule by its id; the severity follows from the rule. Anything else is not reported: no style, no naming, no nice-to-haves, no "consider".

The words in brackets are examples of a rule, not its limits.

## critical

| Rule id | The change… |
|---|---|
| `data-loss` | loses or corrupts stored data |
| `security` | opens a security hole (authentication can be bypassed, one user can read or change another user's data, a secret is in the code or in the logs, input from outside reaches a query, a command or a file path unchecked) |
| `cannot-start` | stops production from starting, or contains a database migration that cannot run |
| `opposite-of-spec` | does the opposite of what the spec asks |

## major

| Rule id | The change… |
|---|---|
| `wrong-main-path` | behaves wrongly on the main path of the feature |
| `criterion-not-met` | leaves an acceptance criterion of the spec unmet |
| `no-test` | adds new behaviour without a test (frontend: new logic without a Vitest test of it; a component's markup itself needs no test, there is no DOM test library) |
| `unhandled-error` | does not handle an error case that will happen in normal use (a crash, a 500, lost input) |
| `project-rule` | breaks a hard rule of the project about the code itself (an entity at the API boundary, logic in a controller, comments in code, a secret in git, a user's data or business data written into a log line, an error report or an issue) |
