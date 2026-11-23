# Decisions Log

**2026-07-25 — Postgres/JWT secrets hardcoded in `application.properties`, not env vars.**
Developer's explicit call: keep datasource credentials as literal values in the committed properties file for now. Reasoning: Phase 0 mandates no environment-conditional config; externalizing to env vars is an environment-splitting concern, which is explicitly out of scope until a later phase. Applies to the Postgres credentials now, and will apply to a future `jwt.secret` property too.
> **Partially reversed 2026-08-03 — see below.**

**2026-08-03 — `jwt.secret` externalized to an environment variable; datasource credentials stay hardcoded.**
Reverses the JWT half of the 2026-07-25 decision, at the developer's initiative, before the key was ever committed (the change was still unstaged, so no history scrubbing was needed). Reasoning: a JWT signing key is a different class of secret from a local Postgres password — anyone holding it can mint valid tokens for any user id, and committing it puts it in history permanently even if later deleted. Datasource credentials remain literal, since they only grant access to a local container.
Implementation: `jwt.secret=${JWT_SECRET}` with **no default**, so startup fails with `Could not resolve placeholder` rather than silently running with an empty key. Value lives in the IntelliJ run configuration. Considered and rejected: a gitignored `secrets.properties` via `spring.config.import` (more moving parts), and a `local` Spring profile (conflicts with the no-profiles rule). Note this is *not* a profile split — one environment, one config file, one externalized value — so the Phase 0 "no environment-conditional config" rule still holds.
Key rotated at the same time, since the original had been through shell history.

**2026-08-09 — Domain model for the training slice: five entities, plan and performance separated.**
Developer's use case is gym workouts first (runs later), recorded *after* the session — the MVP is a logbook, not a live tracker. A future mobile app may do live set/rest tracking; nothing should preclude that.
- `Exercise` — global catalogue, `name` unique. Chosen over free-text exercise names specifically so progress-over-time queries are possible. Equipment type was considered as a related table (developer wanted the JPA relations practice) but cut from the MVP; muscle group dropped.
- `WorkoutPlan` → `PlannedExercise` — targets are **per-exercise** (3×10×60kg), not per-set. Per-set targets would be needed for pyramid/ramping sets; developer trains straight sets, so a child table is deferred.
- `Workout` → `WorkoutExercise` → `ExerciseSet` — the performed session. Sets carry reps and weight.
- Weight in **integer grams**, distance in metres, duration in seconds — units in the field names (`weightGrams`, not `weight`) so the unit can't be lost. Avoids floating-point rounding artefacts.
- `avgPace` and similar are **not stored** — derived from distance/duration, computed in the response DTO.

**2026-08-09 — Plan targets are snapshotted onto the workout, not read through a foreign key.**
When a workout is created from a plan, the plan's targets are copied onto the workout's own rows. A nullable FK back to the plan is kept for provenance only and must never be read for target values. Reasoning: editing a plan later (swapping an exercise, changing a target weight) would otherwise retroactively rewrite what past workouts claim to have been. Same pattern as an invoice storing the price at time of sale rather than joining to the current product price. Accepted cost: duplicated data, and fixing a typo in a plan does not fix it in past workouts — correct behaviour, but it surprises people.

**2026-08-09 — Sets modelled as a table, not a JSON column.**
Developer proposed a `jsonb` field, reasoning that set *order* is semantically weak (`12/10/0` and `12/0/10` describe the same session) and that sets are only ever read with their parent. The ordering observation was accepted — **no `position` column on `ExerciseSet`**. JSON was rejected: the app's stated purpose is progress tracking, and every such query (max weight by month, total volume per session, rep PRs) aggregates *across* sets. Those are indexed one-line aggregates against a table, versus awkward unindexed `jsonb_array_elements` casts against a document. Secondary costs: bean validation can't reach inside a JSON blob, and adding a field later means migrating every stored document. Rule applied: JSON is right only when the value is always read back whole.

**2026-08-09 — Every workout comes from a plan (product rule), but the FK stays nullable (schema).**
Developer's call: no ad-hoc sessions in the MVP. I recommended enforcing this in the service layer rather than with a `NOT NULL` column, since the planned live-tracking mobile app makes unplanned extra work likely, and the constraint is cheap to add later and annoying to remove. Consequence: because a plan is a prerequisite for a workout, the build order flips to `Exercise` → `WorkoutPlan` → `Workout`. I had previously suggested performance-before-plan, which assumed ad-hoc sessions were allowed.

**2026-08-09 — No delete endpoint for `Exercise` in the MVP; auditing timestamps deferred.**
Options weighed: soft delete (`deletedAt`, filtered from listings, consistent with `User`) versus no delete at all. Hard delete was never viable — once workouts reference an exercise, deleting it either cascades away training history or fails on a foreign key. Developer chose no delete: the catalogue is global, curated, and has no user-generated junk to clean up yet. Slice is therefore `POST` / `GET` list / `GET` by id / `PUT`. Archiving can be added when it becomes annoying.
Also deferred: `createdAt`/`updatedAt` on `Exercise`. Cost named — adding `NOT NULL` timestamp columns later to a populated table needs a default or a backfill, which is free right now while the table is empty. Related open gap: `User.createdAt`/`updatedAt` are `NOT NULL` and persist as `0` on every row; the fix is the same Spring Data JPA auditing setup being deferred here.

**2026-08-09 — `Workout.performedOn` as `LocalDate`, not `Instant`.**
Post-hoc logging means the user reconstructs when a session happened; an `Instant` would be false precision they'd have to invent. A date plus an optional `durationSeconds` matches what is actually known. Revisit if live tracking ever supplies real start/stop timestamps. Deliberately *not* repeating the `long` epoch-millis pattern used on `User.createdAt`/`updatedAt`, which is already broken there (never populated).

**2026-08-01 — Drop `User.tokenExpired`.** *(full entry below — this heading was a stray duplicate, now merged)*

**2026-07-25 — Liquibase changelog wired directly via `spring.liquibase.change-log`, not a master changelog with `include`s.**
Developer chose to point Liquibase straight at the single dated changelog file (`db/changelog/2026/07/25-01-changelog.xml`) rather than set up a `db.changelog-master.yaml` indirection layer, since there's currently only one changeset file. Trade-off named: this property will need to change (or be replaced with a master-changelog pattern) the moment a second changeset is added.
> **Superseded 2026-08-09.** Master changelog added at `db/changelog/db.changelog-master.xml` (XML, not YAML) with an explicit `<include>` of the existing dated file; `spring.liquibase.change-log` repointed at it. The dated file was deliberately left in place — because a changeset's identity is `(id, author, filename)`, moving it would have made Liquibase treat both changesets as new and attempt to re-create the `users` table. Verified by startup log: `Run: 0`, `Previously run: 2`. Chose explicit `<include>` over `<includeAll>` for now: `includeAll` sorts lexicographically (so unpadded numbers misorder) and executes anything dropped in the directory.

**2026-07-25 — Changelog generated via IntelliJ's Liquibase tooling, then hand-corrected.**
Developer let the IDE generate the initial `createTable` changelog from the `User` entity/DB diff. It defaulted every primitive-typed field (including `deletedAt`) to `NOT NULL`, which was wrong for a soft-delete timestamp — developer removed that constraint by hand. Lesson: IDE-generated changelogs need review, not blind acceptance, especially around nullability of primitive Java fields.

**2026-07-25 — CSRF disabled in `SecurityConfig`.**
Reasoning: CSRF protection defends cookie/session-based browser auth flows; this API takes bearer tokens (JWT, see below) with no cookie session, so CSRF doesn't apply. Confirmed developer understood the reasoning before applying it (per project's teaching approach), not just copying a common Spring Security snippet.

**2026-07-25 — Chose JWT (bearer token) over session-based auth for authentication persistence.**
Discussed both options (stateful session/cookie vs. stateless JWT) given the custom `/api/auth/login` endpoint already in place rather than Spring Security's built-in form login. Developer chose JWT. Implementation (token generation, validation filter, `UserDetailsService`) explicitly deferred — current scope was just getting signup/login working against a real DB, not the full auth-persistence story.

**2026-07-25 — `anyRequest().authenticated()` chosen over blanket `permitAll()` in `SecurityFilterChain`.**
No other endpoints exist yet, so functionally either would behave the same today. Developer chose to set the intended long-term authorization shape now rather than defer it, on the reasoning that it costs nothing to write correctly upfront.

**2026-08-01 — Close the auth loop before circling back to the Phase 0 `Activity` CRUD slice.**
Phase 0's CRUD slice is still unstarted (flagged 2026-07-25). Trade-off named both ways: doing `Activity` first is what the roadmap literally says, but `anyRequest().authenticated()` means no new endpoint is reachable until token validation exists, and Phase 2 ownership wants an authenticated principal available while the CRUD slice is being written — otherwise both entity and endpoints get retrofitted. Developer chose to finish auth first. Agreed sequence: `UserDetailsService`/`UserDetails` → route login through `AuthenticationManager` → JWT issuance → validation filter + `SessionCreationPolicy.STATELESS`.

**2026-08-01 — Replace the hand-rolled credential check in `AuthenticationService.logIn` with `AuthenticationManager`.**
`logIn` currently does its own `findByEmail` + `passwordEncoder.matches`, which is a partial reimplementation of `DaoAuthenticationProvider`. Developer's original reasoning was deliberate — hand-rolling to understand the framework. Accepted as a valid learning motive, but the partial version is missing: account-status checks (`User.enabled` is set at signup and never read, so disabled users can log in today), user-enumeration protection (distinguishable "Email not found" vs "Incorrect password", and no timing-attack mitigation on the not-found path), a usable `AuthenticationException` taxonomy, and a populated `Authentication` matching what the JWT filter will later produce. Developer agreed to switch. Depth argument that settled it: the JWT filter needs a `UserDetailsService` regardless, so wiring the framework path means one user lookup instead of two.

**2026-08-01 — Separate adapter class for `UserDetails` rather than `User implements UserDetails`.**
Three options weighed: adapter, entity implements the interface directly, or Spring's `User.withUsername(...)` builder. Developer chose the adapter — keeps Spring Security out of the JPA entity, consistent with the DTO boundary the controllers will need anyway. Note the import collision hazard: `org.springframework.security.core.userdetails.User` vs the project's own `User` entity. Entity name kept as `User` (not `AppUser`/`UserAccount`); table is already `users`, which avoids the Postgres reserved-word problem.

