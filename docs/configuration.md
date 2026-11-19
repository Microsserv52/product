---
icon: lucide/settings
---

# Configuration

Configuration is environment based. The base profile has safe local defaults;
the `prod` profile requires explicit datasource and JWT values and emits
structured logs.

## Environment variables

| Variable | Required | Default | Notes |
| --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | No | none | Use `prod` in Compose and Kubernetes. |
| `SERVER_PORT` | No | `8080` | HTTP port. |
| `SPRING_DATASOURCE_URL` | Yes in `prod` | `jdbc:postgresql://product-db:5432/product` | JDBC URL. |
| `SPRING_DATASOURCE_USERNAME` | Yes in `prod` | `product` | Database user. |
| `SPRING_DATASOURCE_PASSWORD` | Yes in `prod` | `product` | Database password. |
| `DB_POOL_MAX` | No | `10` | Hikari maximum pool size. |
| `DB_POOL_MIN_IDLE` | No | `1` | Hikari minimum idle connections. |
| `FLYWAY_ENABLED` | No | `true` | Run Flyway migrations at startup. |
| `JWT_SECRET` | Yes | local-only placeholder | HS256 shared secret, at least 32 random characters. |
| `CACHE_REDIS_URL` | No | `redis://redis:6379/0` | Redis-compatible endpoint. |
| `CACHE_ENABLED` | No | `false` | Enables the read cache. |
| `CACHE_TTL_SECONDS` | No | `60` | Entry TTL in seconds. |
| `MAX_REQUEST_BYTES` | No | `16384` | Body limit enforced before controller binding. |
| `PRODUCT_IMAGE_MODE` | No | `mock` (`s3` in `prod`) | Image storage backend: `mock` (Compose blob mock) or `s3` (AWS S3). |
| `PRODUCT_IMAGE_STORAGE_URL` | No | `http://image-storage:8090` | Base URL of the mock blob storage when mode is `mock`. |
| `PRODUCT_MEDIA_BASE_URL` | No | `/media` | Public URL prefix for attached images in `mock` mode. |
| `PRODUCT_IMAGE_UPLOAD_EXPIRY` | No | `PT10M` | Lifetime of a presigned upload URL (ISO-8601 duration). |
| `PRODUCT_IMAGE_S3_BUCKET` | Yes in `s3` mode | none | S3 bucket for image objects. |
| `PRODUCT_IMAGE_S3_REGION` | Yes in `s3` mode | none | AWS region of the bucket. |
| `PRODUCT_IMAGE_S3_MEDIA_ORIGIN` | No | `https://<bucket>.s3.<region>.amazonaws.com` | Public media origin (e.g. CloudFront). |
| `LOG_LEVEL` | No | `INFO` | Root log level. |
| `LOG_STRUCTURED_FORMAT` | No | `ecs` in `prod` | Structured console format. |
| `HEALTH_DETAILS` | No | `never` | Set `always` only for trusted local diagnostics. |

## Profiles

### Base

Used by tests and local `spring-boot:run`. It contains development defaults,
including the Compose hostnames `product-db` and `redis`.

### `prod`

`application-prod.properties` removes datasource fallbacks, requires
`SPRING_DATASOURCE_*` and `JWT_SECRET`, and enables ECS structured console logs.
Compose activates this profile.

## Secrets

- Never commit `.env`, tokens, passwords or private keys.
- Use a secret manager or Kubernetes `Secret` in deployed environments.
- `JWT_SECRET` must be identical to the value used by Auth and Gateway and must
  be rotated deliberately; rotation invalidates existing tokens.
- Provider/API credentials do not exist in Product today. If a future outbound
  dependency needs one, add a dedicated environment variable and a secret
  reference rather than reusing `JWT_SECRET`.
- Local defaults in `.env.example` are intentionally unsafe for production.

## Local ports

| Variable | Default | Purpose |
| --- | --- | --- |
| `PRODUCT_PORT` | `8080` | Published Product port. |
| `PRODUCT_IMAGE_STORAGE_PORT` | `8090` | Published image-storage mock port (standalone stack only). |
| `PRODUCT_IDENTITY_PORT` | `8081` | Local identity mock. |
| `PRODUCT_DB_PORT` | `5432` | Published PostgreSQL port. |
| `PRODUCT_REDIS_PORT` | `6379` | Published Redis port. |
