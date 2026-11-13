# Activity Tracker

A personal fitness tracker — and an experiment in building software with an
**AI software factory**: issues and production errors go in, tested changes
come out, and a human steps in only at the approval points.

The first phases of the app (auth, activities, sharing, media) were written
by hand while learning Spring. Since November 2026 the application and its
infrastructure are written by AI agents; the developer owns the factory,
decides what gets built, and approves what ships.

## Status

Early. The app runs locally and every pull request is built and tested in
CI. Deployment, staging, the agent loop, and monitoring are not built yet.
The current state is always in [`.claude/ROADMAP.md`](.claude/ROADMAP.md).

## How the factory is meant to work

1. An issue is opened.
2. A triage agent labels it; a spec agent writes a short spec.
3. The developer approves the spec.
4. An implementation agent opens a pull request; CI and a review agent
   check it.
5. The change is deployed to staging; the developer tests and merges.
6. Merge deploys to production; production errors become new issues.

Only the owner can trigger agents. Nothing reaches production without a
human approval.

## Where to look

| | |
|---|---|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | What runs where today, and how a change flows |
| [`docs/superpowers/specs/`](docs/superpowers/specs/) | The design behind the factory |
| [`.claude/ROADMAP.md`](.claude/ROADMAP.md) | Phases and product backlog |
| [`.claude/DECISIONS.md`](.claude/DECISIONS.md) | Decision log with reasoning, including the hand-written phase |
| [`CLAUDE.md`](CLAUDE.md) | Operating instructions for the agents |
| [`docs/design/prototype/`](docs/design/prototype/) | Design prototype the product is built towards (sample data is fictional) |

## Stack

Spring Boot 4 (Java 26, Gradle) · PostgreSQL 17 + Liquibase · React 19 +
Vite + TypeScript + Ant Design · JWT auth · GitHub Actions.

## Running locally

Requires Docker, JDK 26, Node 22 and Yarn 1.x.

```bash
docker compose up -d
```

```bash
export JWT_SECRET="$(openssl rand -base64 32)"
export OPENROUTER_API_KEY=... OPENROUTER_BASE_URL=https://openrouter.ai/api/v1 OPENROUTER_MODEL=...
./gradlew bootRun
```

```bash
cd frontend && yarn install && yarn dev
```

Tests (need Docker for Testcontainers):

```bash
./gradlew test
```

## License

[MIT](LICENSE)