**2026-08-02 — JWT via Spring Security's `oauth2-jose` (resource server), not `jjwt` or raw `nimbus-jose-jwt`.**
Three options weighed. Trade-off named explicitly before choosing: `jjwt`/Nimbus would require hand-writing the `OncePerRequestFilter` in step 4, which was that step's stated learning goal (how a filter builds an `Authentication`, populates the `SecurityContext`, and why filter-thrown exceptions bypass `@RestControllerAdvice`). `oauth2-jose` supplies `JwtEncoder`/`JwtDecoder` beans and an `oauth2ResourceServer().jwt()` DSL, so validation becomes configuration rather than code. I leaned toward `jjwt`-then-compare for learning depth; developer chose `oauth2-jose`. Their call, and it is the more idiomatic production answer — the filter-chain material can be recovered later by reading `BearerTokenAuthenticationFilter`, which is what the DSL installs.
Consequence to watch: with a resource server, `UserDetailsService` is **no longer consulted per request** — the token itself is the identity. `SecurityUserService` becomes a login-only component, and the request principal becomes a `Jwt`, not `SecurityUser`. This retires the long-running "SecurityUser holds a live entity / lazy-loading" question by removing the per-request lookup entirely, but it moves the problem: Phase 2 ownership checks must resolve a user from a token claim rather than from an injected `UserDetails`.

**2026-08-02 — JWT parameters: HMAC (HS256), `sub` = numeric user id, 24h lifetime, no refresh token.**
- *HMAC over RSA:* one service mints and verifies; a keypair only earns its complexity when a separate service must verify without being able to mint. Shared secret lives in `application.properties` per the hardcoded-secrets decision.
- *`sub` = user id, not email:* emails change; Phase 2 ownership checks will key on the id. Developer's reasoning was that the token carries identity, not data — correct, and the reason activity data stays in the DB and out of claims.
- *24h, no refresh:* accepted deliberately for a personal project. **Known limitation, not an oversight:** a stateless token can't be revoked, so logout is a client-side no-op and a leaked token is valid for a full day. Refresh tokens (short-lived access + revocable refresh) are the standard fix if this ever matters.
Note: `SecurityUserService.loadUserByUsername` still keys on email while `sub` holds an id. Not a conflict — the lookup is login-only once the resource server is in place.

**2026-08-01 — Drop `User.tokenExpired`.**
Developer couldn't recall the original intent and judged it out of scope for the MVP. It was a candidate home for `UserDetails.isCredentialsNonExpired()`, but nothing sets or reads it. Removal requires a *new* Liquibase changeset with `dropColumn` — the existing changelog must not be edited, since applied changesets are checksummed. This is the second changeset file, which is the trigger condition named in the 2026-07-25 single-changelog decision: `spring.liquibase.change-log` now needs to point at a master changelog with `include`s.

## 2026-09-11 — Roles model: no table, single constant

**Decision:** no `roles` table, no `role` column, no `@ManyToMany`. A `Role`
enum (`user/model/`) with a single constant `USER`, and `SecurityUser` grants
it to everyone unconditionally.

**Reasoning:** Phase 2's sharing model is ACL-style — a `PermissionEvaluator`
over per-object permissions, keyed off the subject id. Role authorities do not
participate in that decision at all. The only work global roles will ever do
here is an admin/ordinary-user split that does not exist yet. Options weighed
were (a) constant, (b) a `role` column on `users`, (c) `roles` + `user_roles`
with `@ManyToMany`. (c) is what most tutorials show, and it would have dragged
the lazy-loading / `open-in-view` hazard directly into the authentication path
for a system with exactly one role. Chose (a); revisit when a second role
actually exists, at which point the change lands in `SecurityUser` and one
changeset.

## 2026-09-11 — Custom `authorities` claim, not the default `scope`

**Decision:** the token carries a custom `authorities` claim holding unprefixed
role names (`["USER"]`). The `ROLE_` prefix is to be applied inbound by a
`JwtGrantedAuthoritiesConverter` configured with
`setAuthoritiesClaimName("authorities")` + `setAuthorityPrefix("ROLE_")`.

**Reasoning:** Spring's default converter reads `scope`/`scp` and prefixes
values with `SCOPE_`. Taking that default would have meant `hasRole` never
works — which is the exact complaint this work item exists to fix — and would
have skipped `JwtAuthenticationConverter` entirely, the thing worth learning
here. `scope` is also semantically an OAuth2 delegation concept; this is a
first-party login token, not a delegated one.

**Consequence to remember:** the claim name and the converter's
`setAuthoritiesClaimName` must agree. A mismatch fails silently with an empty
authority list, not with an error.

## 2026-09-11 — Authorities unprefixed at the source (landed, not deliberated)

**Decision:** `SecurityUser.getAuthorities()` returns `USER`, not `ROLE_USER`.

**Status:** this is how the code landed rather than something argued through.
Reviewer flagged the consequence and the developer did not rule on it before
the session ended — logging both positions so it gets settled rather than
inherited.

- **Reviewer's position:** prefix at the source and strip in `TokenService`,
  so the same user carries the same authority string no matter which path they
  arrived on.
- **As built:** unprefixed at the source, prefix added only inbound by the
  converter. Simpler `TokenService` (nothing to strip), but produces an
  asymmetry — after login the principal holds `USER`, after a token-bearing
  request it will hold `ROLE_USER`.

The asymmetry is currently harmless because `/api/auth/**` is `permitAll`, so
no authorization decision is ever made against the login-path authorities.
It stops being harmless if anything ever authorizes on the login path.

## 2026-09-11 — Roles are a login-time snapshot

**Decision:** accepted that authorities are baked into a 24h token at login
with no revocation path before expiry.

**Reasoning:** the resource server does not consult `UserDetailsService` per
request, so a role change cannot take effect until the token expires. With one
role that can never change, this costs nothing today. Named explicitly so it is
a known trade rather than a surprise when a second role appears — at which
point the options are short token lifetimes, a refresh flow, or a denylist.

## 2026-09-13 — Prefix asymmetry resolved: unprefixed at the source

**Resolves the open 2026-09-11 entry** ("Authorities unprefixed at the source
(landed, not deliberated)").

**Decision:** `SecurityUser.getAuthorities()` keeps returning `USER`, not
`ROLE_USER`. The `ROLE_` prefix is applied only inbound, by
`JwtGrantedAuthoritiesConverter`.

**Reasoning (reviewer reversed an earlier lean, developer accepted):** the
login-path authorities have exactly one consumer, `TokenService`, and it wants
the unprefixed form. Prefixing at the source only to strip it back off buys a
symmetry that no code observes. The asymmetry — `USER` on the login path,
`ROLE_USER` on the request path — stays inert as long as nothing authorizes
before a token is issued, which `/api/auth/**` being `permitAll` guarantees.

**Underlying principle, worth keeping:** `ROLE_` is a Spring Security internal
convention, not data. `hasRole("X")` is sugar that concatenates `ROLE_` and
calls `hasAuthority("ROLE_X")` — the framework has one flat string space for
authorities and uses prefixes to namespace roles against OAuth2 scopes
(`SCOPE_`). Keeping the prefix out of the token means the wire format is not
shaped by a framework detail. Same principle that made the earlier
`{"role":"USER"}` serialisation wrong.

**Revisit if:** anything ever authorizes on the login path.

## 2026-09-13 — Converter left implicitly wired (open, against recommendation)

**State:** the `JwtAuthenticationConverter` bean is connected to the resource
server by `JwtConfigurer`'s implicit bean lookup, not by an explicit
`jwt(jwt -> jwt.jwtAuthenticationConverter(...))` call. Line 39 still reads
`oauth2.jwt(Customizer.withDefaults())`.

**Verified, not assumed:** confirmed empirically on 2026-09-13 by the
404/403 experiment. The reviewer declined to assert the lookup existed from
memory; the developer's test settled it.

**Reviewer's position:** wire it explicitly anyway. The connection is invisible
to anyone reading `SecurityConfig`, and it would break silently — empty
authority list, 403s, no error — if a second `Converter<Jwt, ...>` bean were
ever added to the context.

**Developer's position:** not yet stated; the sprint ended first. Logged as open
rather than settled.

## 2026-09-14 — `Activity` is a completed session, nothing more

**Decision:** `Activity` records that a session happened — type, when, how
long, free-text notes. It is not a plan, not a template, and carries no
description of what was performed inside it.

**Reasoning:** stating it positively also states what is excluded, which is
what stopped the model drifting. Scheduled/planned workouts would need a
status field or a separate entity; neither is in scope.

## 2026-09-14 — Activities are ownerless in this slice

**Decision:** no `user_id` on `activities`. Every user sees every activity.

**Reasoning:** strict Phase 0 reading — the slice is about entity → repository
→ service → controller mechanics, and Phase 2 owns the multi-user model.
Reviewer recommended adding the owner FK now on the grounds that it is the
cheap slice of Phase 2 (no ACL table, no `PermissionEvaluator`, just "you see
your own rows") and that retrofitting is the expensive direction. Developer
chose to keep Phase 2 genuinely unstarted.

**Known cost, accepted:** the later `user_id` will be a NOT NULL FK added to a
populated table, so it needs a backfill or a truncate of dev data.

## 2026-09-14 — Flat model; no JPA inheritance

**Decision:** one `activities` table with only the columns every activity has.
No `@Inheritance`, no type-specific nullable columns.

**Reasoning:** options weighed were flat-with-nullable-extras, flat-minimal,
and a `Run`/`Ride`/`Strength` inheritance hierarchy. Inheritance is a large JPA
topic — discriminators, polymorphic queries, DTOs across the hierarchy — and
would have become the subject of the slice instead of CRUD.

**Discovered afterwards, and worth keeping:** inheritance would not have solved
the strength-training case anyway. A run's extra data is *scalar* (distance,
pace — more columns); a lifting session's is a *collection* (an ordered list of
sets). Subclass columns cannot hold a list, so a child table was always the
answer. Declining inheritance cost nothing.

## 2026-09-14 — Sets/reps deferred, deliberately and cheaply

**Decision:** set-level detail (exercise, reps, weight, RIR) is not modelled
now. It arrives later as an `activity_sets` child entity, probably with an
`exercises` lookup table.

**Reasoning:** unlike the ownership deferral, this one is structurally free —
a child table is purely additive, an FK pointing at `activities`, with no
change to any existing column. And the relationship work it brings (cascade,
orphan removal, fetch strategy, nested DTOs, N+1) is a topic that deserves its
own session rather than being tangled with first-time CRUD mechanics.

**Note:** this is where `spring.jpa.open-in-view` finally stops being
theoretical.

