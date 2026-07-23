# sppo-gtfs-service

Stateless **read** microservice that exposes GTFS **route shapes** (itinerary geometry) and
light route metadata for the SMTR (Rio de Janeiro) open-data feed, so that
[`sppo-tracker-backend`](https://github.com/fellipetoledo/sppo-tracker-backend) can classify
route adherence (`OUT_OF_ROUTE`) and detect deviations.

This service is the **source of truth for geometry**. It does **not** classify positions, run
the corridor test, or store GPS — that logic stays in the backend.

- **Data source:** a GCP Cloud SQL / Postgres database already populated with the SMTR GTFS
  (`routes`, `trips`, `shapes`, optionally `feed_info`). This service only reads.
- **Consumer:** `sppo-tracker-backend`, resolving **by line**, off the hot path, with a
  TTL cache. QPS is low and highly cacheable — the service optimizes for **compact payloads**
  and **cheap revalidation** (ETag/304), not raw throughput.

## Stack

- Java 21, Spring Boot 4.1, Maven
- Hexagonal architecture, root package `com.fajtech.sppogtfs`
  - `domain/` — pure value objects and geometry (no Spring)
  - `application/` — use cases + ports (`port/in`, `port/out`)
  - `infrastructure/` — REST adapter, JPA persistence adapter, config, observability
- Caffeine cache, Micrometer/Prometheus, Testcontainers, `application/problem+json` errors
- Times are UTC internally.

## How it works

On startup (and on a daily cron, or via an admin endpoint) the service scans
`routes` / `trips` / `shapes` once and builds an **in-memory index**
`normalizedLineCode → [RouteShape]`, with encoded polylines and bounding boxes precomputed.
Reads hit this index (no per-request DB query); a Caffeine cache holds already-serialized
bodies keyed by ETag. Reloads swap the index reference **atomically** — no downtime.

### GTFS join (central rule)

```
linha (GPS)  →  routes.route_short_name  →  trips.route_id  →  distinct trips.shape_id
             →  shapes (geometry)
```

- A line has **N shapes** (ida/volta + operational variations). Shapes are returned
  **deduplicated by `shape_id`**, points ordered by `shape_pt_sequence`.
- `directionId` / `headsign` come from a **representative trip** (the most frequent
  `(direction_id, headsign)` for that shape). Shapes are never multiplied per trip.
- **SPPO filter:** only `route_type = 3` (buses). BRT can be excluded by agency id or line
  prefix (`sppo.gtfs.filter.exclude-brt=true`).

## `lineCode` normalization — shared contract ⚠️

**This exact algorithm MUST be replicated in `sppo-tracker-backend`.** If the two sides differ,
the join fails silently — it is the #1 source of bugs.

1. `trim` + `toUpperCase`.
2. **Exact** match against `route_short_name` normalized the same way.
3. **Relaxed fallback** (only if exact fails): if the code is purely numeric, compare
   **without leading zeros** (`0100 ≡ 100`).
4. No match after both steps → **unresolved** (`404` on the per-line endpoint, or listed in
   `unresolved` in the batch). This is not a server error.

Implemented in [`LineCode`](src/main/java/com/fajtech/sppogtfs/domain/LineCode.java);
`gtfs_resolve_total{result=hit|relaxed|unresolved}` tracks which path was taken.

## API (v1)

Base path `/api/v1`. Default polyline format is **Google Encoded Polyline (precision 5)**;
`?format=geojson` returns GeoJSON (`LineString` / `FeatureCollection`).

| Method & path | Purpose |
|---|---|
| `GET /lines/{lineCode}/shapes?format=encoded\|geojson&simplify={m}` | All shapes of a line (critical endpoint). `ETag` + `If-None-Match` → `304`. `404` if the line doesn't exist; `200` with `shapes:[]` and `resolution:"no_shapes"` if it exists without shapes. |
| `POST /lines/shapes:batch` | Warmup: `{ "lines": ["100","SV789"], "simplify": 0 }` → `{ items, unresolved, feedVersion }` (encoded form). |
| `GET /shapes/{shapeId}` | Standalone shape geometry. |
| `GET /lines/{lineCode}` | Line metadata: long name, available directions, shape count. |
| `GET /feed/version` | `{ id, publishedAt, source, counts: { routes, shapes } }` — poll to invalidate cache. |
| `POST /internal/reload` | Admin: force an atomic index rebuild (requires admin key). |
| `GET /actuator/health` | Liveness/readiness. |
| `GET /actuator/prometheus` | Metrics. |

### Example — `GET /api/v1/lines/100/shapes`

```json
{
  "line": "100",
  "routeLongName": "Rodoviária ↔ Praça XV",
  "feedVersion": { "id": "2026-07", "publishedAt": "2026-07-01T00:00:00Z", "source": "SMTR/data.rio" },
  "resolution": "exact",
  "shapes": [
    {
      "shapeId": "100_0_1",
      "directionId": 0,
      "headsign": "Praça XV",
      "encodedPolyline": "}v~pC...",
      "bbox": { "minLat": -22.91, "minLon": -43.24, "maxLat": -22.82, "maxLon": -43.18 },
      "pointCount": 742,
      "lengthMeters": 18234.5
    }
  ]
}
```

### Caching contract

- Every `LineItinerary` carries `feedVersion`; the backend uses `feedVersion.id` as part of its
  cache key, so a monthly GTFS refresh invalidates caches naturally.
- `ETag` derives from `feedVersion.id` + `lineCode` (+ `format` / `simplify`). The backend
  revalidates cheaply with `If-None-Match` → `304`.

### Simplification

`?simplify={meters}` applies Douglas–Peucker. **Default is off** (the backend corridor test uses
the faithful geometry). If used, keep the tolerance conservative (≤ 2 m) — it changes the
adherence test.

## Configuration (prefix `sppo.gtfs`)

Env vars (see `application.yml`):

| Env var | Meaning | Default |
|---|---|---|
| `GTFS_DB_URL` | JDBC URL of the GCP Postgres | `jdbc:postgresql://localhost:5432/gtfs` |
| `GTFS_DB_USER` / `GTFS_DB_PASSWORD` | DB credentials | `gtfs` / `gtfs` |
| `GTFS_DB_POOL_SIZE` | Hikari max pool size | `5` |
| `GTFS_API_KEY` | `X-Api-Key` required on `/api/**` (empty = public) | *(empty)* |
| `GTFS_ADMIN_KEY` | `X-Api-Key` required on `/internal/**` | *(empty → /internal closed)* |
| `GTFS_CORS_ORIGINS` | Comma-separated allowed origins for `/api/**` | *(none)* |
| `GTFS_FEED_FALLBACK_ID` | Feed id when the DB has no `feed_info` row | current `yyyy-MM` |

Other keys: `sppo.gtfs.filter.route-types` (`[3]`), `filter.exclude-brt` (`true`),
`filter.brt-agency-ids`, `filter.brt-line-prefixes`, `reload.on-startup` (`true`),
`reload.cron` (`0 0 4 * * *`, UTC), `cache.max-size` (`5000`), `cache.ttl-minutes` (`120`).

### Connecting to Cloud SQL from Oracle Cloud

Two supported options — **choose one and set `GTFS_DB_URL` accordingly**:

1. **Public IP + mandatory SSL** (recommended for simplicity): point `GTFS_DB_URL` at the
   Cloud SQL public IP with `?sslmode=verify-full` and mount the server CA, e.g.
   `jdbc:postgresql://<PUBLIC_IP>:5432/gtfs?sslmode=verify-full&sslrootcert=/secrets/server-ca.pem`.
2. **Cloud SQL Auth Proxy as a sidecar**: run the proxy container next to the service and point
   `GTFS_DB_URL` at `jdbc:postgresql://localhost:5432/gtfs`.

Credentials come from environment variables / secrets — never baked into the image.

## Running

### Local (compose: service + seeded Postgres)

```bash
docker compose up --build
curl http://localhost:8080/api/v1/lines/100/shapes
curl http://localhost:8080/api/v1/feed/version
```

### Maven

```bash
mvn -B spring-boot:run          # needs a reachable GTFS Postgres (see env vars)
mvn -B test                     # unit + web-slice tests
mvn -B verify                   # also runs the Testcontainers persistence IT (needs Docker)
```

### Demo profile (no external DB)

For a quick local run without any database, the `demo` profile uses an in-memory H2 (in
PostgreSQL mode) seeded with the fictional sample in `src/main/resources/demo-gtfs.sql`.
It exercises the full app (index build + REST). **Not for production.**

```bash
java -jar target/sppo-gtfs-service-*.jar --spring.profiles.active=demo
curl http://localhost:8080/api/v1/lines/100/shapes
```

## Testing

- **Domain** (TDD units): `LineCode` normalization, polyline encode/decode, bbox, haversine,
  Douglas–Peucker.
- **Application**: join scenarios — multiple shapes, existing line with no shapes, leading-zero
  relaxed fallback, unknown line, BRT/non-bus filtering.
- **REST** (`@WebMvcTest`): endpoint contract, `ETag`/`304`, `problem+json`, `no_shapes`, GeoJSON.
- **Persistence** (`*IT`, Testcontainers Postgres): the JPA adapter over a mini GTFS fixture.
  Runs under `mvn verify` where a Docker daemon is available.

## Integration checklist with `sppo-tracker-backend`

- [ ] `lineCode` normalization **identical** on both sides (copy + test both).
- [ ] Backend uses `feedVersion.id` in its cache key and revalidates via `ETag`/`If-None-Match`.
- [ ] Backend consumes encoded polyline + `bbox`; corridor (±15 m) and fallback (100 m) stay
      backend rules, not this service's.
- [ ] `unresolved` / `no_shapes` treated as "no itinerary" — never drop classification.
- [ ] v1 contract is versioned; breaking changes go to `/api/v2`.
