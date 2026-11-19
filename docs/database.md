---
icon: lucide/database
---

# Database and migrations

PostgreSQL is the system of record. Flyway owns the schema; Hibernate validates
it with `ddl-auto=validate` and never creates tables at runtime.

## Schema

### `products`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | `UUID` | Application-generated primary key. |
| `name` | `VARCHAR(120)` | Display name, trimmed at the boundary. |
| `price` | `NUMERIC(12,2)` | Positive, stored with two decimals. |
| `unit` | `VARCHAR(32)` | Sale unit. |
| `created_at` | `TIMESTAMPTZ` | Defaults to `now()`, UTC. |
| `updated_at` | `TIMESTAMPTZ` | Defaults to `now()`, UTC. |

### `idempotency_keys`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | `UUID` | Primary key. |
| `idempotency_key` | `VARCHAR(128)` | Unique retry key. |
| `request_hash` | `VARCHAR(64)` | SHA-256 of the normalized request body. |
| `product_id` | `UUID` | Foreign key to `products`, `ON DELETE CASCADE`. |
| `created_at` | `TIMESTAMPTZ` | Defaults to `now()`, UTC. |

## Indexes

| Index | Query path |
| --- | --- |
| `idx_products_name` | `findAllByOrderByNameAsc` for `GET /products`. |
| Unique `idempotency_key` | Point lookup on retries and conflict detection. |
| `idx_idempotency_keys_created_at` | Retention and operational cleanup. |

No additional indexes are added without a measured query path. Every new index
must document the query it serves and its write cost.

## Migrations

Migrations live in `src/main/resources/db/migration` and run automatically when
`FLYWAY_ENABLED=true`. They are forward-only and versioned:

```text
V1__create_products.sql
V2__create_idempotency_keys.sql
V3__add_product_images.sql
```

### `product_images`

| Column | Type | Notes |
| --- | --- | --- |
| `product_id` | `UUID` | Primary key, foreign key to `products` (`ON DELETE CASCADE`). |
| `asset_id` | `UUID` | Asset identifier shared with the upload ticket. |
| `object_key` | `VARCHAR(512)` | Blob key under `media/products/{product_id}/{asset_id}/original.{ext}`. |
| `content_type` | `VARCHAR(64)` | `image/jpeg` or `image/png`. |
| `bytes` | `BIGINT` | Object size in bytes. |
| `width`, `height` | `INTEGER` | Decoded dimensions (1-4096). |
| `alt` | `VARCHAR(200)` | Alternative text. |
| `uploader` | `VARCHAR(120)` | JWT subject of the attaching caller. |
| `created_at` | `TIMESTAMPTZ` | Defaults to `now()`, UTC. |

### `product_image_uploads`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | `UUID` | Primary key, the `uploadId` returned to the client. |
| `product_id` | `UUID` | Foreign key to `products` (`ON DELETE CASCADE`). |
| `asset_id` | `UUID` | Asset identifier shared with the final image. |
| `object_key` | `VARCHAR(512)` | Blob key under `pending/products/{product_id}/{asset_id}/original.{ext}`. |
| `content_type`, `bytes`, `width`, `height` | | Declared upload metadata, validated against the stored bytes. |
| `expires_at` | `TIMESTAMPTZ` | Ticket expiry (`PRODUCT_IMAGE_UPLOAD_EXPIRY`). |
| `consumed_at` | `TIMESTAMPTZ` | Set when the ticket is attached. |
| `created_at` | `TIMESTAMPTZ` | Defaults to `now()`, UTC. |

The `idx_product_image_uploads_expires_at` index supports ticket expiry and
retention cleanup.

- Never edit an applied migration; add a new version.
- Test migrations against real PostgreSQL, not only H2.
- Flyway uses a PostgreSQL advisory lock, so multiple replicas can start
  concurrently without racing the schema history.
- For a breaking change, use an expand/contract sequence across releases.

## Retention

`idempotency_keys` currently has no automatic expiry. An operator should delete
rows older than the documented retry window (for example 30 days) using the
`created_at` index, or add a scheduled cleanup migration/job in the owning
environment. Product rows have no automatic deletion; `DELETE /products/{id}`
is explicit.

## Backup and recovery

- Use managed PostgreSQL snapshots or `pg_dump` according to platform policy.
- Restore into a fresh database, run Flyway, then start a single replica before
  scaling out.
- Never drop production tables or indexes without explicit owner approval.