## 2026-09-14 — `spring.jpa.hibernate.ddl-auto=validate` adopted

**Decision:** Hibernate validates every entity mapping against the real schema
at startup and refuses to boot on a mismatch.

**Reasoning:** Liquibase owns the schema, so Hibernate should never disagree
with it; `validate` turns any disagreement into a loud startup failure instead
of a runtime surprise. Adopted originally as a way to answer "what column type
does Hibernate expect for `Duration`" without guessing, and kept on its own
merits — it caught a missing `@Enumerated(EnumType.STRING)` before a single row
existed.

**Limit, learned the same day:** `validate` compares column *types*, not stored
*units* or nullability semantics. It cannot tell you whether a `numeric`
duration holds seconds or nanoseconds.

## 2026-09-28 — A `test` Spring profile exists, against the Phase 0 constraint

**Decision:** test configuration lives in
`src/test/resources/application-test.properties`, activated by
`@ActiveProfiles("test")`.

**Conflict:** Phase 0 says "single environment — no Spring profiles, no dev/prod
split, no environment-conditional config." This crosses that line.

**Reasoning for accepting it:** the profile carries test scaffolding (a throwaway
`jwt.secret`), not environment configuration, and no production code branches on
it. The alternative the reviewer preferred — `@TestPropertySource` on the classes
that need the property — avoids profiles entirely and stays inside the
constraint. Developer chose the profile file; logged rather than argued.

**Prior attempt, worth remembering:** a plain `application.properties` under
`src/test/resources` **shadows** the main one rather than merging with it (same
classpath filename, test classpath wins). That silently dropped every other
property, including `spring.liquibase.change-log`, and the failure surfaced as a
Liquibase YAML parse error. Profile-specific files overlay; same-named files
replace.

## 2026-09-28 — Testing approach: slices, bottom-up

**Decision:** tests are written as narrow slices, introduced one rung at a time —
plain JUnit, then Mockito, then `@WebMvcTest`. `@SpringBootTest` is reserved for
the single context-load test.

**Reasoning:** matches the idiom checklist's preference for `@DataJpaTest` /
`@WebMvcTest` over `@SpringBootTest` for everything, and each rung introduced
exactly one new concept for a developer who had never written a Spring test.

**`@DataJpaTest` deliberately skipped for now** — the repository has no custom
queries, so it would mostly exercise Spring Data's generated methods. It becomes
worth writing in Phase 2, when ownership introduces scoped queries such as
`findAllByUserId`.

**Recurring lesson across all three test classes:** the first draft of each
asserted something that could not fail — the mapper test asserted its own input,
the service test read the wrong captor, the delete test verified before acting,
and the 400 test made two fields invalid at once. Rule adopted: **make it fail on
purpose before trusting it green.**

## 2026-09-28 — Phase 2 ownership: FK now, and the four choices around it

**Decision:** `activities.user_id`, NOT NULL, FK to `users(id)`, plus an index
on it. Supersedes the 2026-09-14 "activities are ownerless" decision.

Four sub-decisions, all presented with alternatives and chosen by the developer:

1. **Migration: reset the database, one clean changeset.** `docker compose
   down -v`, then a single `addColumn` with the NOT NULL constraint. The
   alternative was expand/backfill/contract (add nullable → `<update>` → add
   NOT NULL), which is the real zero-downtime production pattern but was
   ceremony over ~5 throwaway dev rows. Side benefit taken deliberately: the
   wipe replays the whole changelog from empty, which re-proves the chain.
   Verified in psql — `user_id bigint not null`, `fk_activities_user`,
   `idx_activities_user_id`, all five changesets in `databasechangelog`.

2. **Owner resolved in the controller, passed down as `Long`.** Alternatives
   were the service reading `SecurityContextHolder` itself, or a custom
   `@CurrentUserId` argument resolver. Reasoning that settled it: the
   dependency on "who is asking" becomes a visible parameter instead of a
   hidden static read, and `ActivityServiceTest` keeps working with plain
   Mockito. `@CurrentUserId` was named as the right answer *later*, once the
   five-fold repetition of `Long.valueOf(jwt.getSubject())` has been felt.

3. **404, not 403, for another user's row.** Falls out of
   `findByIdAndUserId(...).orElseThrow(ActivityNotFoundException::new)` with no
   second branch, and leaks no existence oracle. 403 would have required
   loading by id first and comparing the owner.

4. **`@ManyToOne(fetch = LAZY)` association over a bare `Long userId` column.**
   The association is what Phase 2's sharing slice will want to navigate.
   `LAZY` is explicit because `@ManyToOne` defaults to EAGER.

**`getReferenceById` over `findById` in `create`.** The INSERT needs only the FK
value, and the proxy carries its identifier without initialising, so no SELECT
against `users` is issued. Cost named and accepted: no existence check, so a
bad id surfaces as `DataIntegrityViolationException` → 500 rather than a clean
error. Judged unreachable here because the id comes from a signed token and
`User` is soft-deleted via `deleted_at`, so the row never leaves `users`. If
users were ever hard-deleted, a token issued before the delete stays valid for
its full 24h and this trade becomes real.

## 2026-09-28 — `@Transactional(readOnly = true)` declined on the read methods

**Decision:** `findAll` and `findById` stay on the class-level `@Transactional`.
Developer's position: neither method mutates anything, so nothing would be
flushed either way and the annotation changes no behaviour. Correct as stated.

**Reviewer's position, logged for the record:** the argument was never
correctness. It was (a) Hibernate loading entities as read-only and skipping the
loaded-state snapshot, which matters at Phase 5 reporting volumes rather than
now, and (b) `Connection.setReadOnly(true)` reaching Postgres as
`SET TRANSACTION READ ONLY` under pgjdbc's default `readOnlyMode=transaction`,
making the database itself refuse a write in that transaction. The honest
counter-argument, which is why this is a preference and not a rule: if a read
method later does start mutating, `readOnly` makes it a **silent** no-op — no
flush, no SQL, no error.

**Status: settled.** Not to be raised again as a review finding.

## 2026-09-28 — MVP first; test rigor relaxed until MVP milestone

**Decision (developer):** get a usable end-to-end product — backend plus a
first frontend — before polishing. Tests, coverage targets and idiom purity
are advisory until then. `CLAUDE.md` updated: Phase 3 is now "Frontend MVP",
Media/Integrations/Reporting/Ops shifted to 4–7. Roadmap carries a debt list
for things skipped on purpose.

**Reviewer's position:** agreed, with one caveat logged — the ownership /
sharing queries are the one area where a missing regression test can turn
into a data leak rather than a bug. Kept on the debt list at the top.

## 2026-09-28 — Whole-log sharing via a `shares` table

**Decision:** A shares their whole activity log with B. Read-only. Table
`shares(id, owner_id, viewer_id, created_at, deleted_at)`, both FKs to
`users`, partial unique index on `(owner_id, viewer_id) WHERE deleted_at IS
NULL`, check `owner_id <> viewer_id`. Grant is owner-initiated by viewer
email; revoke is soft delete. No request/accept flow.

