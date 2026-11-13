# Current Scope

> **2026-11-12 — pivot.** AI now builds the app; see
> `docs/superpowers/specs/2026-11-12-ai-factory-pivot-design.md`. This file
> still describes the code as it exists. The "Phase 5 — CSV import" guide
> below was written for the developer to implement by hand; the steps stay
> valid as a spec, the "developer writes it" framing does not. The
> prototype in `docs/design/prototype/` is the product target and needs
> entities not listed here yet (weight, steps, diet, comments, invites,
> templates).

## Domain model

**`WeightEntry`** (`weight/model/WeightEntry.java`) — table `weight_entries`
(added 2026-11-13, issue #5). One body-weight reading per user per day.
- `id`, `user` (lazy `ManyToOne`, `user_id` not null), `entryDate`
  (`LocalDate`), `weightKg` (`BigDecimal`, `numeric(4,1)`), `createdAt`,
  `updatedAt`. Unique on (`user_id`, `entry_date`).
- API, owner only (no `ownerId` parameter, so a share never exposes it):
  `GET /api/weights?from=&to=` (ascending by date; default range is the
  last 12 weeks), `PUT /api/weights/{date}` with `{"weightKg": 81.3}`
  (creates or replaces; 20.0–400.0, at most one decimal),
  `DELETE /api/weights/{date}` (404 when there is no entry).
- A date is rejected (400) when it is later than tomorrow in UTC. "Tomorrow"
  is allowed because a user east of UTC is already on the next calendar
  day for part of every day.
- `WeightService` takes a `Clock` (bean in `config/ClockConfig`, UTC) so
  the date rule is testable.

**`User`** (`user/model/User.java`) — table `users`
- `id`: `Long`, identity-generated PK, `@Getter` only
- `firstName`, `lastName`: `String`, not null
- `email`: `String`, not null, unique
- `password`: `String`, not null — stores a BCrypt hash, never plaintext
- `enabled`: `boolean` — read at login via `SecurityUser.isEnabled()`
- `tokenExpired`: `boolean` — **slated for removal** (nothing reads or writes
  it); needs a `dropColumn` changeset, which forces the master-changelog change
- `createdAt`, `updatedAt`: primitive `long`, DB `NOT NULL` — **still nothing
  populates these**; every row persists them as `0`. No `@PrePersist` /
  `@CreationTimestamp` / Spring Data auditing wired up.
- `deletedAt`: primitive `long`, nullable in DB (soft-delete timestamp needs to
  represent "never deleted")
- Note: entity has `@Getter`/`@Setter` per-field rather than class-level, so
  several fields (`tokenExpired`, timestamps) have no accessors at all.

**`UserRepository`** — `JpaRepository<User, Long>` + `findByEmail`, `existsByEmail`.

**`Role`** (`user/model/Role.java`) — enum, single constant `USER`. Not persisted
and not referenced by `User`; it exists only so the authority string has one
definition rather than being typed in two places.

**`SecurityUser`** (`user/model/SecurityUser.java`) — adapter implementing
`UserDetails`, holding a `User` reference. Maps `getUsername()` → email,
`getPassword()` → the stored hash, `isEnabled()` → `user.enabled`. Authorities
are a single `SimpleGrantedAuthority(Role.USER.name())` — the string `USER`,
**unprefixed**. Every user is a `USER`; no per-user role is stored anywhere.
Adds a non-interface `getId()` delegating to the entity, which is how
`TokenService` gets the subject claim.

**`Activity`** (`activity/model/Activity.java`) — table `activities`.
**Represents a completed session**: "on Tuesday at 18:00 I did 45 minutes of
weights". Not a plan, not a template, and it does not describe what was
performed inside the session.
- `id`: `Long`, identity-generated PK
- `activityType`: `ActivityType` enum, `@Enumerated(EnumType.STRING)` →
  `varchar(255) NOT NULL`. STRING is load-bearing: the JPA default is ORDINAL,
  which stores enum *position* and silently reassigns meaning if constants are
  reordered.
- `duration`: `java.time.Duration` → `numeric`, nullable since changeset
  `add-activity-details-column` (2026-11-02). **Stored in nanoseconds** —
  verified in psql 2026-11-02 (30 min = `1800000000000`).
- `details`: `jsonb`, added 2026-11-02 (Phase 5). The column default was
  first set to JSON `'null'::jsonb`; fixed 2026-11-02 by editing the
  changeset and resetting the local DB. No default now. Java type: `Session`
  record (`activity/model/`), `@JdbcTypeCode(SqlTypes.JSON)`.
- `startedAt`: `Instant` → `timestamptz NOT NULL`
- `notes`: `String` → `varchar(255)` nullable
- `createdAt` / `updatedAt`: `Instant`, `timestamptz NOT NULL`, populated by
  Hibernate's `@CreationTimestamp` / `@UpdateTimestamp`. This is the pattern
  `User` should have used and doesn't.
- `user`: `@ManyToOne(fetch = FetchType.LAZY)` → `@JoinColumn(name = "user_id",
  nullable = false)`, unidirectional. Added 2026-09-28; the ownerless decision of
  2026-09-14 is now superseded. `fetch = LAZY` is set explicitly because
  `@ManyToOne` defaults to EAGER.

**`ActivityType`** — enum: `RUNNING`, `WEIGHTS`, `WALKING`, `TREADMILL`.
Intended as *modality* only ("what did you do"), deliberately not intent
("what was it for"). `TREADMILL` is flagged as inconsistent with that rule —
it describes equipment, so a treadmill walk is ambiguously `WALKING` or
`TREADMILL`. Unresolved.

**`ActivityRepository`** (`activity/`) — `JpaRepository<Activity, Long>` plus
two owner-scoped derived queries: `findAllByUserId(Long)` and
`findByIdAndUserId(Long, Long)`. Both property paths traverse the association
(`user` → `id`). The redundant `@Repository` annotation was removed 2026-09-28.

**`Share`** (`share/model/Share.java`) — table `shares`. A whole-log grant:
`owner` lets `viewer` read every activity `owner` owns.
- `id`: `Long`, identity PK
- `owner`, `viewer`: `@ManyToOne(LAZY)` → `users`, `nullable = false`
- `createdAt`: `Instant`, `@CreationTimestamp`
- `deletedAt`: currently a field with `@SoftDelete(strategy = TIMESTAMP)` —
  **wrong placement**, the annotation belongs on the class and the column is
  then Hibernate-managed. Open finding.
- DB: partial unique index on `(owner_id, viewer_id) WHERE deleted_at IS
  NULL`, check `owner_id <> viewer_id`, index on `viewer_id`. Both raw SQL in
  the changeset; `validate` cannot see them.

**`ShareRepository`** — `findAllByOwnerId` (`@EntityGraph("viewer")`),
`findAllByViewerId` (`@EntityGraph("owner")`), `findByIdAndOwnerId`,
`existsByOwnerIdAndViewerId`. Class-level `@SoftDelete` appends
`deleted_at IS NULL` to every one, including the `exists` — verified
manually 2026-10-08 (revoked share → viewer gets 403).

## Share slice (complete 2026-10-08)

| Method | Path | Body | Success | Errors |
|---|---|---|---|---|
| POST | `/api/shares` | `{email}` | 201, empty | 404 unknown email, 409 duplicate or self-share |
| GET | `/api/shares` | — | 200 `[{id, ownerId, email}]` — shares I gave, `email` = viewer | |
| GET | `/api/shares/received` | — | 200 `[{id, ownerId, email}]` — shares I received, `email` = owner | |
| DELETE | `/api/shares/{id}` | — | 204 (soft delete) | 404 not mine / missing |

`ShareService` is `@Transactional` (jakarta variant). `create` uses
`getReferenceById` for the owner, `findByEmail` for the viewer. Both list
methods map inline to `ShareResponse`; no mapper class. `ShareResponse` is
one record serving both lists — `ownerId` is what disambiguates.

**Viewer read path:** `GET /api/activities?ownerId=` (absent → caller, resolved
in the controller). `ActivityService.findAll(userId, ownerId)` guards before
the repository call: `userId.equals(ownerId) ||
existsByOwnerIdAndViewerId(ownerId, userId)`, else `ForbiddenException` →
403. Inline guard (option A), not `PermissionEvaluator`. `findById` and all
writes stay owner-only and 404 on a foreign row.

## Activity CRUD slice (in progress)

Package layout mirrors `authentication/`: `activity/` holds the repository,
with `model/`, `dto/`, `service/`, `controller/` and `mapper/` beneath.

- `ActivityRequest(activityType, startedAt, duration, notes)` — record
- `ActivityResponse(id, ownerId, activityType, startedAt, duration, notes,
  createdAt, updatedAt)` — record. `ownerId` added 2026-10-08 so a shared
  list is labelled.
- `ActivityMapper` — entity → response DTO. Reads `activity.getUser().getId()`;
  safe on the LAZY proxy because the id lives in the proxy, no SELECT.
- `ActivityService.create` — maps request → entity, saves, **returns the
  entity** (the agreed design was DTO-in/DTO-out; see Known gaps)
- `ActivityController` — `POST /api/activities` → 201 + `Location`

**All five endpoints exist as of 2026-09-24** — the Phase 0 CRUD slice is
complete. All of `/api/**` is behind `hasRole("USER")`.

| Method | Path | Success | Missing id |
|---|---|---|---|
| POST | `/api/activities` | 201 + `Location` | — |
| GET | `/api/activities` | 200 list | — |
| GET | `/api/activities/{id}` | 200 | 404 |
| PUT | `/api/activities/{id}` | 200 | 404 |
| DELETE | `/api/activities/{id}` | 204 | 404 |

`ActivityNotFoundException` (`common/exception/`) is thrown by the service via
`orElseThrow` and mapped to 404 by `GlobalExceptionHandler`. The controller does
not catch it.

`ActivityService` is `@Transactional` at class level (Spring's annotation, not
jakarta's), holds all five operations and returns DTOs; `ActivityMapper` is
called inside the service, so `Activity` never leaves it.

**Ownership scoping (2026-09-28).** All five service methods take a trailing
`Long userId`. `create` attaches the owner via
`userRepository.getReferenceById(userId)` — a lazy proxy, so no SELECT against
`users` is issued; the INSERT only needs the FK value. The other four go through
the scoped lookup, so another user's row is indistinguishable from a missing one
and yields 404 with no extra branch. `findAll` uses `findAllByUserId`.

The controller resolves the owner: `@AuthenticationPrincipal Jwt` on all five
handlers, with a private `userId(Jwt)` helper doing
`Long.valueOf(jwt.getSubject())`. The service never reads `SecurityContextHolder`,
which keeps it unit-testable with plain Mockito.

DELETE loads the entity first and 404s on a missing id, rather than calling
`deleteById` and returning 204 unconditionally — a deliberate choice for an
explicit, version-independent not-found.

`updateById` uses `saveAndFlush`, not `save`. Reason: it maps the entity to a
DTO before the transaction commits, and `@UpdateTimestamp` is written by
Hibernate at flush — so without the explicit flush the response body carried a
stale `updatedAt` while the database row was correct. `create` needs no
equivalent because IDENTITY generation forces the INSERT at `save()` time.

## Auth flow

**Signup** — `POST /api/auth/signup` → 201 Created, empty body.
`AuthenticationService.signUp` checks `existsByEmail` (throws
`UserExistsException` → 409), hashes via `PasswordEncoder`, saves.
Known race: `existsByEmail` + `save` is check-then-act; the unique constraint is
the real guard, and `DataIntegrityViolationException` is not yet handled.

**Login** — `POST /api/auth/login` → 200 with `{"token": "..."}`.
`AuthenticationService.logIn` builds an unauthenticated
`UsernamePasswordAuthenticationToken`, passes it to `AuthenticationManager`, and
hands the resulting `Authentication` to `TokenService.generateToken`.
No manual credential checking — `DaoAuthenticationProvider` (auto-assembled from
the `SecurityUserService` and `PasswordEncoder` beans) does the lookup, the
account-status checks, the password comparison, and the
`UsernameNotFoundException` → `BadCredentialsException` collapse that closes
user enumeration.

**`SecurityUserService`** (`user/service/`) — `UserDetailsService` over
`UserRepository`. Consulted at login only — the resource server is wired, so it
does not run per request.

**`TokenService`** (`authentication/service/`) — builds a `JwtClaimsSet` with
`subject` (numeric user id as a String), `issuedAt`, `expiresAt` (now + 24h) and
an `authorities` claim — a `List<String>` mapped from the principal's authorities
via `GrantedAuthority::getAuthority` — then encodes it via `JwtEncoder`. HS256.
Payload as of 2026-09-11: `{sub, iat, exp, authorities: ["USER"]}`.
Claim values are unprefixed by design; `ROLE_` is applied inbound by
`JwtAuthenticationConverter` (see Security config). The token stays free of a
Spring Security naming convention.

## DTOs

`authentication/dto/` — all records with bean validation:
- `SignupRequest(email, password, firstName, lastName)`
- `LoginRequest(email, password)`
- `LoginResponse(token)`

No outbound user DTO exists; no endpoint returns a `User`.

## Error handling

`GlobalExceptionHandler` (`common/exception/`, `@RestControllerAdvice`):
- `AuthenticationException` → 401 `"Unauthorized"`
- `UserExistsException` → 409 with the exception message
- `MethodArgumentNotValidException` → 400 with `Map<String, List<String>>` of
  per-field messages
- `Exception` → 500 `"Internal server error"`, logged with stack trace

Body shapes are inconsistent across handlers (bare strings vs. a JSON object) —
open design question, `ProblemDetail` is the standard alternative.

Caveat for step 4: this advice does **not** see exceptions thrown in servlet
filters. Bearer-token failures will go through `AuthenticationEntryPoint`
instead.

## Security config

`SecurityConfig` — CSRF disabled (bearer-token API, no cookie session).
Matchers, in order (first match wins): `/api/auth/**` permitted, `/api/**`
requires `hasRole("USER")`, `anyRequest().authenticated()`. Beans:
`PasswordEncoder` (BCrypt), `AuthenticationManager` (from
`AuthenticationConfiguration`), `SecretKey` (from Base64 `jwt.secret`,
`HmacSHA256`), `JwtEncoder`, `JwtDecoder` (both `Nimbus*` over the shared key).

`SessionCreationPolicy.STATELESS` and `oauth2ResourceServer(jwt)` are both set
(2026-08-09).

**`JwtAuthenticationConverter`** bean (2026-09-13) wrapping a
`JwtGrantedAuthoritiesConverter` with `setAuthoritiesClaimName("authorities")`
and `setAuthorityPrefix("ROLE_")`, replacing the defaults of `scope`/`scp` and
`SCOPE_`. This is what makes `hasRole` work at all.

Caveat: the converter is **not wired explicitly** — the resource server line
still reads `oauth2.jwt(Customizer.withDefaults())`, and `JwtConfigurer`'s
implicit bean lookup is what connects them. Verified working 2026-09-13, but
invisible in the code and fragile if a second `Converter<Jwt, ...>` bean is
ever added.

Nit outstanding: the converter bean takes an unused `SecretKey` parameter.

## Phase 4 — Media (active; steps 1–3 done 2026-10-13, upload endpoint next)

Design and the nine-step build order live in ROADMAP.md under Phase 4;
decisions in DECISIONS.md 2026-10-09. Summary of the target state:

- `Media` (`media/model/`) — table `media(id, activity_id, storage_key)`
  exists (`storage_key` NOT NULL UNIQUE, index on `activity_id`, plain FK).
  `@ManyToOne(LAZY) Activity`, **unidirectional** — no collection on
  `Activity`. Activity delete cleans media at the service level.
- `StorageService` (`media/storage/`) — store / load (`Resource`) / delete,
  keyed by a service-generated UUID. `LocalStorageService` with root from
  `@ConfigurationProperties`.
- Endpoints: `POST /api/activities/{id}/media` (owner, 201),
  `GET /api/activities/{id}/media` (owner or viewer), `GET /api/media/{id}`
  (owner or viewer, streams `Resource`), `DELETE /api/media/{id}` (owner).
- Open: `Content-Type` on download with the minimal table; where the
  owner-or-viewer rule gets extracted to; `<img>` cannot send a bearer
  header.

## Frontend (`frontend/`, 2026-10-08)

React 19 + Vite 8 + TypeScript, `react-router-dom` 7, no other runtime
deps. Dev server proxies `/api` → `localhost:8080` (`vite.config.ts`), so
no CORS on the Spring side. Reads nothing but the API.

- `src/api/client.ts` — `fetch` wrapper. Attaches `Authorization: Bearer`
  from `localStorage` key `token`; non-2xx → `ApiError(status, body)`; a 401
  clears the stored token. `ApiError.validationErrors` exposes the 400
  `Map<String, List<String>>` body.
- `src/api/types.ts` — hand-mirrored DTOs (`ActivityRequest/Response`,
  `ShareRequest/Response`, auth records). `ACTIVITY_TYPES` must match the
  Java enum by hand.
- `src/api/{auth,activities,shares}.ts` — one function per endpoint.
- `src/auth/AuthContext.tsx` — token state, `userId` decoded from JWT `sub`
  (unverified, display only). `RequireAuth` redirects to `/login`.
- `src/lib/duration.ts` — ISO-8601 `PT..` ↔ minutes. Form takes minutes,
  sends `PT{n}M`. `datetime.ts` — `datetime-local` ↔ ISO instant.
- Routes: `/login`, `/activities` (own CRUD), `/shares` (received list with
  links, given list with grant/revoke), `/shared/:ownerId` (read-only list
  via `?ownerId=`).

## Local infra

- `docker-compose.yml` — single `postgres:17-alpine`, db/user/pass all `tracker`.
- `application.properties` — datasource hardcoded to match compose; `debug=true`
  (conditions evaluation report); `jwt.secret` (Base64, 32 bytes),
  `jwt.expiration=24h`. `spring.liquibase.change-log` points at
  `db/changelog/db.changelog-master.xml` (2026-08-09).
- `http/auth.http` — IntelliJ HTTP Client demo requests covering signup,
  duplicate signup, validation failures, login, and both 401 cases.

## Tests

First tests written 2026-09-25/28. Last green: 13 tests, 2026-09-28.
**As of 2026-10-08 the suite does not compile** — see ROADMAP debt. The
descriptions below are of the tests as last seen green.

- `ActivityMapperTest` — plain JUnit, no Spring. Asserts all seven fields copy
  entity → response.
- `ActivityServiceTest` — Mockito, mocked `ActivityRepository` and a **real**
  `ActivityMapper`. `create` uses an `ArgumentCaptor` to assert the entity
  handed to `save` carries the request's values **and the `User` returned by
  `getReferenceById`** (asserted with `assertSame` — `User.id` is getter-only,
  so a real instance cannot be given an id, and identity plus the stub key is
  what ties it to `userId`); `findById` covers both the found path and
  `Optional.empty()` → `ActivityNotFoundException`; `updateById` captures the
  entity handed to `saveAndFlush` and asserts it carries a *second* request
  whose every value differs from the fixture.
- `ActivityControllerTest` — `@WebMvcTest` with `@MockitoBean ActivityService`,
  plus `@Import(SecurityConfig.class)` and `@ActiveProfiles("test")`
  (2026-09-28). Seven tests: 201 + `Location` + body, 400 on invalid body, 404
  via `@RestControllerAdvice`, 204 on delete with `verify`, **401 with no
  token**, **403 when the token carries `ROLE_ADMIN` instead of `ROLE_USER`**,
  and one asserting the controller forwards the *token's* subject to the
  service (`verify(service).findById(id, 2L)` for a token with `sub: "2"`).
  Requests authenticate via a private `authenticatedAs(Long)` helper wrapping
  `SecurityMockMvcRequestPostProcessors.jwt()`.
- `ActivityTrackerApplicationTests` — `@SpringBootTest` context load, using
  Testcontainers.

Test config lives in `src/test/resources/application-test.properties` with
`@ActiveProfiles("test")`. A plain `application.properties` there was tried
first and **shadowed** the main one — same filename on the classpath, test
classpath wins, replaces rather than merges — which silently dropped
`spring.liquibase.change-log` and broke the context. Profile-specific files
overlay instead.

## Known gaps

- **Row-level scoping has no automated regression guard.** No test executes
  `findByIdAndUserId` against real rows — to a mock, "no such row" and "not your
  row" are the same `Optional.empty()`. Verified manually instead (2026-09-28):
  `show-sql` showed `where a1_0.user_id=?`, and a cross-user GET returned 404.
  `@DataJpaTest` deferred by decision; see ROADMAP.
- No `@DataJpaTest`. Entity↔table mapping and repository behaviour are
  unverified except by `ddl-auto=validate` at startup.
- `findAll`/`findById` are not `@Transactional(readOnly = true)` — declined by
  the developer 2026-09-28, see DECISIONS.md. Recorded as settled, not a gap to
  re-raise.
- No test covers `findAll` or `deleteById` at the service layer.
- `spring.jpa.open-in-view` on by default (warned at startup, never addressed).
- `User.createdAt` / `updatedAt` never populated. `Activity` does this
  correctly via `@CreationTimestamp` / `@UpdateTimestamp`; `User` should copy
  the pattern.
- Signup's 409 re-opens user enumeration that login closes — flagged, decision
  never given.
- Authorities are a login-time snapshot baked into a 24h token; no revocation
  path before expiry.
- Negative `Duration` is accepted — `@Positive` was removed (it throws
  `UnexpectedTypeException` on `Duration`) and nothing replaced it.
- Validation annotations on `ActivityResponse` are inert.
- Activity `duration` storage unit still unverified.

## Phase 5 — CSV import with LLM mapping (active, planned 2026-11-02)

Decisions: see DECISIONS.md 2026-11-02 entries. Summary:
- CSV only.
- The raw text is sent to OpenRouter.
- Java validates the result, then the user reviews it, adds dates and
  confirms.
- All-or-nothing on validation failure.
- Global 10 MB upload limit.
- Exercise names stay in Estonian.
- Per-exercise comments live in the JSON.
- `duration` becomes nullable.
- Session details are stored as `jsonb` on `activities`, validated by
  records nested in `ActivityRequest`, and editable by hand.

Sample input: the developer's own training-plan CSV. A 10-week training-plan grid:
week blocks run across (sets / weight / reps in reserve / reps / comment),
workout blocks ("Treening 1–3") run down, and FAQ text sits at the bottom.
Weeks 1–3 are filled in, so about 9 sessions. No dates, no durations.
Ranges appear as strings ("10-15" target reps, "1-2" reps in reserve).
Reps are per set ("12, 11, 10"). Some cells are empty.

Implementation guide (the developer writes the code; the reviewer reviews
each step):

### Step 1 — Schema
- New `db/changelog/2026/11/02-01-changelog.xml`, included at the end of
  the master changelog.
- Changeset A: `activities.details jsonb`, nullable, no default.
- Changeset B: `dropNotNullConstraint` on `activities.duration` (state
  the column type).
- Settle how `duration` is stored: insert 45 min, read it in psql. If
  it's nanoseconds, decide on a unit change now.
- Check: boot the app; `\d activities` shows `details jsonb` and
  `duration` without `not null`.

### Step 2 — Backend model, DTOs, validation
- Records:
  - Session details: `version` (int) and a list of exercises.
  - Exercise: `name`, `targetReps` (String), `repsInReserve` (String),
    `comment`, `videoUrl` (optional), and a list of sets.
  - Set: `weight` (`BigDecimal`/`Double`, since 31.25 exists) and `reps`
    (nullable `Integer`).
  - Package choice (`activity/model/` vs. a new `activity/details/`) is
    the developer's; justify it.
- Validation:
  - `@NotBlank` on the exercise name.
  - `@Valid` on every nested list.
  - `@PositiveOrZero` on weight and reps.
  - `@Size(max = …)` on the lists.
- `Activity`: `details` field with `@JdbcTypeCode(SqlTypes.JSON)` and
  `@Column(columnDefinition = "jsonb")`. Drop `nullable = false` on
  `duration`. Boot 4 means Jackson 3 and Hibernate 7: if no JSON mapper
  is found, look up Hibernate's `FormatMapper`.
- `ActivityRequest`: drop `@NotNull` on `duration`, keep the
  non-negative check, `@Valid` on `details`.
- `ActivityResponse`: add `details`. `ActivityMapper` passes it both
  ways and is null-safe for `duration` and `details`.
- `ActivityService.update`: PUT replaces everything, so null `details`
  clears it. Make that explicit.
- Tests:
  - JPA round-trip: save, `flush` + `clear`, reload, compare the nested
    values.
  - Plain `Validator` test for nested violations.
  - `ActivityControllerTest`: invalid nested JSON → 400.
  - `ActivityMapperTest`: null duration and null details.
- Check: curl a POST with details, then GET; inspect the JSON in psql.

### Step 3 — Frontend show/edit exercises
- `api/types.ts`: types for the three records.
- `lib/duration.ts`: null shows as "—".
- `ActivityList`: an expandable exercises → sets view (weight × reps,
  comment, reps in reserve).
- `ActivityForm`: duration optional. Exercise editor: add/remove
  exercises and sets. Keep the state nested; empty inputs become `null`,
  not `""` or `0`.
- `SharedLogPage`: read-only display, reusing the same component.
- Check: create by hand, edit a set, reload, the change persists.

### Step 4 — OpenRouter client
- `@ConfigurationProperties` record (like `StorageProperties`):
  `baseUrl`, `apiKey` (from an env var, never committed), `model`,
  `timeout`.
- A `RestClient` bean: base URL, `Authorization: Bearer`, timeouts.
  Chosen over Spring AI: one call, nothing hidden, no question of Boot 4
  compatibility.
- Chat completions request:
  - System prompt:
    - the target shape;
    - keep names in Estonian;
    - each exercise's comment goes on that exercise;
    - null for empty cells;
    - one session per filled week × workout;
    - no invented values;
    - type `WEIGHTS`.
  - User message: the raw CSV.
  - `response_format` = strict `json_schema`, on a model that supports
    structured outputs.
- Deserialize into the import-result record (draft sessions, no date).
  If parsing fails, throw a dedicated exception, mapped to 502 in
  `GlobalExceptionHandler`.
- Retry: validate with the injected `Validator`. On violations, send them
  back and retry, at most 2 retries, then fail.
- Tests: `MockRestServiceServer`, with these cases:
  - valid response → drafts;
  - invalid JSON → exception;
  - invalid then valid → success after exactly 2 calls.
  
  No real API calls in tests.

### Step 5 — Import endpoints
- Package `importer/`.
- `POST /api/imports/preview`:
  - multipart, one file;
  - not CSV → 415 (reuse `UnsupportedMediaTypeException`); empty → 400;
  - read as UTF-8 explicitly;
  - returns drafts and persists nothing;
  - authenticated users only, no share check.
- `POST /api/activities/bulk` (or `/api/imports/confirm`):
  - body is a list of `ActivityRequest` with `@Valid` cascading to the
    elements, plus `@Size(max = …)`;
  - `@Transactional` + `saveAll`; the owner always comes from the
    principal, never from the body;
  - one invalid element → nothing saved.
- Tests:
  - `@WebMvcTest` preview: 415 / 400 / 200, with the LLM service mocked.
  - Bulk: one invalid element → 400 and the repository never called;
    valid → 201.
  - Integration: a DB failure on element N rolls back elements 1…N-1.
- Check: curl preview with the sample file and refine the prompt.

### Step 6 — Frontend import page
- `api/imports.ts` (preview via `FormData`, confirm).
- `ImportPage` + route + nav link.
- Flow:
  - upload → loading state → editable drafts (date required, duration
    optional, reuses the step-3 editor);
  - optional "fill dates" helper (start date + weekday pattern);
  - Confirm → bulk POST → `ActivitiesPage`; Reject → discard.
- Bulk 400 errors shown next to the draft that failed. Check what
  `GlobalExceptionHandler` returns for list-element violations.
- Check: a browser walk with the sample file, giving 9 activities with
  exercises after a reload. Two-user walk: the viewer sees them
  read-only.

### Open questions for the developer
- Why is `@Valid` on `ActivityRequest.details` needed when the nested
  records already have annotations?
- Why is the bulk-confirm endpoint safe without an import id or any trust
  in the preview?
- Why does `flush` + `clear` matter in the round-trip test?

### Step 3 — as built (2026-11-02, reviewer-written on request)
- `api/types.ts`: `WorkoutSet`, `Exercise`, `ActivityDetails`; `duration`
  is `string | null`; `details` on the request and the response.
- `lib/details.ts`: the form keeps its own draft state (strings for every
  input) separate from the API shape. `toDrafts` / `fromDrafts` convert
  between them: empty input → `null`, all-empty sets dropped, zero
  exercises → `details: null`. `version` is always `"1"`. The UI caps
  exercises and sets at 12, matching the backend `@Size`.
- `ExerciseEditor` (controlled, the parent owns the state): add/remove
  exercises; "+ Add set" copies the previous set's values.
- `ExerciseDetails`: read-only. The name links to the video; shows target
  reps, reps in reserve, sets as "31.25 kg × 15", and the comment.
- `ActivityList`: an Exercises column with a Show/Hide (n) toggle that
  expands a full-width row. Reused as-is on `SharedLogPage`, read-only
  because no `onEdit` is passed.
- `ActivityForm`: duration is optional (empty → `null`); `card wide`
  (640px).

### Step 4 — detailed plan (2026-11-05)
Done: `OpenRouterProperties` (`openrouter.*`, `@NotBlank`, empty-default
placeholders, `timeout` 60 s) + `OpenRouterClientConfig`
(`openRouterRestClient`, JDK request factory, Bearer header).
Remaining, in order:

**Packages (revised 2026-11-05):**
- `openrouter/`: HTTP-only integration — `ChatCompletionRequest`,
  `ChatCompletionResponse`, `OpenRouterClient` (`@Component`),
  `OpenRouterClientConfig`, `OpenRouterProperties`. No prompt, no import
  schema, no activity knowledge.
- `importer/`: draft records, prompt/schema loading, `ImportMappingService`
  (uses `OpenRouterClient`).

**4a — Wire records (OpenRouter chat completions, OpenAI-compatible).**
Base URL `https://openrouter.ai/api/v1`, `POST /chat/completions`.
- Request: `model`, `messages` (each with a role and content), `temperature`
  (0), `max_tokens` (a cap), and `response_format`
  (`type = json_schema` + `json_schema { name, strict = true, schema }`).
  Optionally `provider { require_parameters = true }`, so OpenRouter only
  routes to providers that honour `response_format`.
- Response: only what's read: `choices[0].message.content` (a String),
  `choices[0].finish_reason`, and optionally `usage` for logging token
  counts.
- `content` is a JSON *string* inside the JSON response, so it's parsed
  twice: Spring parses the outer response, then an injected `JsonMapper`
  (Jackson 3, `tools.jackson`) parses `content`.
- Unknown properties: Jackson 3 `FAIL_ON_UNKNOWN_PROPERTIES` is **off** by
  default, both in a plain `JsonMapper` and in Boot's auto-configured one
  (probed 2026-11-05). `@JsonIgnoreProperties(ignoreUnknown = true)` is
  optional; it makes the record independent of mapper config.
- Response reuses `ChatCompletionRequest.Message`, so the retry loop can
  append the assistant message to the next request unchanged.
  `finish_reason` stays a String (unknown values must not break parsing).

**4b — Target shape (what the LLM must return). Revised 2026-11-05.**
LLM output is CSV-shaped, *not* the stored `Session` shape. Java maps it.
- `ImportResult { drafts: ImportDraft[] }`
- `ImportDraft { label, activityType, notes, exercises: ImportExercise[] }`
  - `label`: source position, e.g. "Nädal 2 / Treening 1" — shown in the
    preview so the user knows which date to assign. No `startedAt`.
- `ImportExercise { name, targetReps, weight, repsInReserve, reps: int[],
  comment, videoUrl }` — one weight per exercise (CSV "Raskus"), reps list
  (CSV "Kordused"). Set count ("Seeriad") dropped: = `reps.length`
  (CSV has mismatches, e.g. 2 sets / reps "8"; reps win).
- Java maps → `Session(version "1", exerciseList)`: each rep value →
  `WorkoutSet(weight, rep)`. Version set by Java, not LLM.
- Planned-only workouts (no reps anywhere) are not imported. Empty weeks
  skipped. Bodyweight = `weight: 0`. (Decided 2026-11-05.)
- Enforcement: prompt says so; Java backs it with `@NotEmpty` on
  `ImportExercise.reps` and `ImportDraft.exercises` → violation feeds the
  retry loop. Exercise without reps inside a done workout: omitted by the
  LLM (default; same `@NotEmpty`).
- Validate drafts with injected `jakarta.validation.Validator`, then the
  mapped `Session` with the existing constraints.
- Dependency direction: `importer` → `activity.model`, never reverse.

**4c — JSON schema.**
- Store it as a classpath resource: `src/main/resources/llm/import-schema.json`.
  Load it once at startup into a `JsonNode` / `Map` and put it into the
  request. That's easier to read and diff than building it in Java.
- Strict-mode rules (OpenAI-compatible):
  - every property is listed in `required`;
  - optional fields are expressed as a type union with `"null"`;
  - `additionalProperties: false` on every object.
- The schema must match the records exactly; a test guards this (4g).

**4d — Prompt.**
- System prompt in `src/main/resources/llm/import-system-prompt.txt`.
- Rules:
  - one draft per week × workout that has data;
  - keep exercise names exactly as written (Estonian);
  - each exercise's comment goes on that exercise;
  - empty cell → `null`; never invent values;
  - weight in kg as a number; reps per set as an integer;
  - ranges such as "10-15" and "1-2" kept as strings;
  - skip FAQ / prose rows;
  - type `WEIGHTS` unless clearly something else;
  - `version` "1".
- User message: the raw CSV text, with a size cap checked before the call.
  10 MB of CSV is far too many tokens; a cap of something like 200 KB
  fails fast with a 400/413.

**4e — Service flow (`OpenRouterClient` + `ImportMappingService`).**
1. Build the messages: system prompt, then the CSV.
2. POST through `openRouterRestClient`.
3. Extract and check the result:
   - no choices → upstream error;
   - `finish_reason == "length"` → the output was cut off; treat it as an
     upstream error;
   - otherwise parse `content` into `ImportResult`.
4. Validate. If there are violations, append the assistant's reply plus a
   user message listing them (as "path: message"), then call again. At
   most 2 retries, so 3 calls in total.
5. Failures:
   - still invalid, or `content` doesn't parse → `ImportMappingException` → 502;
   - `RestClientResponseException` (4xx/5xx from OpenRouter) → 502;
     OpenRouter's 401 is a misconfigured key, so log it loudly;
   - `ResourceAccessException` (a timeout) → 504 or 502, developer's call.
6. Don't retry on HTTP errors or timeouts, only on validation failures.
7. Logging: model, token usage, attempt count, the violations. Never the
   headers (API key). The CSV is user data, so log it at DEBUG only, if
   at all.

**4f — Injection.** Inject the client by name or `@Qualifier` (bean name
`openRouterRestClient`) so a second `RestClient` bean can't cause an
ambiguity error, or the wrong client being injected.

**4g — Tests (no network).**
- Build a `RestClient` in the test from `RestClient.builder()`, bind
  `MockRestServiceServer` to that builder before calling `build()`, and
  construct the service by hand with a real `Validator` and `JsonMapper`.
  No Spring context.
- Cases:
  - valid response → drafts, 1 call;
  - `content` isn't JSON → exception, 1 call;
  - invalid then valid → success, exactly 2 calls; the 2nd request body
    contains the violation path;
  - invalid 3 times → exception, exactly 3 calls;
  - `finish_reason` "length" → exception;
  - OpenRouter 500 → exception with no retry;
  - the request carries the model, `response_format.json_schema.strict = true`
    and the CSV in the user message (check with jsonPath on the request);
  - schema/record drift: deserialize a sample that matches the schema into
    `ImportResult`.
- Manual check after tests: one real call with the sample CSV, looking at
  the drafts and the token usage.
