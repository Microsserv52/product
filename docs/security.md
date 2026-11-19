---
icon: lucide/shield-check
---

# Security

## Authentication and authorization

Product is an OAuth2 resource server. It validates HS256 bearer tokens locally
with the shared `JWT_SECRET`; it never calls Auth per request.

- The `roles` claim maps directly to Spring Security authorities.
- Catalogue reads are public.
- `POST`, `PUT`, `PATCH` and `DELETE` on `/products/**` require `ROLE_ADMIN`.
  This includes image management under `/products/{id}/image/**` (uploads,
  attach, alt and removal).
- Unauthenticated requests receive `401 UNAUTHORIZED`.
- Authenticated non-admins receive `403 FORBIDDEN`.

The platform frontend policy is the source of truth for this split: public
catalogue GETs, admin-only writes. If the platform policy changes, update this
service and the contract together.

## Input protection

- Jakarta Validation rejects malformed or out-of-range request bodies.
- Unknown JSON fields are rejected.
- Path identifiers must be valid UUIDs.
- `Idempotency-Key` is length and character constrained.
- `RequestSizeLimitFilter` rejects bodies above `MAX_REQUEST_BYTES` before
  binding.
- Request headers are capped by the embedded server.
- Errors are safe and never include stack traces or SQL.

## Secrets

- `JWT_SECRET` is the only service secret today. Store it in a secret manager
  or Kubernetes `Secret`, not in Git or image layers.
- `.env` is ignored and must never be committed.
- Local identity mock values are development-only and must not be reused.
- Dependency and image scanning run in CI; do not disable failures for
  unresolved critical findings.

## Container posture

- Multi-stage build keeps Maven and sources out of the runtime image.
- Runtime runs as UID/GID `10001`, not root.
- The base image is pinned to `eclipse-temurin:25.0.5_8-jre-noble`.
- The container exposes only port `8080` and has a readiness health check.
- Writable paths are limited to the application's temporary directory.

## Local mocks

`mocks/identity` signs tokens for local Compose and CI. It is not built into the
Product image and is not deployed by any production manifest. Never expose it
through an ingress or reuse its default secret outside local development. The
mock also refuses to start when `ENV` or `APP_ENV` is `prod` or `production`
(case-insensitive), exiting with status `1` before binding port 8080.

`mocks/image-storage` emulates S3-compatible blob storage for product images in
Compose. It is local-only; set `PRODUCT_IMAGE_MODE=s3` with appropriate
`PRODUCT_IMAGE_S3_*` variables for production. It refuses to start when
`ENV` or `APP_ENV` is `prod` or `production`, exiting with status `1` before
binding its port.

## Reporting

Report suspected vulnerabilities to the Microsserv52 maintainers privately.
Do not open a public issue containing exploit details or credentials.