**Alternatives considered and rejected:**
- *Per-activity sharing* — finer control, more rows, more UI; not needed.
- *`public_uuid` link on `activities`* (developer's proposal) — capability
  URL, "anyone with the link". Legit pattern but no grantee, global-only
  revocation, and the check is `WHERE public_uuid = ?` with no principal
  involved, so it teaches nothing about authorization. Parked as a possible
  later feature, not a replacement.
- *Everyone sees everything* (briefly proposed) — dropped same session in
  favour of learning sharing properly.

**`deleted_at` soft delete on the join row** — developer's call. Reviewer
flagged the cost: every read must filter it, and the unique constraint must
be partial or re-sharing after revoke fails. Developer chose Hibernate
`@SoftDelete(strategy = TIMESTAMP)` so the filter is automatic.

**Where the read check lives — open.** Two options presented:
(A) inline `assertCanRead(ownerId, callerId)` in `ActivityService`, caller
passed as a second parameter to match the existing convention;
(B) `@EnableMethodSecurity` + custom `PermissionEvaluator` +
`@PreAuthorize("hasPermission(#ownerId, 'ActivityLog', 'read')")` on
`findAll` and `@PostAuthorize` on `findById`. Reviewer recommends B (it is
the Phase 2 lesson and keeps `ShareRepository` out of `ActivityService`).
Developer leaning A for MVP speed. Not yet decided; activity controller and
service were rolled back at end of session.

**Read endpoint shape:** `GET /api/activities?owner={userId}`, default self.
Same `findAll` method, parameter now means "whose log". Foreign, non-shared
log → 403 (not 404). `ActivityResponse` gains `ownerId`.

## 2026-10-05 — Read guard: inline check in `ActivityService` (option A)

**Decision (developer, logged 2026-10-08):** `findAll(userId, ownerId)` guards
inline — owner or `shareRepository.existsByOwnerIdAndViewerId` — and throws
`ForbiddenException` → 403. `ShareRepository` is injected into
`ActivityService`. Chosen for MVP speed.

**Reviewer's position, for the record:** `@EnableMethodSecurity` +
`PermissionEvaluator` + `@PreAuthorize("hasPermission(...)")` keeps the
share lookup out of the activity service and is the Phase 2 lesson. Still
the recommended refactor once a second guarded read appears. Not to be
re-raised until then.

## 2026-10-08 — Share-slice and activity tests skipped; suite left non-compiling

**Decision (developer):** "let's skip test for now." Suite currently fails
at `compileTestJava` (three sites, see ROADMAP debt). Accepted under the
MVP-first rule of 2026-09-28.

**Reviewer's position:** non-compiling is worse than red — it hides every
other regression too. Logged at the top of debt; first thing after MVP.

## 2026-10-08 — Frontend stack: React + Vite

**Decision (developer):** React + Vite, separate dev server, calling the
existing bearer-JWT API. Developer already knows React; frontend is a
window onto the backend, not the learning target.

**Rejected:** Thymeleaf/htmx — fights the stateless JWT auth model, would
need a cookie/session path. Plain static HTML + fetch — viable but developer
knows React, no reason to go lower.

**Open sub-decisions (to be settled as they come up):** `frontend/` in this
repo vs separate repo; TypeScript vs JS; Vite dev proxy vs Spring CORS
config; token storage (`localStorage` vs memory).

## 2026-10-08 — Reviewer scaffolded and wrote the frontend

**Decision (developer):** "do the scaffold and basic setup. I know react
and typescript well enough. I don't need to focus on that right now."
Overrides the no-scaffolding rule in CLAUDE.md. Trade-off named once:
that rule guards Spring learning; frontend is explicitly not the target.
Reviewer ran `npm create vite` and wrote the whole MVP frontend.

**Sub-decisions:** `frontend/` inside repo (one history, one `.claude/`);
TypeScript (DTO drift surfaces at compile); Vite proxy over Spring CORS
(zero backend change, CORS logged as debt); `localStorage` token (simplest,
XSS-readable, logged as debt).

## 2026-10-09 — Post-MVP test debt deferred again; Phase 4 starts

**Decision (developer):** "let's skip these right now and jump to media."
Items 1–4 of the proposed post-MVP order (compile fix, `@DataJpaTest`
scoping, share tests, auth tests) stay on the debt list.

**Reviewer's position, logged once:** the suite does not compile, and the
scoping queries have no regression guard. Phase 4 adds a *third* guarded
read path (media download by owner or shared viewer), so the surface that
can leak grows before any of it is covered. Named, not re-raised.

## 2026-10-09 — Phase 4 media design (one-round decisions)

1. **Media attaches to `Activity`, many per activity.** `media.activity_id`
   NOT NULL FK. Ownership and sharing inherited through the activity.
2. **Bytes on local filesystem behind a `StorageService` interface**, root
   directory from a property. One implementation now; S3/MinIO is a second
   implementation later, not a rewrite.
3. **Download authorizes with the activity rule: owner or shared viewer.**
   Third site for that rule (after `ActivityService.findAll` and, implicitly,
   the shared-log page). Extraction of the rule into one place becomes due
   in this phase — form (plain shared method vs `PermissionEvaluator`) still
   the developer's call.
4. **Minimal metadata: `media(id, activity_id, storage_key)`** — developer's
   choice over the fuller row (`content_type`, `size_bytes`,
   `original_filename`, `created_at`). Reviewer flagged the corner: the
   download endpoint has to set `Content-Type` from somewhere. Either it
   gets stored at upload, or it is sniffed from bytes/extension on every
   download. Open until the download endpoint is written.

## 2026-10-10 — Media: unidirectional mapping, service-level cleanup

1. **`Media → Activity` unidirectional**, no `@OneToMany List<Media>` on
   `Activity`. Reasoning: a LAZY collection on `Activity` invites an N+1 on
   `GET /api/activities` the moment the mapper touches it, needs two-sided
   sync helpers, and the frontend fetches media per activity anyway (step
   6 endpoint). Matches the existing `Activity → User` pattern. Cost: one
   derived query, `findAllByActivityId`, and step 7 looks media up by
   repository instead of `activity.getMedia()`.
2. **No `ON DELETE CASCADE` on `fk_media_activity`; cleanup in
   `ActivityService.deleteById`.** DB cascade would delete rows but orphan
   files on disk. Service deletes media rows + files, then the activity.
   Until step 7 wires it, deleting an activity that has media fails on the
   FK (500) — known, temporary.

## 2026-10-14 — Upload allowlist: `image/jpeg` only for MVP

Spec said `image/*`. Developer chose strict `equals("image/jpeg")` for MVP.
Reviewer position: prefix match on `image/` costs nothing more. Developer's
call; smallest surface first. Widen when the frontend needs PNG/WebP.
Allowlist is on the client-sent `Content-Type` part header — trust boundary
question (sniff magic bytes or not) left open, not an MVP blocker.
Rejection → `UnsupportedMediaTypeException` → 415, lives in `MediaService`.
`content_type` column added (changeset `2026/10/10-02`) so download never
sniffs.

## 2026-10-18 — Owner-or-viewer rule: `@EnableMethodSecurity` + `PermissionEvaluator`

Context: rule `caller == owner || share(owner → caller)` existed inline in
`ActivityService.findAll`, copied into `MediaService.getMedia`, and step 6
list would make a third copy. Reviewer extracted a plain
`ShareAccessService.assertCanRead(ownerId, callerId)` on request (simplest
thing, matches pre-MVP "simplest design" rule).

Decision (developer): replace it with method security. `@PreAuthorize(
"hasPermission(#id, 'Media', 'READ')")` etc., backed by `ShareEvaluator
implements PermissionEvaluator`, registered via a static
`MethodSecurityExpressionHandler` bean.

Reasoning: learning goal explicitly; authorization visible at the method
signature; one evaluator serves every future read rule (step 6 list, later
write rules via a second permission string).

Reviewer position: more machinery than needed pre-polish (AOP proxy, SpEL
arg binding, static bean ordering, tests must go through a context to
exercise it). Named the trade-off, developer chose it. Not contested.

Consequences accepted:
- Unknown resource id → 403, not 404. Evaluator runs before the service can
  say "not found". (Pending: confirm in step 5b.)
- `AccessDeniedException` must have its own `@ExceptionHandler`; the
  catch-all `Exception.class` handler otherwise returns 500. Found the hard
  way on first run.
- Unit tests that `new` a service bypass the proxy; security is only
  exercised through a Spring context.
- `ForbiddenException` likely becomes dead.


## 2026-10-24 — Unknown media id returns 403 (resolves pending item above)

Decision (developer): accept 403 for a nonexistent media id. `ShareEvaluator`
returns false when `mediaRepository.findById` is empty, and `@PreAuthorize`
runs before `MediaService.getMedia` can throw `MediaNotFoundException`.
`http/media.http` expectation changed 404 → 403.

Alternative not taken: evaluator returns true on a missing row so the
service 404s. Equally safe, since a missing row has no owner to leak.

Consequences: `MediaNotFoundException` is unreachable from `GET
/api/media/{id}`. Same id is looked up twice per allowed download
(evaluator + service). Guard sits on `MediaController.getMedia` while the
activity guard sits on `ActivityService.findAll`; placement left
inconsistent, not decided.

## 2026-10-30 — Media delete authorization and activity cleanup

Decision (developer): media delete guarded by `@PreAuthorize("hasPermission(
#id, 'Media', 'DELETE')")`. `ShareEvaluator` grants DELETE to the owner only;
shares stay read-only. Consequence: foreign, shared-viewer, unknown and
already-deleted media ids all return 403, not 404. Activity write paths
(including activity delete) keep `findByIdAndUserId` → 404 for foreign ids.
Two conventions now coexist: media endpoints 403, activity writes 404.

Reviewer suggested a derived owner query (404, evaluator stays read-only);
developer chose the evaluator. Not contested.

Activity delete with media: service-level cleanup as decided 2026-10-10.
`MediaService.deleteAllForActivity` deletes rows inside the caller's
transaction and registers file deletes in `afterCommit`; a failed file
delete is logged and leaves an orphan on disk. Rows go before the activity
so `fk_media_activity` holds. `deleteAllForActivity` is public and unguarded;
safe only because `ActivityService.deleteById` checks ownership and owns
the transaction.

## 2026-10-30 — Frontend images: blob + object URL

Decision (developer): `<img>` cannot send `Authorization`, so the client
fetches `GET /api/media/{id}` with the bearer token, turns the response
into a Blob, and renders it via `URL.createObjectURL`. No backend change.

Alternatives deferred: short-lived signed URLs, or cookie auth for image
requests. Both need backend work; revisit if per-image fetches or memory
become a problem.

Costs accepted: one list request per activity row plus one request per
image (N+1 from the browser); full-size images downloaded for thumbnails
(no server-side resizing this phase); object URLs must be revoked on
unmount or they leak.

## 2026-11-01 — Share errors: explicit exceptions, generic 409 sanitized

Made by reviewer during the requested cleanup; developer to confirm.
`ShareService.create` now checks self-share → `SelfShareException` (400)
and active duplicate (`existsByOwnerIdAndViewerId`) → `ShareExistsException`
(409, "Share already exists") before saving. Previously both surfaced as
`DataIntegrityViolationException` with the raw constraint/SQL text in the
body. The DB constraints (`ck_shares_not_self`, `uq_shares_active_pair`)
stay as the race backstop; the generic `DataIntegrityViolationException`
handler still maps to 409 but returns a fixed message and logs the detail.
Cost: one extra `exists` query per grant.

## 2026-11-01 — `User` timestamps: drop and re-add as `timestamptz`

`created_at`/`updated_at`/`deleted_at` were `BIGINT` mapped to primitive
`long`, never populated (always 0). Changeset `2026/11/01-01` drops them and
re-adds `timestamptz` (`created_at`/`updated_at` NOT NULL default `now()`,
`deleted_at` nullable); `User` uses `Instant` + `@CreationTimestamp`/
`@UpdateTimestamp`, matching `Activity`. No data lost — every value was 0.
Existing rows get the migration time as `created_at`. Same changeset drops
`token_expired`. `deleted_at` kept but unused (no user soft-delete yet).

## 2026-11-01 — Signup field limits

`@Size(max = 255)` on email/names (column width). Password `max = 72`:
BCrypt silently ignores bytes beyond 72, so longer passwords would give a
false sense of strength.

## 2026-11-02 — Phase 5 first integration: LLM-mapped file import instead of Google Sheets OAuth

Developer's choice: the user uploads a CSV / Excel / TXT file, the backend
calls an LLM through the OpenRouter API to map it to the `Activity` format,
and the user previews the result and confirms or rejects it. No OAuth2
client and no scheduling for now.
Reviewer's earlier suggestion was Google Sheets via the OAuth2
authorization-code flow (per-user refresh tokens, linking the OAuth flow to a
stateless JWT session). Reasoning for the switch: works with any source
format, no token storage, no redirect flow to reconcile with JWT. Trade-off:
the OAuth2-client learning goal moves to later (Sheets is deferred, not
dropped); user data is sent to a third-party LLM provider; output is
non-deterministic, so it needs a confirm step and server-side validation.

## 2026-11-02 — Import: reject the whole file on any validation failure; 8 MB limit

If any mapped row fails validation, nothing is imported. Simpler than
partial imports, and the user gets no half-imported log to clean up. The
upload limit for imports is 8 MB. `spring.servlet.multipart.max-file-size`
is already 10 MB globally (for media), so the 8 MB limit has to be checked
in the import endpoint or service. Sending data to a third-party LLM:
developer accepts it, not a concern for now.

## 2026-11-02 — Import: 10 MB limit, raw text to the LLM, session details as `jsonb`

- Upload limit: reuse the existing global 10 MB multipart limit. This
  supersedes the 8 MB entry above; not important at this stage.
- No Java-side parsing of the file into a grid. The raw file text goes
  straight to the LLM, which returns JSON in our format; Java validates it;
  the user reviews it and adds dates. Reviewer's earlier position was to
  parse in Java first. Caveat: `.xlsx` is a binary zip, so it can't be sent
  as text. It needs a text-extraction step (Apache POI) or a "save as CSV"
  rule.
- Exercise/set details live in a single `jsonb` column on `activities`,
  not in new `Exercise`/`Set` tables. Developer's reasoning: hobby project,
  simplest thing that works. Reviewer's earlier position was new entities.
  Corner named: Phase 6 reporting on exercise data (e.g. volume per week)
  then needs Postgres `jsonb` operators or native queries instead of JPQL.
  The JSON shape needs to be versioned by hand.

## 2026-11-02 — Import scope: CSV only; Estonian names; JSON validated in `ActivityRequest`

- Phase 5 import supports CSV only. Excel and TXT are out for now, so no
  POI, and the caveat above about `.xlsx` being binary is moot.
- Exercise names stay as written in the source (Estonian), not translated.
  Keeps names stable across imports for later grouping.
- The session-details JSON is validated by records nested in
  `ActivityRequest` with `@Valid`. The user can edit past exercises, so the
  same validation applies to manual create/update and to import confirm.

## 2026-11-02 — Exercise comments in the JSON; `duration` nullable

- Per-exercise comments (e.g. "filmi, KP") are stored on the exercise
  inside the details JSON. `notes` stays for session-level text, which
  avoids `varchar(255)` overflow under the reject-everything import rule.
- `Activity.duration` becomes nullable: the source files have no duration.
  Knock-on effects: changeset drops `NOT NULL`; `ActivityRequest` loses
  its required-field validation on duration; mapper and frontend handle
  null; Phase 6 aggregates must treat null as "unknown", not zero.

## 2026-11-02 — Local DB is disposable; edit applied changesets instead of stacking fixes

The app is local-only, and the developer is fine with resetting the
database. A bad changeset can be edited in place, followed by a DB reset,
instead of adding a corrective changeset. This stops applying the moment a
second environment or real data exists (Phase 7 at the latest); from then
on, applied changesets are immutable. A reset also wipes users, activities
and shares, and leaves media files orphaned under `storage/`.

## 2026-11-05 — Annotation suggestions must name the exact target
Developer request after `@Qualifier` landed on the class instead of the constructor parameter (my 4c note said "with `@Qualifier`" without saying where). Rule added to CLAUDE.md Hard rules: every annotation suggestion names the target kind (class/method/constructor/parameter/field/record component/enum constant/`@Bean` method) and the concrete member.

## 2026-11-05 — Import scope: done workouts only
Developer: planned-only workouts not imported; empty weeks skipped; bodyweight = weight 0. LLM output shape is CSV-like (exercise-level weight + reps[]), Java expands to per-set `Session` (dev proposed exercise-level shape; I agreed for LLM output, storage stays per-set for editing/pyramids). Enforcement: prompt + `@NotEmpty` on reps/exercises so violations go through the retry loop. Default (not yet confirmed): exercise without reps inside a done workout is omitted.

## 2026-11-12 — Pivot: from learning Spring by hand to an AI software factory PoC

**Developer's decision.** The developer stops writing application code and
stops treating Spring syntax as the thing to learn; AI builds the app and
the infra. New goal: a proof of concept in which one developer ships a
working product that improves itself (issue or alert in → tested change
out → human approval → prod → monitoring → back in).

- Old position (2026-07 → 2026-11-10): developer writes the code, agent
  reviews and never writes unprompted; phases 0–7 teach idiomatic Spring.
- Reasoning given by the developer: syntax is no longer the bottleneck,
  and enough was learned by writing the first five phases by hand.
- Reviewer's note at the time: on 2026-11-10 a split was proposed
  (learning zone vs. factory zone). The developer chose the full pivot
  instead.
- **Anti-goal (developer's words):** failure is "if I don't understand what
  I have actually welded together here". Hence ARCHITECTURE.md, plain-
  language explanations, and explain-back checkpoints per phase.

Also decided by the developer on 2026-11-12:
- GitHub Actions as CI and agent runtime.
- Budget €20/month covers VPS, backups, domain, and the app's own
  OpenRouter calls. Agent tokens are outside it.
- Agent runtime plan A: the developer's Claude Code subscription, if it allows unattended
  runs (unknown — developer checks). Plan B: OpenRouter API with a lighter
  model, on its own monthly cap. Unattended triggering is part of the PoC
  in both.
- Product scope: finish CSV import; manual weight and steps entry; Garmin
  sync and Xiaomi scale stay as later factory tickets; Google Sheets
  dropped; reporting undecided.
- The frontend prototype (Design canvas, copied to `docs/design/prototype/`)
  is the product target.
- Tool choices delegated to the builder, bare minimum only.

**Builder's calls** (developer may veto): private repo; Claude Code GitHub
Action driven by labels; one Hetzner VPS with Docker Compose and Caddy;
prod + one staging slot on the same box; GHCR; SSH deploy; nightly backup
of database and media with a real restore test; Sentry free tier + external
uptime check; closed signup before going public; free GitHub plan with a
self-hosted runner as the fallback; Actuator health only; no CORS config
(single origin); reporting deferred; the prototype is the backlog, not a
gate on "PoC done"; where the prototype conflicts with pre-pivot decisions
(read-only sharing vs. viewer comments, CSV-only import vs. Excel and
multiple record kinds, direct grant vs. invite) the prototype wins.

## 2026-11-12 — Planning docs: old roadmap archived, session-start reading narrowed

Builder's call. `.claude/ROADMAP.md` rewritten around factory phases; the
old one is kept privately by the developer. The four docs had grown to ~200 KB read
at every session start. New rule in `CLAUDE.md`: read ROADMAP,
ARCHITECTURE and the last entries of NOTES/DECISIONS; search the rest.
The "annotation placement" rule of 2026-11-05 is retired with the reviewer
role.

## 2026-11-12 — Protecting `main` (open)

Tried to enable branch protection (require `backend` + `frontend` checks)
via the API. GitHub answered 403: "Upgrade to GitHub Pro or make this
repository public to enable this feature." Until the developer chooses,
the builder's default applies: changes arrive by PR by rule, and F2's
deploy step will run only after both checks pass. Options put to the
developer: (1) keep the default, €0; (2) make the repo public, €0, code
and issues visible; (3) GitHub Pro, about $4/month.

## 2026-11-12 — JWT test secret in git history accepted (builder's call)

The value now used by the `test` profile was once in the main
`application.properties`, so it is in history. No other secret was found
in history. Accepted because it is a dev/test-only value. Condition: F2
generates a fresh prod secret and this value is never reused.

## 2026-11-12 — Open source: the repo becomes public (resolves "Protecting `main`")

**Developer's decision.** The project is open source: an AI-factory PoC
and a portfolio/demo project to be shared publicly. With a public repo,
branch protection on `main` is available on the free plan and Actions
minutes are unlimited.

What is public: code, `CLAUDE.md`, ROADMAP, SPEC, DECISIONS, ARCHITECTURE,
design and plan docs, session notes from the pivot onwards. What is not:
secrets, other people's data, and the pre-pivot session notes and old
roadmap, which stay on the developer's machine in the git-ignored
`.claude/private/`.

Clean-up before publication (builder's calls, developer approved the
clean-up as a whole): git history rewritten so every commit carries the
GitHub noreply address; wording about whose Claude Code subscription is
used made neutral; sample people and addresses in the prototype copy
replaced with fictional ones on `example.com`; the test fixture image
replaced with a self-made one and removed from history; MIT licence;
root README.

Consequences for later phases:
- F4: agents react only to the repo owner (label-triggered, plus an
  explicit owner check in the workflow); text in issues from anyone else
  is data, never instructions; the agent API key has a monthly cap.
- F5: production error reports must not copy user data into public
  issues — details stay in the error tracker, the issue gets a sanitized
  summary.
- Fork pull requests never get secrets and are never deployed to staging.
- Friends' feedback goes to the developer directly, who files the issue.

## 2026-11-12 — Branch protection settings on `main` (builder's call)

Enabled right after the repo went public. Pull request required; required
checks `backend` and `frontend`; branch must be up to date with `main`;
no approval count (one developer); rules apply to the owner as well
(`enforce_admins`), so "nothing pushes to `main` directly" is enforced by
GitHub rather than by convention — this also covers agents acting with the
owner's credentials. Cost: an urgent fix still has to go through a PR and
wait about two minutes for CI; the escape hatch is turning protection off
in Settings → Branches.

## 2026-11-13 — Explain-back checkpoints postponed until the MVP is usable

**Developer's decision**, overriding the builder. The developer does not
want to spend time on the explain-back questions now and wants to focus on
reaching a usable MVP.

- Builder's position: the developer's own anti-goal is "I must understand
  what was built"; the checkpoint per phase is how that is checked, and
  debt of this kind grows quietly — each new phase adds a layer on top of
  one that was never explained back.
- Developer's position: these are side topics right now; getting to the
  MVP comes first.
- Outcome: phases are closed when built and verified. Skipped checkpoints
  are listed under "Understanding debt" in `.claude/ROADMAP.md`.
  `docs/ARCHITECTURE.md` keeps being updated with every infra change, in
  plain language. The debt is paid back before F6 at the latest. The rule
  in `CLAUDE.md` is unchanged — this is a postponement, not a repeal.

## 2026-11-13 — MVP order: features before deploy; Ant Design for the frontend

**Developer's decisions.**
- Features first, deploy (F2) after. Builder had recommended deploy first
  (nothing is usable on a phone until the app is online, and the VPS and
  domain have lead time). Developer chose features first.
- MVP = the app is online (F2) and does what the spreadsheet does: manual
  body weight and daily steps with a dashboard, in the prototype's look.
  Staging, the agent loop, monitoring, CSV import, diet, comments,
  templates, Garmin and Xiaomi come after.
- Use a component library for the frontend, "for example antd".

**Builder's calls.**
- Ant Design 6 with `ConfigProvider` (dark algorithm + the prototype's
  tokens), `@ant-design/icons`, Manrope self-hosted via Fontsource. Charts
  stay hand-drawn SVG as in the prototype — no chart library. Cost: the JS
  bundle grew from about 277 kB to about 782 kB (253 kB gzipped).
- Forms keep native `<form>` submission and browser validation with Ant
  Design inputs inside, instead of Ant Design's `Form` — smallest change
  to existing logic.
- Workout create/edit moved into a dialog; delete confirmations use Ant
  Design's confirm dialog instead of `window.confirm` (old debt item).
- Weight and steps are owner-only for now, not visible through a share.
- Work is tracked as GitHub issues (#4 look, #5 weight, #6 steps), one PR
  each — the same path the agents will take later.

## 2026-11-13 — Body weight (issue #5): builder's calls

- `PUT /api/weights/{date}` is the only write: it creates or replaces the
  day's entry, so there is no separate create/update and no id in the API.
- "Date not in the future" is checked against UTC with one day of slack
  (latest allowed = tomorrow UTC). A strict "not after today UTC" would
  reject a user in Estonia logging at 00:30 local time. Cost: someone can
  log one day ahead.
- The server owns the date rule through an injected `Clock`; the frontend
  also limits the date picker to today.
- The dialog offers "Delete entry" when the chosen day already has one —
  not in the issue's spec, added because a mistyped date would otherwise
  be impossible to remove from the UI.
- Chart is hand-drawn SVG with points placed by date, so gaps between
  readings show as gaps in time, not as evenly spaced points.
- `vite.config.ts` reads `API_TARGET` (default `http://localhost:8080`) so
  a second backend can be checked without touching the developer's
  running one.

## 2026-11-13 — Daily steps (issue #6): builder's calls

- Same shape as weight on purpose: one entry per day, `PUT /{date}`
  creates or replaces, owner only.
- The "not in the future" rule moved out of `WeightService` into
  `common/EntryDatePolicy`, used by both services — one rule, one place.
- `StepsRequest.steps` is a `BigDecimal` with `@Digits(fraction = 0)`
  rather than an `Integer`: with an `Integer`, the JSON parser silently
  turned `9870.5` into `9870`. Found by calling the real API, pinned by a
  test.
- "This week" / "last week" averages are calendar weeks starting on
  Monday; days without an entry are left out of the average and get no
  bar. The prototype used rolling 7-day windows under the same labels.
- The 2-week / 8-week toggle only changes what is drawn; the card always
  loads 8 weeks.
- One shared `DayEntryDialog` replaces the weight-only dialog.
- The sidebar now stays in place while the page scrolls (the Dashboard is
  the first page taller than the screen).

## 2026-11-13 — F2 tasks 1–3: builder's calls

- Sign-up allowlist comes from one env var and fails closed: unset means
  nobody. The policy runs before the duplicate-email check so a refusal
  does not reveal whether an address is registered. Matching ignores case
  and spaces.
- Caddy forwards only `/api/*` and `/actuator/health`; any other path is
  the web app. So even if another actuator endpoint were ever switched on
  in the app, it would not be reachable from outside.
- The server's `.env` holds eight values. The datasource URL and user,
  the media path and `SPRING_JPA_SHOW_SQL=false` are fixed in
  `infra/compose.yml`, and the database password is passed to the api
  from the same variable Postgres uses — one secret, one place. (The plan
  first listed them as separate variables; the plan text was corrected.)
- The api container gets an explicit list of variables rather than the
  whole env file.
- `jar { enabled = false }`: `build/libs` holds exactly one jar, so the
  Dockerfile's `COPY build/libs/*.jar` cannot pick the wrong one.
- Images are built from already-built artefacts (Gradle and yarn run in
  CI, not inside Docker) — faster, and the same jar that passed the tests
  is the one that ships.
- No cache headers were added in Caddy yet; a stale `index.html` after a
  deploy is possible in theory. Left out as not yet a problem.

## 2026-11-13 — F2 server scripts written before the server: builder's calls

- One repository variable, `DEPLOY_ENABLED`, gates the `images` and
  `deploy` jobs and the backup check. Not in the plan. Reason: without it,
  merging the workflow before a server exists would turn every run on
  `main` red and mail a failed backup check daily. It doubles as the
  go-live switch (the developer sets it) and the stop switch.
- Script tests with stand-ins for `docker`, `curl`, `restic`
  (`infra/test/`), run by a new CI job `infra` together with `shellcheck`.
  Not in the plan. Reason: the rollback path would otherwise be exercised
  for the first time on prod. The job is not a required check yet —
  changing branch protection is the developer's call.
- `deploy.sh` pulls the new images before it changes anything, so a failed
  pull (missing tag, private package) leaves the server exactly as it was.
- Files reach the server through `tar` over SSH, not `scp`: tar replaces
  each file instead of writing into it, so a backup that is running while
  a deploy arrives keeps its own copy of the script.
- `backup.env` is read line by line, not executed as shell, so a secret
  containing `$` or `=` is taken literally.
- `backup.sh` and `restore.sh` only require `RESTIC_REPOSITORY` and
  `RESTIC_PASSWORD`; whatever else is in `backup.env` is passed on. Which
  storage variables to use (Backblaze's own interface or its
  S3-compatible one) is decided when the bucket exists — to be checked
  against restic's documentation then, not assumed now.
- `restore.sh` insists on an absolute, empty target directory and puts
  the throwaway database on an internal Docker network with no published
  port.
- Kept from the plan although weaker than it could be: the deploy job
  learns the server's SSH host key with `ssh-keyscan` on every run. A
  pinned key would need one more secret; left as a known gap in
  `docs/ARCHITECTURE.md`.
- `shellcheck` was installed on the laptop with Homebrew (named in the
  plan).

## 2026-11-13 — Review of the server scripts: what changed (builder's calls)

An independent reviewer read the scripts and workflows before they were
pushed. Eight findings, all accepted:

- `docker compose up` failing (not just an unhealthy app) now also rolls
  back.
- The backend log of a failed deploy is saved on the server, not printed:
  the workflow log is public and the app log can contain e-mail addresses.
- The new `compose.yml` arrives in `incoming/`; `deploy.sh` swaps it in
  after the dump and puts the previous one back on a failed pull or a
  rollback.
- The deploy job runs `deploy.sh` with its output in a file on the server,
  so a cancelled run or a lost connection cannot stop it before the
  rollback.
- CI's concurrency group now includes the event name, and a manual
  redeploy of an existing tag skips the three checks — a rollback by hand
  no longer waits behind other runs or tests.
- A missing `IMAGE_TAG` in `.env` gives a message instead of a silent exit.
- The restore drill removes its throwaway database volume.
- The backup service sets `HOME=/root` so restic can keep its cache.

## 2026-11-16 — Server: Hetzner CPX22, over budget (developer's decision)

- **Decided by the developer:** keep the Hetzner CPX22 (2 vCPU, 4 GB,
  80 GB, shared x86, Helsinki, Ubuntu 26.04) at about €21–22 a month with
  VAT. The developer called the extra cost tuition and wants headroom to
  run more apps on subdomains later. A domain was bought for a year from
  an Estonian registrar.
- **Effect:** the server alone is over the €20/month project budget,
  before the domain and OpenRouter. The developer loaded €25 of credit,
  which covers about the first month.
- **Builder's position (overridden):** the measured footprint of the whole
  stack is about 565 MB (backend 500 MB, Postgres 44 MB, Caddy 20 MB;
  empty database, one user, about 2,400 requests on the laptop), so a
  2 GB machine at about €13 (Hetzner CPX12, or a DigitalOcean 2 GB
  droplet) would carry the tracker alone and fit the budget.
- **Builder's mistake, recorded:** the plan's "about €6–8" came from the
  old CX22 price and was not re-checked at buying time; a "starting from"
  price was then read as the 4 GB price. Real prices on 2026-11-16, excl.
  VAT: CX23 €5.99 (not available), CPX12 €9.99, CPX22 €16.99. Prices are
  checked on the provider's own plan table before any figure is quoted.
- **Revisit:** when the credit runs out, or when CX23 is back on sale.
  Moving is a new server + `provision.sh` + restore from backup; Hetzner
  cannot scale a server down to a smaller disk.
- **Builder's calls:** Ubuntu 26.04 is accepted (Docker publishes packages
  for it; every package `provision.sh` installs exists there — checked in
  a container, not on the server). Backups will use Backblaze through its
  S3-compatible interface, as restic's documentation recommends, instead
  of the native B2 one named in the plan. More apps on the same box are
  not built for now; when a second app exists, Caddy moves out of the
  tracker's compose project into a shared front proxy.

## 2026-11-16 — Automatic review loop for factory PRs

Design: `docs/superpowers/specs/2026-11-16-review-loop-design.md`
(approved by the developer 2026-11-16).

- **Decided by the developer:** a review agent runs automatically on every
  factory PR and reports only major and critical findings. 6 or more
  critical findings → scrap at once (close PR, delete branch, analyse the
  cause, correct the spec, rebuild from zero). At most 3 review rounds; any
  major or critical finding after round 3 → scrap the same way. The
  rebuild starts by itself, without a new `approved` label (option B).
  Built only on GitHub issues, PRs, labels and Actions. Limits are enforced
  by a tested script, not by an LLM (approach 1 of 3).
- **Builder's calls:** one automatic rebuild per issue, then
  `needs-human`; red CI counts as a failed round; a written severity
  rubric; the analysis agent stops instead of rebuilding when the cause is
  a wrong review; the issue body counts as spec v1 when there is no spec;
  removing `factory` takes a PR out of the loop; `approved` after
  `needs-human` restarts with counters reset; non-factory PRs get one
  report-only review per push; stop switch `FACTORY_ENABLED`.

## 2026-11-17 — CSV import: scope and how it is built (issue #18)

- **Decided by the developer** (asked 2026-11-17, spec approved the same
  day): the import brings in workouts, body weight and steps (not
  aerobic, not diet); CSV only; the user gives each workout its date by
  hand on the review screen; the import will be used repeatedly, so it
  must recognise what is already in the app.
- **Builder's recommendations the developer did not take:** workouts
  only (developer added weight and steps); dates worked out from a start
  date and weekdays (developer chose by hand); a one-off migration
  (developer chose repeated use, which is why the fingerprint exists).
- **Builder's calls, listed in the issue for the developer to object to:**
  1. A weight or steps value that differs from the stored one starts
     unticked — the app's value wins unless the user ticks the row.
  2. "Already imported" is decided by a workout's content (exercise
     names, weights, reps), not by its date or its place in the file, and
     identical workouts are matched by count. A workout corrected in the
     spreadsheet therefore shows up as new.
  3. For weight and steps the model only names the columns and the date
     order; Java reads the rows. The model never retypes a number.
  4. 20 previews per user per day, counted in memory (resets on restart).
  5. One retry when the model's answer does not fit, then refuse. The
     pre-pivot design allowed two retries; one keeps the cost of a bad
     file at two calls.
  6. No new dependency: the CSV parser is about 100 lines of our own
     code. Files saved by Estonian Excel are handled (semicolons, decimal
     commas, windows-1257).
  7. All imported workouts are stored as type WEIGHTS, and the file's
     label ("Nädal 2 / Treening 1") goes into the workout's notes.
- **Not decided yet, the developer's:** the OpenRouter spending limit and
  which model to use. No real key was available during the build, so the
  prompts are untuned and the cost per import is unmeasured.

## 2026-11-17 — CSV import: what the review changed (builder's calls)

An independent reviewer read the branch before the PR. Nothing critical,
no path to another user's data. Changed as a result:

- Java no longer trusts the model's date order: the order under which
  most rows are real dates wins; a tie goes to the order that keeps the
  dates closest together, then to the model's.
- The delimiter is chosen by which one gives rows of equal width (tab,
  then semicolon, then comma on a tie), not by counting characters.
- A data row with more filled cells than the header is refused instead of
  being read as a shorter number. Years outside 1990–2100 are not dates.
- A quote only opens a quoted cell at the start of the cell.
- Preview no longer runs inside a database transaction.
- In the fingerprint a missing weight counts as 0 and repeated spaces in
  a name are ignored, so small differences in the model's reading do not
  turn an old workout into a new one. (Reverses a rule from earlier the
  same day.)
- The page refuses a workout date in the future instead of quietly
  storing "now".
- A cut-off answer is reported as "split the file" (413), not "try
  again". The upstream error text is no longer logged, only its status.
- OpenRouter timeout raised from 60 s to 180 s (`OPENROUTER_TIMEOUT`).
- The PR is opened as a draft: the spec says prompts are tuned and the
  cost measured on real files before merge, and neither has happened.
  Merging anyway is the developer's call.
- Deferred, listed as known gaps in `docs/ARCHITECTURE.md`: confirm is
  not idempotent for workouts; no content-type check; one kind per file;
  encodings other than UTF-8 and windows-1257; storage limits not in the
  prompt; `provider.require_parameters` and other real-API differences
  (for the tuning step with a real key).

## 2026-11-18 — CSV import: the model names the layout, code reads the cells (builder's call)

Changes builder's call from the spec of issue #18 (the model extracting
the workouts as JSON). The developer's four decisions (workouts + weight
+ steps; CSV only; a date typed for every workout; repeated imports) are
untouched.

