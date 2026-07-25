# sppo-gtfs-service

Stateless **read** microservice that exposes SMTR (Rio de Janeiro) bus **route shapes**
(itinerary geometry) and light metadata, so that
[`sppo-tracker-backend`](https://github.com/fellipetoledo/sppo-tracker-backend) can classify
route adherence (`OUT_OF_ROUTE`) and detect deviations.

This service is the **source of truth for geometry**. It does **not** classify positions, run
the corridor test, or store GPS — that logic stays in the backend.

- **Data source:** Google **BigQuery**, dataset `rj-smtr.planejamento` (SMTR open data).
  This service only reads.
- **Consumer:** `sppo-tracker-backend`, resolving **by line**, off the hot path, with a
  TTL cache. QPS is low and highly cacheable — optimized for **compact payloads** and
  **cheap revalidation** (ETag/304), not raw throughput.

## Stack

- Java 21, Spring Boot 4.1, Maven
- Hexagonal architecture, root package `com.fajtech.sppogtfs`
  - `domain/` — pure value objects and geometry (no Spring): `LineCode`, `Coordinates`,
    `RouteShape`, `WktLineString`, encoded-polyline / haversine / Douglas–Peucker
  - `application/` — use cases + ports (`port/in`, `port/out`) + the in-memory index
  - `infrastructure/` — REST adapter, **BigQuery** loader adapter, config, observability
- Caffeine cache, Micrometer/Prometheus, `application/problem+json` errors, UTC internally.

## How it works

On startup (and on a daily cron, or via an admin endpoint) the service runs a few BigQuery
queries and builds an **in-memory index** `normalizedServico → [RouteShape]`, with encoded
polylines and bounding boxes precomputed. Reads hit this index (no per-request BigQuery
query — important for cost and latency); a Caffeine cache holds already-serialized bodies
keyed by ETag. Reloads swap the index reference **atomically** — no downtime.

### Data model & join (real SMTR schema)

The GPS feed sends only `linha` (e.g. `100`, `SV789`) — no shape id or direction. Resolution:

```
linha (GPS) ── normalize ──▶ viagem_planejada_dia.servico
                                   │  (modo = 'Ônibus')
                                   ├─▶ shape_id (regular)  ┐
                                   └─▶ trajetos_alternativos[].shape_id  ┘ distinct
                                                             │
                             shapes_geom.wkt_shape ◀── shape_id (WKT LINESTRING → geometry)
```

- **`rj-smtr.planejamento.viagem_planejada_dia`** — which `shape_id`s a `servico` runs, with
  `sentido`, `evento`, `modo`, plus `trajetos_alternativos` (variations). Partitioned by `data`;
  carries the `feed_version` in effect.
- **`rj-smtr.planejamento.shapes_geom`** — geometry per `shape_id` as a WKT `LINESTRING`
  (`wkt_shape`), versioned by `feed_start_date`/`feed_end_date`.

Rules:
- Shapes are **deduplicated by `shape_id`** (regular + alternates), points parsed from WKT.
- `sentido` (`"I"` ida / `"V"` volta) maps to a GTFS-style `directionId` (0/1); `evento` marks a
  non-regular / alternative trajectory (`null` = regular). Both are exposed in the payload.
- **SPPO filter:** only `modo = 'Ônibus'` (configurable via `sppo.gtfs.filter.modos`).
- **Feed selection:** the current feed is the `feed_version` of the most recent available `data`
  partition of `viagem_planejada_dia`; planned trips are scanned across a lookback window within
  that feed to capture all day-type variations (Dia Útil / Sábado / Domingo). Geometry is the
  most recent `wkt_shape` per `shape_id`, decoupling from feed-version skew between the tables.

## `lineCode` normalization — shared contract ⚠️

**This exact algorithm MUST be replicated in `sppo-tracker-backend`,** matched against `servico`.
If the two sides differ, the join fails silently — it is the #1 source of bugs.

1. `trim` + `toUpperCase`.
2. **Exact** match against `servico` normalized the same way.
3. **Relaxed fallback** (only if exact fails): if the code is purely numeric, compare **without
   leading zeros** (`0100 ≡ 100`).
4. No match → **unresolved** (`404` per-line, or listed in `unresolved` in the batch).

Implemented in [`LineCode`](src/main/java/com/fajtech/sppogtfs/domain/LineCode.java);
`gtfs_resolve_total{result=hit|relaxed|unresolved}` tracks which path was taken.

## API (v1)

Base path `/api/v1`. Default polyline format is **Google Encoded Polyline (precision 5)**;
`?format=geojson` returns GeoJSON.

| Method & path | Purpose |
|---|---|
| `GET /lines/{lineCode}/shapes?format=encoded\|geojson&simplify={m}` | All shapes of a line (critical endpoint). `ETag` + `If-None-Match` → `304`. `404` if the line doesn't exist; `200` with `shapes:[]` and `resolution:"no_shapes"` if it exists without geometry. |
| `POST /lines/shapes:batch` | Warmup: `{ "lines": ["100","SV789"], "simplify": 0 }` → `{ items, unresolved, feedVersion }` (encoded form). |
| `GET /shapes/{shapeId}` | Standalone shape geometry. |
| `GET /lines/{lineCode}` | Line metadata: available directions, shape count. |
| `GET /feed/version` | `{ id, publishedAt, source, counts: { lines, shapes } }` — poll to invalidate cache. |
| `POST /internal/reload` | Admin: force an atomic index rebuild (requires admin key). |
| `GET /actuator/health` / `GET /actuator/prometheus` | Liveness/readiness, metrics. |

### Example — `GET /api/v1/lines/100/shapes`

```json
{
  "line": "100",
  "feedVersion": { "id": "2026-07-18 13:00:00-03:00", "publishedAt": "2026-07-19T00:00:00Z", "source": "SMTR/BigQuery rj-smtr.planejamento" },
  "resolution": "exact",
  "shapes": [
    {
      "shapeId": "s100i", "directionId": 0, "sentido": "I",
      "encodedPolyline": "~swjC~ntfG...",
      "bbox": { "minLat": -22.92, "minLon": -43.20, "maxLat": -22.90, "maxLon": -43.19 },
      "pointCount": 742, "lengthMeters": 18234.5
    },
    { "shapeId": "s100i_alt", "directionId": 0, "sentido": "I", "evento": "Desvio", "encodedPolyline": "..." }
  ]
}
```

### Caching contract

- Every `LineItinerary` carries `feedVersion`; the backend uses `feedVersion.id` as part of its
  cache key, so a feed refresh invalidates caches naturally.
- `ETag` derives from `feedVersion.id` + `lineCode` (+ `format` / `simplify`). The backend
  revalidates cheaply with `If-None-Match` → `304`.

### Simplification

`?simplify={meters}` applies Douglas–Peucker. **Default off** (the backend corridor test uses the
faithful geometry). If used, keep the tolerance conservative (≤ 2 m) — it changes the adherence test.

## Configuration (prefix `sppo.gtfs`)

| Env var | Meaning | Default |
|---|---|---|
| `GOOGLE_APPLICATION_CREDENTIALS` | Path to a GCP service-account key (BigQuery read) | *(ADC)* |
| `GCP_PROJECT_ID` | Project used to run/bill the queries | *(ADC default)* |
| `GTFS_VIAGEM_TABLE` | Planned-trips table | `rj-smtr.planejamento.viagem_planejada_dia` |
| `GTFS_SHAPES_TABLE` | Shape-geometry table | `rj-smtr.planejamento.shapes_geom` |
| `BIGQUERY_LOCATION` | BigQuery processing location | `US` |
| `GTFS_PLANNED_LOOKBACK_DAYS` | Days scanned within the current feed (day-type variations) | `14` |
| `GTFS_RELOAD_STARTUP_ATTEMPTS` | Attempts for the startup index load (transient BigQuery failures) | `3` |
| `GTFS_RELOAD_STARTUP_BACKOFF` | Wait before the 2nd attempt, doubling per attempt | `10s` |
| `GTFS_MODOS` | `modo` values included (comma-separated) | `Ônibus` |
| `GTFS_API_KEY` | `X-Api-Key` required on `/api/**` (empty = public) | *(empty)* |
| `GTFS_ADMIN_KEY` | `X-Api-Key` required on `/internal/**` | *(empty → /internal closed)* |
| `GTFS_CORS_ORIGINS` | Comma-separated allowed origins for `/api/**` | *(none)* |

The service account needs BigQuery Data Viewer + Job User on the project/datasets. Credentials
come from the environment (mounted key / workload identity) — never baked into the image.

## Running

### Demo profile (no BigQuery, no credentials)

The `demo` profile uses an in-memory loader shaped like the real feed, so the full app runs
(index build + REST) with zero external dependencies. **Not for production.**

```bash
mvn -B package -DskipTests
java -jar target/sppo-gtfs-service-*.jar --spring.profiles.active=demo
curl http://localhost:8080/api/v1/lines/100/shapes
```

Or via Docker: `docker compose up --build` (compose defaults to the demo profile; see the
commented block in `docker-compose.yml` to point it at real BigQuery).

### Against real BigQuery

```bash
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/sa.json
export GCP_PROJECT_ID=your-billing-project
mvn -B spring-boot:run
```

## Testing

```bash
mvn -B test
```

- **Domain** (TDD units): `LineCode` normalization, WKT LINESTRING parsing, polyline encode/decode,
  bbox, haversine, Douglas–Peucker.
- **Application**: join scenarios — regular + alternate shapes, `sentido`→`directionId`, existing
  line with no geometry (`no_shapes`), leading-zero relaxed fallback, unknown line, `modo` filter.
- **REST** (`@WebMvcTest`), one per endpoint: contract, `ETag`/`304`, `problem+json`,
  `no_shapes`, GeoJSON, batch warmup, input validation, and the `X-Api-Key` gate
  (`/api/**` open when no key is set; `/internal/**` closed unless a key is configured).

> The BigQuery adapter is thin (query + row mapping) and is exercised against real BigQuery in a
> deployed environment; there is no local BigQuery emulator in the test suite.

## Integration checklist with `sppo-tracker-backend`

- [ ] `lineCode` normalization **identical** on both sides, matched against `servico`.
- [ ] Backend uses `feedVersion.id` in its cache key and revalidates via `ETag`/`If-None-Match`.
- [ ] Backend consumes encoded polyline + `bbox`; corridor (±15 m) and fallback (100 m) stay
      backend rules, not this service's.
- [ ] `unresolved` / `no_shapes` treated as "no itinerary" — never drop classification.
- [ ] v1 contract is versioned; breaking changes go to `/api/v2`.

## Roadmap

- **`segmento_shape` — decided, partially adopted (2026-07-25).** This table is SMTR's own
  *"segmented shapes used for trip validation"*, and it is where the corridor is **defined**:
  `buffer_completo` = "área de 20 m ao redor do segmento", plus per-segment exclusion flags
  (`indicador_tunel`, `indicador_segmento_desconsiderado`, `indicador_area_prejudicada`,
  `indicador_segmento_pequeno`). The backend takes the **20 m width** from here — its
  previous ±15 m had no source.
  - ⚠️ **The exclusion flags do not belong to the corridor test.** They mean "do not use this
    segment to judge whether the trip was performed" — `indicador_segmento_pequeno` (< 990 m)
    is the ~1-per-shape remainder of SMTR's ~1 km cut (1,397 small segments for 1,383 shapes,
    mean 981 m), and a tunnel segment is still route even where GPS is unreliable. Excluding
    them from the corridor would *create* false OUT_OF_ROUTE, at the end of nearly every line
    and inside every tunnel. They belong to per-segment trip validation — backend's
    `docs/regras-de-negocio.md` §11.
  - We serve the **segment lines + flags**, not the buffer geometry. Measured on the current
    feed: the *union* of treated buffers and of complete buffers have **identical area
    (100.00%)** — the treatment removes overlap *between neighbouring segments*, not route
    area — so for the boolean "is it on route?" a ≤ 20 m test against the segment line is
    equivalent. Cost per shape (WKT): whole shape 7.9 KB · segment lines 13.1 KB (1.65×) ·
    buffers 119.0 KB (15×), 160.8 MB in total.
  - ⚠️ The **treated `buffer` becomes mandatory** for per-segment trip validation (counting
    how many segments a trip covered), because attributing a point to *one* segment is
    exactly what the treatment disambiguates. Spec kept in the backend's
    `docs/regras-de-negocio.md` §11.
