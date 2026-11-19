---
icon: lucide/history
---

# Changelog and versioning

Product follows semantic versioning for the service image. The HTTP contract
follows the version published by
[`Microsserv52/product-interface`](https://github.com/Microsserv52/product-interface).

## 0.1.0 - 2026-11-16

### Added

- Java 25 / Spring Boot 4 Product service with PostgreSQL and Flyway.
- `POST /products`, `GET /products`, `GET /products/{id}` and
  `DELETE /products/{id}`.
- HS256 JWT validation, public catalogue reads and `ROLE_ADMIN` mutations.
- Optional `Idempotency-Key` with persisted replay and conflict detection.
- Optional Redis read cache with synchronous invalidation, TTL and graceful
  degradation.
- Stable error envelope, request IDs, payload limits and request timeouts.
- Liveness, readiness, info, Micrometer and Prometheus endpoints.
- Standalone Compose stack with PostgreSQL, Redis and a local identity mock.
- Unit, API, persistence, cache, outage, readiness and contract tests.
- Product images: `POST /products/{id}/image/uploads`, `POST/PATCH/DELETE
  /products/{id}/image`, the `Product.image` response field and two blob-storage
  backends (`HttpImageStorage` for Compose, `S3ImageStorage` for prod).
- Multi-stage non-root image, GHCR publication, CodeQL and documentation site.

### Interface compatibility

- Product interface `0.1.0`.
- Contract snapshot: `src/test/resources/contract`.

## Versioning policy

- **MAJOR** — removed or renamed required contract fields, changed status codes
  or authentication rules.
- **MINOR** — additive optional fields or new endpoints.
- **PATCH** — documentation, examples or non-behavioral fixes.
- Every release records the interface version and the image digest.
- Migrations are forward-only; breaking schema changes use expand/contract.
