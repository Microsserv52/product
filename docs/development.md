---
icon: lucide/terminal
---

# Development and validation

## Prerequisites

| Path | Requirements |
| --- | --- |
| Containerized | Git, Docker, Docker Compose v2, curl. |
| Local Java | JDK 25, Maven via `./mvnw`. |
| Local tests with containers | Docker for PostgreSQL and Redis Testcontainers. |
| Documentation | Zensical, or use the CI workflow. |

## Standalone mode

Only this repository is used:

```bash
cp .env.example .env
./scripts/bootstrap.sh
./scripts/smoke.sh
docker compose exec -T product id -u   # must print 10001
./scripts/stop.sh
```

`bootstrap.sh` is idempotent. It creates `.env` only when absent, builds the
image from the repository `Dockerfile`, starts every dependency, waits for
readiness and runs the authenticated smoke test.

Reset local data only with explicit confirmation:

```bash
./scripts/reset.sh --yes
```

## Tests

```bash
./mvnw test       # all tests
./mvnw verify     # tests + JaCoCo line coverage gate
```

| Suite | Coverage |
| --- | --- |
| `ProductApiTest` | Auth, validation, status codes, idempotency and contract snapshots. |
| `ProductPersistenceTest` | Real PostgreSQL write/read-back and migrations. |
| `ProductCacheTest` | Redis hit, miss, invalidation and list invalidation. |
| `ProductCacheOutageTest` | Redis-unavailable degradation. |
| `ProductReadinessTest` | Readiness turns `503` when PostgreSQL stops; liveness stays `200`. |
| `ProductApplicationTests` | Context and actuator wiring. |

Contract tests load the snapshot in `src/test/resources/contract` copied from
the published interface. Update that snapshot only with a matching interface
release.

## Full-platform mode

The platform repository owns integration Compose and Kubernetes manifests:

1. Publish the service image as an immutable `sha-<full-commit-sha>` tag.
2. Set `PRODUCT_IMAGE` to that image and `PRODUCT_URL=http://product:8080` in the
   platform topology.
3. Start the platform stack from the platform repository.
4. Verify Gateway routing, Auth-issued tokens, Order product lookup and the
   public catalogue.
5. Run the platform integration smoke tests or k6 scenario.

Product does not start Account, Auth, Gateway or Order, and the platform does not
start this repository's PostgreSQL, Redis or identity mock. Each repository owns
its own local dependencies.

For demo data in the platform topology, the platform repository ships an opt-in
seeder (`scripts/seed_demo.py`, `docker compose --profile demo up demo-seeder`)
and documented SQL steps for the `products` table; see the platform
[Get started](https://github.com/Microsserv52/platform/blob/main/docs/development/get_started.md) guide. The seeder drives
the real API, so products it creates are indistinguishable from operator-created
ones; both refuse to run in production environments. The seeder attaches a
local picture file for each product through the image endpoints and the
Compose `image-storage` mock; in production, attach `media/...` objects in AWS
S3 via `PRODUCT_IMAGE_MODE=s3`.

## CI

Pull requests run:

- Maven validation and whitespace/merge-marker checks;
- the full test and coverage gate against Testcontainers;
- CodeQL;
- Docker build, Compose bootstrap/smoke and a non-root user check;
- container scanning.

Pushes to the default branch and release tags publish the tested image to GHCR.
The publish job uses `GITHUB_TOKEN` with `contents: read` and `packages: write`;
no personal tokens are required.
