# CSV Import Implementation Plan

**Goal:** A signed-in user uploads a CSV saved from a spreadsheet, reviews what was understood, and confirms; workouts, weight and steps are written.

**Spec:** GitHub issue #18 (approved by the developer 2026-11-17). Earlier design notes that still hold: `.claude/SPEC.md`, "Phase 5", sections 4b–4g.

**Architecture:** New package `importer/`. A preview endpoint reads the file and writes nothing; a confirm endpoint writes the selected entries in one transaction. The model is asked two different things: for a workout grid it extracts drafts into a schema-enforced JSON shape; for a date/value table it only names the columns and Java reads the rows.

**Tech:** Spring Boot 4.1, Jackson 3 (`tools.jackson`), existing `OpenRouterClient`, Liquibase, React + Ant Design.

## Global constraints

- No new dependencies. CSV parsing is a small hand-written parser.
- The model is never called in the test suite; `MockRestServiceServer` stands in.
- Fixture files are invented. No real training data in the repo.
- The uploaded text is never logged.
- Confirm validates like the manual endpoints and acts only for the caller.
- No comments in code.

## Files

Backend, `src/main/java/dev/eugenlinde/activitytracker/importer/`:

| File | Responsibility |
|---|---|
| `csv/CsvText.java` | bytes → text (UTF-8, BOM stripped; falls back to windows-1257), delimiter guess (`;`, `,`, tab), parse to rows |
| `csv/DateReader.java` | cell + `DateOrder` (DMY, MDY, YMD) → `LocalDate` or empty |
| `csv/TableReader.java` | rows + `TableMapping` → weight rows, steps rows, count of unreadable rows |
| `ImportFingerprint.java` | `Session` → SHA-256 hex over exercise names, weights, reps |
| `llm/FileAnalysis.java` | model answer 1: `kind` (WORKOUT_GRID, TABLE, UNKNOWN) + `TableMapping` |
| `llm/WorkoutExtraction.java` | model answer 2: drafts → exercises (one weight, list of reps) |
| `llm/ImportMappingService.java` | builds the two requests, parses and validates, one retry on invalid output |
| `WorkoutDraftMapper.java` | draft → `Session("1", …)` |
| `ImportProperties.java` | `import.max-text-bytes` (200000), `import.daily-preview-limit` (20), `import.analysis-rows` (40) |
| `PreviewLimiter.java` | per-user, per-day counter in memory |
| `service/ImportPreviewService.java` | file checks, analysis, extraction or table read, comparison with stored data |
| `service/ImportConfirmService.java` | writes selected entries, one transaction |
| `controller/ImportController.java` | `POST /api/imports/preview`, `POST /api/imports/confirm` |
| `dto/*` | preview response, confirm request and response |

Resources: `llm/import-analysis-prompt.txt`, `llm/import-analysis-schema.json`, `llm/import-workouts-prompt.txt`, `llm/import-workouts-schema.json`.

Changed: `Activity` (+`importFingerprint`), `ActivityRepository` (+fingerprint counts), `GlobalExceptionHandler` (+413, 429, 502, 400), changelog `2026/11/17-01-changelog.xml`, `application.properties`.

Frontend: `api/imports.ts`, `pages/ImportPage.tsx`, route and nav entry, types.

## Interfaces

- `CsvText.read(byte[]) -> List<List<String>>`; `CsvText.decode(byte[]) -> String`
- `DateReader.read(String, DateOrder) -> Optional<LocalDate>`
- `TableReader.read(rows, TableMapping) -> TableRows(List<WeightRow>, List<StepsRow>, int unreadable)`
- `ImportFingerprint.of(Session) -> String`
- `ImportMappingService.analyse(String head) -> FileAnalysis`; `.extractWorkouts(String text) -> WorkoutExtraction`; both throw `ImportMappingException`
- `ImportPreviewService.preview(Long userId, String filename, String contentType, byte[] bytes) -> ImportPreviewResponse`
- `ImportConfirmService.confirm(Long userId, ImportConfirmRequest) -> ImportConfirmResponse`

## Rules pinned by tests

- Fingerprint ignores exercise-name case and surrounding spaces, comments, target reps; changes with any weight or rep.
- Already-imported marking is by count: n identical drafts, m stored with that fingerprint → first min(n, m) marked.
- Weight/steps row equal to the stored value → left out and counted as unchanged; different → returned with the stored value.
- Preview: wrong extension or content type → 415; empty → 400; text over the cap → 413; over the daily limit → 429; model failure → 502.
- Model output invalid → one retry carrying the violations; invalid again → 502. HTTP errors and timeouts are not retried.
- Confirm: future date, missing date, out-of-range weight or steps, invalid exercise → 400 and nothing written.

## Review focus

1. A CSV saved by Estonian Excel: semicolons, decimal commas, windows-1257 letters (õ, ä, ö, ü, š, ž).
2. Dates like `3.11.2026`, `03/11/26`, `2026-11-03`; day and month swapped silently.
3. Steps written `10 234` or `10,234`; weight written `81,3 kg`.
4. The same file imported twice in a row.
5. A model answer that is valid JSON but wrong (duplicated drafts, absurd weights).

## Order of work

1. Pure pieces with tests: `CsvText`, `DateReader`, `TableReader`, `ImportFingerprint`, `WorkoutDraftMapper`.
2. Model layer with tests: schemas, prompts, `ImportMappingService`.
3. Persistence and services with tests: changeset, entity, repository, limiter, preview, confirm.
4. Controller and error handling with `@WebMvcTest`.
5. Frontend page; `yarn build` and lint.
6. Whole-branch review by a fresh reviewer; fixes test-first.
7. Docs (`ARCHITECTURE.md`, `SPEC.md`, roadmap, decisions, notes), PR.
