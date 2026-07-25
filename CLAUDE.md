# CLAUDE.md

Guide for Claude Code (and any agent) working in this repository. Keep it updated in
the same PR whenever a convention changes.

## Overview

Stateless **read** microservice (Java 21 / Spring Boot 4.1) that serves SMTR bus
**route shapes** from **BigQuery** (`rj-smtr.planejamento`) so that
`sppo-tracker-backend` can classify route adherence (`OUT_OF_ROUTE`) and detect
deviations. **Hexagonal architecture**, root package `com.fajtech.sppogtfs`.

> The full contract — data model, join rules, API, caching, config — is in
> **`README.md`**. It is the source of truth for this service; read it before
> changing the payload, the query or the index.

This service owns **geometry only**. It never classifies positions, runs the
corridor test, or stores GPS — that logic belongs to the backend.

## Commands

```bash
mvn --batch-mode test                  # unit tests (JUnit 5 + AssertJ)
mvn --batch-mode verify                # + package
mvn -Dtest=LineCodeTest test           # one class
java -jar target/sppo-gtfs-service-*.jar --spring.profiles.active=demo  # no BigQuery, no creds
docker compose up --build              # demo profile
```

Against real BigQuery: `GOOGLE_APPLICATION_CREDENTIALS` + `GCP_PROJECT_ID`, then
`mvn -B spring-boot:run`. Never bake credentials into the image or commit them —
`secrets/` and `*-sa.json` are gitignored.

## Architecture

Three layers, dependencies always pointing inwards:

- `domain/` — pure value objects and geometry, no Spring: `LineCode`, `Coordinates`,
  `RouteShape`, `WktLineString`, `PolylineEncoder`, `GeoMath`, `DouglasPeucker`.
- `application/` — use cases + ports (`port/in`, `port/out`) + the in-memory index
  (`GtfsIndex`, `GtfsIndexBuilder`, `GtfsQueryService`).
- `infrastructure/` — REST adapters (`adapter/in/rest`), BigQuery loader
  (`adapter/out/persistence`), `config/` (`@ConfigurationProperties`),
  `observability/`.

## Invariants (do not break)

- **`lineCode` normalization is a shared contract** with `sppo-tracker-backend`
  (`LineCodeKey` there). trim + upper, exact match against `servico`, relaxed
  fallback without leading zeros. If the two sides diverge, the join fails
  **silently** — this is the #1 source of bugs in the pair.
- **No BigQuery query per request.** Reads hit the in-memory index; reloads
  (startup, daily cron, `POST /internal/reload`) swap the index reference
  **atomically**. Queries cost money — keep them few and bounded.
- **v1 is versioned:** breaking payload changes go to `/api/v2`.
- Errors as `application/problem+json`; never leak stack traces. UTC internally.
- `?simplify` is **off by default** — the backend's corridor test needs faithful
  geometry.

## Conventions

- **Commits:** Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`,
  `chore:`), messages in English. This repo is English throughout (code, javadoc,
  docs) — unlike `sppo-tracker-backend`, whose docs are in Portuguese.
- **Tests** live in the same package as the class under test (`*Test.java`), TDD:
  failing test first. The BigQuery adapter is thin and exercised against real
  BigQuery in a deployed environment — there is no local emulator.
- Test coverage gap to be aware of: only `LineShapesController` has a `@WebMvcTest`;
  the batch/shape/metadata/feed-version/internal endpoints are untested.