Measured with the developer's OpenRouter key on one real training plan
(`.claude/private/import-samples/`, never committed), model
`anthropic/claude-haiku-5.5`:

| Approach | File | Runs | Exact | Time | Answer tokens |
|---|---|---|---|---|---|
| model copies workouts out | real, 3 of 10 weeks | 7 | 7 | 27–41 s | 8–12 k |
| same, thinking off | real | 1 | 0 (52 wrong values) | 14 s | 4.5 k |
| same, thinking low / medium | real | 2 | 0 (2 and 1 wrong) | 30 s | 9.5 k |
| model copies workouts out | filled to 10 weeks | 5 | 2 (2 silently wrong, 1 rejected by the app's checks) | 92–113 s | 18–34 k |
| model names the layout | real | 8 | 8, one identical layout | 5–6 s | 1.2–1.6 k |
| model names the layout | filled to 10 weeks | 6 | 6, one identical layout | 5–7 s | 1.1–1.3 k |

The errors of the first approach on the large file were silent: a wrong
weight, an exercise of another workout placed into a workout, an
exercise name reworded. A review page with 30 workouts would
not catch those.

Decided:

- The model answers with `GridLayout` only; `GridReader` reads the cells.
  This is the same division of work the weight and steps tables already
  had.
- Model: Claude Haiku 5.5. About 0.002 USD per import. A larger model was
  not tried: with the model no longer copying numbers there is nothing
  left for it to improve that was measured as wrong.
- The model's thinking is left at the provider default and not set in the
  request; the request still sends `temperature: 0`, which this model
  ignores. `provider.require_parameters` is not used: with it OpenRouter
  finds no endpoint, because of the temperature field.
- An exercise entry whose repetitions or weight are not plain numbers is
  skipped and counted, never guessed. More than 12 sets counts as
  unreadable. A link that is not http(s) is dropped.
- `notes` removed from the preview: nothing fills it any more.
- OpenRouter timeout back to 60 s (was raised to 180 s for long answers
  that no longer exist).
- What it costs if wrong: a plan with a different layout gives "nothing
  to import" instead of a best-effort reading. Only one template has been
  seen. If another layout turns up, it gets its own layout question; the
  free-form extraction is not kept as a fallback, because its errors are
  silent.
- Spent on the measurements: about 0.2 USD of the developer's key,
  estimated from the token counts; the OpenRouter dashboard has the exact
  figure.

## 2026-11-18 — CSV import rework: what the second review changed (builder's calls)

A fresh reviewer read the rework. Nothing critical. Changed as a result:

- A model answer with a missing or null field is sent back for the one
  retry instead of crashing with a 500.
- The layout is checked against the sheet before it is used (columns
  named once; week blocks alike and left to right; week and workout
  titles must be real cells in the right place; no exercise-looking row
  outside every workout). Strict on purpose: a refused sheet is visible,
  a half-read one is not. Cost if wrong: a legitimate but irregular
  sheet is refused until the check is loosened.
- Skipped exercise entries are returned and listed (row, exercise,
  workout), not only counted. A row with repetitions but no name is
  listed too.
- Cells longer than 60 characters are not matched against the number
  patterns (a 4 kB cell overflowed the stack).
- More than 200 workouts, or a workout with more than 12 exercises, is a
  413 with a message that says which — not a 502 "try again".
- A cut-off model answer is a model failure (502), no longer "split the
  file": a layout answer is small, so a cut-off is the model overrunning.
- Labels may be 100 characters each (was 60).
- Not changed: skip-not-guess stays, including for a weight written as
  words; number coercion of `1.7` to `1` in the model's answer; `1 000`
  read as two sets. Listed as known gaps where a user could meet them.

## 2026-11-18 — CSV import: the real weight and steps file has the days across the columns (builder's call)

The spec assumed one row per date. The developer's real file (a habit
tracker, half a year) is week blocks with the dates in a row and no
year. The one-row-per-date reader stays; a third kind was added for
this shape.

