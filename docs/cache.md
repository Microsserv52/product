---
icon: lucide/database-zap
---

# Cache policy

Caching is optional and disabled by default. The service is correct without
Redis; enabling the cache changes latency, not the API contract.

## Keys and TTL

| Cache | Key | Example | TTL |
| --- | --- | --- | --- |
| `product` | `product:product::{uuid}` | `product:product::0195abfb-...` | `CACHE_TTL_SECONDS`, default 60 s |
| `product-list` | `product:product-list::all` | `product:product-list::all` | `CACHE_TTL_SECONDS`, default 60 s |

`RedisCacheConfiguration.prefixCacheNameWith("product:")` supplies the outer
prefix; Spring Data Redis adds the cache name and key serialization. Keys are
low-cardinality and contain no user or tenant identity because catalogue reads
are public.

## Invalidation

- `POST /products` evicts every entry in both caches after the transaction.
- `DELETE /products/{id}` evicts every entry in both caches after the
  transaction.
- Every image mutation (`POST /products/{id}/image`, `PATCH` and `DELETE`)
  evicts both caches the same way, so catalogue reads never serve a stale
  `image`.
- TTL bounds staleness if an eviction is lost because Redis is unavailable.
- The cache writer is configured for immediate writes so eviction and clear
  operations are synchronous with the request.

## Redis unavailable

Cache operations never fail the request. `CacheErrorHandler` records the error
and the service falls back to PostgreSQL:

```text
product.cache.errors{cache="product",operation="get"}
product.cache.errors{cache="product",operation="put"}
product.cache.errors{cache="product",operation="evict"}
```

Readiness deliberately excludes Redis. A Redis outage therefore keeps the
service ready and increases database load.

## Measurements

Micrometer and Spring Cache expose hit, miss and latency data through
`/actuator/metrics` and `/actuator/prometheus`, including:

- `cache.gets` with `cache`, `result` (`hit`/`miss`) tags;
- `cache.puts`;
- `cache.evictions`;
- `product.cache.errors` for degraded operations.

Enable the cache only after comparing baseline database latency and confirming
the metrics in the target environment:

```bash
CACHE_ENABLED=true CACHE_TTL_SECONDS=60 docker compose up --detach --build
```

The integration tests cover hit, miss, invalidation and Redis-unavailable
behavior before a release enables the flag.

## Not cached

- Authenticated or per-user responses.
- Error responses.
- Idempotency records and write paths.
