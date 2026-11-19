---
icon: lucide/activity
---

# Observability

## Health endpoints

| Endpoint | Purpose | Failure behavior |
| --- | --- | --- |
| `GET /actuator/health/liveness` | Process liveness. | `503` if the application is shutting down or broken. |
| `GET /actuator/health/readiness` | Traffic readiness, including PostgreSQL. | `503` when the database is unavailable. |
| `GET /actuator/health` | Overall health and component details. | `503` when any critical component is down. |
| `GET /actuator/info` | Build and application information. | `200`. |
| `GET /actuator/prometheus` | Prometheus scrape endpoint. | `200`. |

Redis is excluded from readiness because it is an optional cache. Set
`HEALTH_DETAILS=always` only in trusted local environments; the production
default is `never`.

## Metrics

Micrometer exposes JVM, HTTP, Hikari, cache and application metrics. Important
series:

| Metric | Meaning |
| --- | --- |
| `http.server.requests` | Request count, latency and status by URI and method. |
| `hikaricp.connections.*` | Pool usage, pending acquisitions and timeouts. |
| `cache.gets` | Cache hit/miss by cache name. |
| `cache.evictions` | Evictions after writes. |
| `product.cache.errors` | Cache operation failures by cache and operation. |
| `product.created` | Successful product creations. |
| `product.deleted` | Successful product deletions. |

Labels are intentionally low cardinality. Do not add product IDs, user IDs or
request IDs as metric labels.

## Logs

The base profile logs a human-readable line. The `prod` profile uses ECS
structured console output with:

- timestamp in UTC;
- level and logger;
- `requestId` from `X-Request-Id` when supplied, otherwise a generated UUID;
- the configured root level from `LOG_LEVEL`.

`X-Request-Id` is returned on every response so clients and traces can correlate
requests. Secrets, JWTs and database credentials are never logged.

## Suggested alerts

| Alert | Condition |
| --- | --- |
| Readiness failing | `up == 0` or readiness `503` for more than two scrape intervals. |
| Database saturation | Hikari pending acquisitions or timeouts sustained. |
| Elevated 5xx | `http.server.requests{status=~"5.."}` rate above baseline. |
| Cache degradation | `product.cache.errors` increasing while `CACHE_ENABLED=true`. |
| Latency regression | p95 `http.server.requests` above the agreed catalogue SLO. |