- The model names three row labels in the first call; there is no second
  call. Java finds the blocks and reads the cells. Measured on the real
  file: 157 weights and 175 steps, all equal to a separately computed
  answer; the model's answer was the same in 5 of 5 calls.
- A week whose days do not follow each other column by column is refused
  whole. This is also how day-before-month is settled without trusting
  the model, and what catches a date exported as a number.
- The year: user's choice, else exactly one year in the file name, else
  the latest year in which the last filled day is not in the future.
  Always shown on the page with a way to change it. Cost if wrong: every
  date one year off — visible on the review page, not silent.
- Diet, sleep, aerobic minutes and the workout-number row are not
  imported. Out of scope of issue #18 (roadmap: "Later for import").
- Left for the developer: body weight is stored with one decimal
  (`NUMERIC(4,1)`, manual entry too); the real sheet has two (74.45).
  Import rounds to 74.5. Storing two decimals is a schema change on
  prod, so it is the developer's decision.

## 2026-11-18 — Days-across reader: what its review changed (builder's calls)

A fresh reviewer read the first version. One critical finding and three
important ones, all about dates landing a year or months off without
any sign. The year and week-order logic was rewritten:

- "The year of the file" is the calendar year most of its days fall in,
  not the year of its first date. A 2025 sheet that begins on 30
  December reads as 2025. (Was: the given year went onto the first date,
  so such a sheet landed in 2026 while the page said 2025.)
