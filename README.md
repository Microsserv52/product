# Product

Product catalogue microservice for the [Microsserv52 platform](https://github.com/Microsserv52/platform).
It owns product identity, display name, price and sale unit, and it is called by
Order over HTTP when an order is created. The service is implemented with Java
25, Maven and Spring Boot, persists to PostgreSQL and can use a Redis-compatible
read cache.

[![Product CI](https://github.com/Microsserv52/product/actions/workflows/ci.yml/badge.svg)](https://github.com/Microsserv52/product/actions/workflows/ci.yml)
[![Documentation](https://github.com/Microsserv52/product/actions/workflows/docs.yml/badge.svg)](https://microsserv52.github.io/product/)

> [!IMPORTANT]
> The canonical HTTP contract lives in
> [`Microsserv52/product-interface`](https://github.com/Microsserv52/product-interface).
> This repository contains the runnable service; it does not define a second
> competing contract.

## Quick start

The supported clean-machine path is fully containerized. It requires Git,
Docker and Docker Compose v2; it does not require host Java, Maven, PostgreSQL
or Redis.

```bash
git clone https://github.com/Microsserv52/product.git
cd product
./scripts/bootstrap.sh
curl http://localhost:8080/actuator/health
```

`bootstrap.sh` creates a local `.env` from `.env.example`, builds the runtime
image with the repository `Dockerfile`, starts Product, PostgreSQL, Redis and a
deterministic local identity mock, waits for readiness, then runs an
authenticated create/read/delete smoke test.

| URL | Purpose |
| --- | --- |
| `http://localhost:8080` | Product API |
| `http://localhost:8080/actuator/health` | Overall health |
| `http://localhost:8080/actuator/health/readiness` | Readiness probe |
| `http://localhost:8080/actuator/metrics` | Micrometer metrics |
| `http://localhost:8080/actuator/prometheus` | Prometheus scrape endpoint |
| `http://localhost:8081` | Local identity mock (development and CI only) |

Stop the stack with `./scripts/stop.sh`. Remove only this repository's
containers and named volumes with `./scripts/reset.sh --yes`.

## Endpoints

| Method | Path | Authentication | Success |
| --- | --- | --- | --- |
| `GET` | `/products` | Public | `200` product array |
| `POST` | `/products` | `ROLE_ADMIN` | `201` product |
| `GET` | `/products/{id}` | Public | `200` product |
| `DELETE` | `/products/{id}` | `ROLE_ADMIN` | `204` no content |

`POST /products` accepts an optional `Idempotency-Key` header. Replaying the
same key with the same normalized body returns the original product. Reusing a
key with a different body returns `409 IDEMPOTENCY_KEY_REUSED`.

The platform frontend policy permits public catalogue reads and requires
`ROLE_ADMIN` for writes. This service therefore authorizes public `GET`s and
admin-only mutations. See [API](docs/api.md) and the
[interface contract](https://github.com/Microsserv52/product-interface).

Errors use a stable envelope:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "details": [{ "field": "price", "message": "must be greater than or equal to 0.01" }]
}
```

## Local development

The full stack remains the recommended path. For focused Java work:

```bash
./mvnw test                 # unit, API, persistence, cache and contract tests
./mvnw verify               # tests plus JaCoCo coverage gate
./mvnw spring-boot:run      # requires a reachable PostgreSQL and JWT_SECRET
```

The tests use H2 for fast API tests and real PostgreSQL/Redis Testcontainers for
persistence, cache and readiness tests when Docker is available. They are
skipped only when Docker is unavailable locally, never in CI.

If your workstation has JDK 24 instead of the target JDK 25, compile and test
with `./mvnw -Djava.version=24 test`. The released image always uses Java 25.

## Configuration

All runtime configuration is environment based. Required production values:

| Variable | Required | Default | Purpose |
| --- | --- | --- | --- |
| `SPRING_DATASOURCE_URL` | Yes | local Compose value | PostgreSQL JDBC URL. |
| `SPRING_DATASOURCE_USERNAME` | Yes | `product` | Database user. |
| `SPRING_DATASOURCE_PASSWORD` | Yes | `product` | Database password. |
| `JWT_SECRET` | Yes | local-only value | HS256 secret shared with Auth and Gateway. |
| `CACHE_REDIS_URL` | No | `redis://redis:6379/0` | Redis-compatible cache endpoint. |
| `CACHE_ENABLED` | No | `false` | Enables the read cache. |
| `CACHE_TTL_SECONDS` | No | `60` | Cache entry TTL; keep short and bounded. |
| `SERVER_PORT` | No | `8080` | HTTP listen port. |
| `DB_POOL_MAX` | No | `10` | Hikari maximum pool size. |
| `MAX_REQUEST_BYTES` | No | `16384` | Request body limit. |
| `LOG_LEVEL` | No | `INFO` | Root log level. |
| `HEALTH_DETAILS` | No | `never` | Actuator health detail visibility. |

The complete reference, profiles and secret guidance are in
[Configuration](docs/configuration.md).

## Documentation

- [Architecture](docs/architecture.md)
- [API and errors](docs/api.md)
- [Configuration and environment variables](docs/configuration.md)
- [Cache policy](docs/cache.md)
- [Database and migrations](docs/database.md)
- [Observability](docs/observability.md)
- [Security](docs/security.md)
- [Kubernetes and rollback](docs/kubernetes.md)
- [Development and validation](docs/development.md)
- [Changelog and versioning](docs/changelog.md)

## Validation modes

**Standalone** uses only this repository:

```bash
./scripts/bootstrap.sh
./scripts/smoke.sh
./scripts/stop.sh
```

**Full platform** uses the platform Compose or Kubernetes topology. Publish the
image as `ghcr.io/microsserv52/product:sha-<full-commit-sha>`, then point
`PRODUCT_IMAGE` and the platform `PRODUCT_URL` at that immutable image and
service. The platform repository owns the integration Compose/Kubernetes
manifest and its smoke tests; this repository documents and tests the HTTP
contract from the service side. See
[Development and validation](docs/development.md).

## Image publication

CI publishes only after format, test, coverage, CodeQL, Compose and container
checks pass:

```text
ghcr.io/microsserv52/product:sha-<full-commit-sha>
```

Release tags may additionally publish `ghcr.io/microsserv52/product:v<version>`.
Kubernetes and production deployments must reference an immutable digest or
`sha-` tag, never `latest`. Package visibility, retention and pull
authentication are documented in [Kubernetes and rollback](docs/kubernetes.md).

## Troubleshooting

| Symptom | Check |
| --- | --- |
| `bootstrap.sh` cannot reach Docker | Start Docker and verify `docker compose version`. |
| Readiness never turns green | `docker compose logs product`; confirm PostgreSQL and `JWT_SECRET` are set. |
| `401 UNAUTHORIZED` | Obtain a fresh token from the local identity mock; check that `JWT_SECRET` matches. |
| `403 FORBIDDEN` | Use a token with `roles: ["ROLE_ADMIN"]` for writes. |
| Cache assertions fail locally | Set `CACHE_ENABLED=true`; ensure Redis is healthy. |
| Port already in use | Change `PRODUCT_PORT`, `PRODUCT_DB_PORT`, `PRODUCT_REDIS_PORT` or `PRODUCT_IDENTITY_PORT` in `.env`. |
| Stale schema after changing migrations | `./scripts/reset.sh --yes` removes local database volumes. |

## Links

- [Microsserv52 organization](https://github.com/Microsserv52)
- [Platform repository](https://github.com/Microsserv52/platform)
- [Published platform documentation](https://microsserv52.github.io/platform/)
- [Product service repository](https://github.com/Microsserv52/product)
- [Product interface repository](https://github.com/Microsserv52/product-interface)
- [Product interface documentation](https://microsserv52.github.io/product-interface/)