- A date-shaped cell that is not a real date makes its whole week
  unreadable, so a week can no longer be half-parsed under the wrong
  date order and win.
- Weeks must follow each other: a week that repeats an earlier one, or
  starts more than 62 days after the previous one, is refused and its
  values counted. This replaces "the same date twice: the last one
  wins" for this kind of sheet. Cost if wrong: a sheet with a real hole
  of two months is refused after the hole.
- Weeks listed newest first are detected and read in their real order.
- The year in the file name is only a hint: it is not used when it would
  put filled days in the future.
- Only a readable value counts as "the last filled day" when the year is
  guessed; a dash or a zero does not.
- A week ends at a row with an empty label, so a summary row further
  down cannot attach to it. Labels match without regard to case.
- An out-of-range year answers 400 with its own message.
- Not changed: the labels the model gives are not checked against the
  sheet with a retry (a wrong label gives "nothing to import", not wrong
  data); every change of year is a new model call.

## 2026-11-18 — Body weight stays at one decimal (developer's decision)

Asked after the real habit tracker turned out to hold two decimals
(74.45). The developer's answer: one decimal stays. Import rounds half
up (74.45 becomes 74.5), the same precision manual entry accepts; no
schema change. The review page shows the rounded value before the user
confirms.

## 2026-11-19 — Agents run on the Claude Code subscription: plan A (developer's decision)

The developer checked and reported that the Claude Code subscription
allows runs in GitHub Actions. F4 is built on plan A; plan B
(OpenRouter) stays the fallback, switchable by configuration as the
design says. Not verified by the builder: no unattended run has been
made yet; the first F4 step is to prove one.

## 2026-11-19 — Order of work: F3 before the restore drill (developer's decision)

Offered: a restore drill with real data first, then F3. The developer
chose to start F3. The drill stays on the backlog; until it is done,
the backup of real data has been written but never read back.

## 2026-11-19 — F3 staging: data and trigger (developer's decisions), spec in issue #22

- Data: staging has its own database, empty at first, kept between
  deploys, wiped by a manual action. Production data never goes there.
  (Builder's suggestion, accepted. The alternative offered — a fresh copy
  of production at every deploy — tests migrations best but runs
  unreviewed code on real data.)
- Trigger: every PR from this repository goes to staging automatically
  after green checks; the latest wins. **The builder suggested a
  `staging` label instead**, so that it is always explicit which PR
  holds the one slot and docs-only PRs do not deploy; the developer
  chose automatic, one step less from the phone. What follows from it:
  the PR that loses the slot is told in a comment, and two PRs that
  change the database differently can break each other on staging
  (answer: the manual reset).

## 2026-11-19 — F3 staging: how it is built (builder's calls), spec in issue #22

- One proxy for both addresses; the copies share one Docker network
  between their `web` containers and nothing else. A second proxy cannot
  get a certificate without the same two ports.
- Staging runs production's compose file under another project name with
  an override file. The project name and file list are set in the
  environment by `staging.sh`, not left to the staging `.env`: with only
  `compose.yml` in play the project would be `tracker`, production.
  Checked on the laptop that the environment variable wins over the
  `name:` in the file.
- **Changed from the spec:** the spec said CI logs in to the server from
  the pull request's run with a key that can only deploy to staging.
  Built instead: the deploy runs in a separate workflow started by the
  finished CI run. That workflow is always the copy on `main`, and the
  key sits in an environment usable from `main` only, so a pull request
  cannot read the key at all, rather than reading a key that can do
  little. The key is still restricted on the server. Cost: the workflow
  cannot be tried before it is merged.
- Staging uses the compose files and `deploy.sh` that production runs
  (copied at every staging deploy), never ones from the pull request. A
  compose file from a pull request could mount the host. Cost: compose
  changes are not tested on staging.
- The pull request comment carries the result only. Logs stay on the
  server: the repository is public.
- The staging address is not written into repository files; the comment
  takes it from the repository variable `STAGING_URL` when that is set.
- A week of unused images is pruned as before, by either copy's deploy.

## 2026-11-19 — F3 staging: what the review changed (builder's calls)

A fresh reviewer read the first version. Nothing critical, three
important findings. Changed:

- In the staging directory the compose files are stored under other
  names and the staging `.env` names the project and the files. Before,
  a compose command typed by hand there would have acted on production,
  up to deleting its data.
- The Staging workflow queues per job, not per workflow: every finished
  CI run used to enter the queue, and a green pull request waiting in it
  could be thrown out by an unrelated run. On the server a lock turns a
  second request away while one is running.
- Provisioning refuses a staging address that is production's own, or
  not a plain host name; it would have stopped production's proxy at its
  next restart, far from the cause. It also refuses a key that is
  already listed with other rights, and keeps the last line of
  production's `.env` whole when that file has no final newline.
- The staging proxy's own "staging" block points at a closed port, so a
  stray request cannot loop inside it.
- Over SSH `staging.sh` fixes its directories and `PATH` itself and does
  not take them from the caller.
- The comment tells apart: did not start and went back; images could not
  be fetched; server busy; not running at all.
- Not changed: images are still deployed by tag, not by content hash;
  the host key is fetched anew on every run. Both as in production,
  both listed as known gaps.

## 2026-11-23 — F3 staging on the server: two small calls

- The pull request comment does not carry the staging address. The
  builder offered the choice; the developer went ahead without asking
  for the link, so it stays out (the repository is public). One
  repository variable, `STAGING_URL`, turns it on.
- The staging list of invited addresses is a copy of production's at the
  time of provisioning (the developer's own address). It is not kept in
  step afterwards; it is a line in `/opt/tracker-staging/.env`.

## 2026-11-23 — F4: runtime, identity, order, models (developer's decisions)

- Agents run on the developer's Claude subscription in GitHub Actions
  (plan A). The developer reported on 2026-11-19 that the subscription
  allows it. What the builder found in the documentation on 2026-11-23:
  the setup is described there (`claude setup-token`, "uses your Claude
  subscription instead of API billing"); the legal page speaks of
  "ordinary, individual usage" and says nothing specific about
  unattended runs. Told to the developer before approval of issue #28;
  the developer approved.
- Identity for agents that act on GitHub: Claude's official GitHub App.
  **The builder suggested a small own app** with only code, issue and
  pull request rights, because the official app may also change
  workflow files, so an agent's pull request could alter CI or the
  deploy. The developer chose the official app. What the builder does
  about it: agents that only produce content get no GitHub token at all
  (the review agent is the first); the app is used only where an agent
  must push, from slice 2 on, and that slice's spec has to say what
  keeps workflow files safe.
- Order: the review agent before the implementation agent.
- Models: the most capable model for review, spec and analysis; Sonnet
  for implementation and fixes. One repository variable per agent.

## 2026-11-23 — F4 slice 1, review agent: how it is built (builder's calls), spec in issue #28

- Headless Claude Code (`claude -p`) in a workflow step, not the
  ready-made action: one command with a schema for the answer is easier
  to follow than an action with some forty settings, and its extras
  (mention bot, own branches, own posting) are not wanted.
- Restricted mode with read and search tools only; settings, hooks and
  tool servers from the pull request are not loaded; the working folder
  is a neutral one and the pull request's code is an added folder.
- The Claude program's version is pinned in the workflow (2.1.325, the
  one rehearsed). Upgrading is a pull request.
- Rule ids added to the approved rubric so the answer can be checked by
  a script; the wording of the rules is unchanged.
- Not reviewed, to save allowance: documentation-only pull requests.
  Not reviewed, for safety: forks and pull requests not opened by the
  owner; an issue not opened by the owner is not used as the spec.
- The comment shows the tool's own cost estimate so that the developer
  can see how heavy a run was.

## 2026-11-23 — F4 slice 1, review agent: what its own review changed (builder's calls)

A fresh reviewer read the first version. Nothing critical; seven
important findings. Changed:

- The answer carries `reviewed`; false, or an answer after a single
  turn (nothing was read), is never a clean review.
- Symbolic links are deleted from the code before the reviewer starts;
  the reviewer's program gets an emptied environment; an answer that
  contains the Claude token is discarded as a failed review. None of
  this relies on how the program treats links, which is not documented.
- A missing issue is "no spec", not a failure. A file list or diff that
  cannot be fetched is a comment "did not run", not a silent red run.
- "Documentation only" is a short named list. Everything else — the
  rule book `CLAUDE.md`, the reviewer's prompt and rubric, settings,
  scripts under `docs/` — is reviewed. Renames are judged by both names.
- The comment has a size cap; what does not fit is counted in words.
- A failed run prints a short reason made only of the program's own
  status words.
- The security rule of the rubric reads as examples again and names
  unchecked outside input; severity must match the rule.
- Reviewer text in the comment: links, `www.`, issue references, `$`
  and text-direction characters are neutralised too.
- Not changed: a new comment per reviewed push; the workflow's glue is
  untested shell; the issue body, not a later spec comment, is the spec.
  All three listed as known gaps.
- During that review the reviewing agent once started the real Claude
  program by mistake (a probe without the stand-in on the path): one
  request, refused as "model not found", no usage. Told to the
  developer.

## 2026-11-23 — Review agent drills: harmless markers, not the real token

Builder's call. The steering drill asked the reviewer to quote
`/etc/hostname` and planted a link to that file, instead of pointing at
the process's own environment where the Claude token is. Reason: the
drill shows the same thing (does the reviewer follow planted
instructions, does a planted link or settings file take effect) and a
failure would have printed a host name of a throw-away machine into a
public comment, not a token valid for a year. The token probe prints
only "readable" or "empty".

## 2026-11-23 — A token with a space or line break is refused before the reviewer starts

Builder's call, after the first run in GitHub Actions failed without a
readable reason. The script checks the token's shape (no whitespace, no
control characters) and names the problem; an API error's own words go
into the run's detail line, reduced to letters, digits, dots and
dashes, and only for API errors, so text written by the reviewer never
reaches the log that way.

## 2026-11-24 — F4 slice 2: identity confirmed, no commands for the agent (developer's decisions)

- Identity: Claude's official GitHub App, confirmed a second time. The
  builder asked again because of new facts: the app's token can only be
  had inside the ready-made action and is cancelled when the action
  ends, so scripts cannot act as the app; and Anthropic's documentation
  and the action's FAQ contradict each other on whether the app can
  change workflow files. The builder suggested a small own app with
  rights to code, issues and pull requests only, where GitHub itself
  refuses workflow changes and scripts do the pushing. The developer
  kept the official app.
- The implementation agent runs no commands: it reads and edits files,
  the tests run in the pull request's CI. The builder recommended this;
  the alternative was a shell inside Claude Code's sandbox, whose
  working on GitHub's machines is not documented.

## 2026-11-24 — F4 slice 2a: shape of the implementation agent (builder's calls)

- Slice 2 is built in three steps (2a issue → pull request, 2b fix
  rounds with the gate, 2c analysis and rebuild) instead of one. Each is
  small enough to drill and to explain, and 2a measures how good a
  first attempt written without compiling is before the loop is built.
- The agent runs inside `anthropics/claude-code-action`, pinned to a
  commit, and hands its work over through the action's API commit tool
  to a branch the workflow names. "Agents produce content, scripts act"
  bends in that one place: a script cannot make the commit because the
  app's token is gone when the action ends.
- A script opens the pull request first, with an empty commit, so that
  the app's commit arrives as a push to an open pull request and starts
  CI. A pull request opened by the workflow's own token starts nothing.
- A probe workflow settles the assumptions read from the action's
  source before anything is built on them. If the app can change
  workflow files, the builder stops and asks.

## 2026-11-24 — Implementation agent: limits at the tool, check after the commit (builder's calls)

- The agent's edit and write rules name the allowed places only
  (`src/`, `frontend/src/`, `http/`, `docs/`, `build.gradle`); nothing
  has a bare allow rule; `.git/` is denied outright. In "don't ask"
  mode that refuses everything else, on the machine and in the
  checkout. Probed in the real repository before building.
- The script that checks the branch afterwards stays, and reads the
  branch back from GitHub instead of trusting the checkout the agent
  worked in. The scripts are copied out of the checkout before the
  agent runs and are run from there, so that nothing the agent wrote is
  executed by a later step.
- The frontend's package files are not among the allowed places: the
  agent cannot run the command that keeps the lock file in step, and CI
  would fail on the mismatch. A change that needs a new frontend
  dependency is not for the factory yet.
- The spec is the issue's title and text; comments are not read, and
  only issues written by the owner are built. `FACTORY_AUTHORS` (who is
  reviewed) deliberately does not widen who is built.
- No separate plan document for this slice: the spec issue and the
  script tests carry it.

## 2026-11-24 — Implementation agent: narrower than the spec after the review (builder's calls)

- `build.gradle` is not an allowed place, although the spec (issue 36)
  listed it. The independent review showed that CI's `images` job runs
  the build file on a machine that then holds the right to publish
  images. Narrowing is the safe direction; widening again needs the
  jar built in a job without that right. Told to the developer.
- The agent has no delete operation. One tool fewer to limit; a change
  that must remove a file is not for the factory yet.
- Three jobs instead of one: the agent's job holds a read-only GitHub
  token, and the check runs on a fresh machine whatever happened to
  the agent's job.
- A label `building` marks a pull request the check has not judged.
- The pull request is opened and labelled through GitHub's API rather
  than `gh pr create`, whose behaviour outside a checkout was not
  verified and whose output had to be parsed.

