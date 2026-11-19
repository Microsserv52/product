---
icon: lucide/route
---

# API and errors

The canonical OpenAPI document is
[`openapi/product.yaml`](https://github.com/Microsserv52/product-interface/blob/main/openapi/product.yaml).
This page summarizes the behavior implemented by the service.

## Authentication

- All mutations require a valid HS256 bearer JWT with `ROLE_ADMIN`.
- Catalogue reads are public to match the platform frontend and Gateway policy.
- Missing or invalid tokens return `401 UNAUTHORIZED`.
- Authenticated callers without the required role return `403 FORBIDDEN`.
- Tokens are validated locally; Product never calls Auth per request.

## Endpoints

| Method | Path | Authentication | Success | Errors |
| --- | --- | --- | --- | --- |
| `GET` | `/products` | Public | `200` array | `503` |
| `POST` | `/products` | `ROLE_ADMIN` | `201` product | `400`, `401`, `403`, `409`, `413`, `503` |
| `GET` | `/products/{id}` | Public | `200` product | `400`, `404`, `503` |
| `DELETE` | `/products/{id}` | `ROLE_ADMIN` | `204` | `400`, `401`, `403`, `404`, `503` |
| `POST` | `/products/{id}/image/uploads` | `ROLE_ADMIN` | `201` upload ticket | `400`, `401`, `403`, `404`, `413`, `503` |
| `POST` | `/products/{id}/image` | `ROLE_ADMIN` | `200` product | `400`, `401`, `403`, `404`, `409`, `503` |
| `PATCH` | `/products/{id}/image` | `ROLE_ADMIN` | `200` product | `400`, `401`, `403`, `404`, `503` |
| `DELETE` | `/products/{id}/image` | `ROLE_ADMIN` | `200` product | `400`, `401`, `403`, `404`, `503` |

### Create

```http
POST /products
Authorization: Bearer <token>
Content-Type: application/json
Idempotency-Key: create-tomato-001

{"name":"Tomato","price":10.12,"unit":"kg"}
```

The body is validated at the boundary: `name` 1-120 characters, `price`
0.01-9999999999.99 with at most two decimals, and `unit` 1-32 characters.
Unknown fields are rejected.

### Idempotency

`Idempotency-Key` is optional. A key is 1-128 characters from
`[A-Za-z0-9._:-]`. The service stores the key, a SHA-256 hash of the normalized
body and the created product ID.

| Situation | Result |
| --- | --- |
| New key | Product created, `201`. |
| Same key, same body | Original product, `201`. |
| Same key, different body | `409 IDEMPOTENCY_KEY_REUSED`. |
| Invalid key format | `400 VALIDATION_ERROR`. |

Keys expire with the database retention policy documented in
[Database](database.md).

## Image flow

Catalogue products serialize an optional `image` object. When absent, the
field is `null`.

```json
{
  "id": "0195abfb-7074-73a9-9d26-b4b9fbaab0a8",
  "name": "Tomato",
  "price": 10.12,
  "unit": "kg",
  "image": {
    "url": "/media/media/products/0195abfb-7074-73a9-9d26-b4b9fbaab0a8/0195ac10-5c1f-7c2a-9f3d-9a5b0b0e7a13/original.jpg",
    "alt": "Fresh ripe tomatoes",
    "width": 1200,
    "height": 1000
  }
}
```

Image management uses a two-step upload flow:

1. `POST /products/{id}/image/uploads` with `fileName`, `contentType`
   (`image/jpeg` or `image/png`), declared `size` in bytes, `width` and `height`
   returns `201` with `uploadId`, `uploadUrl`, `uploadHeaders` and `expiresAt`.
   The presigned `uploadUrl` is a PUT URL with `If-None-Match: *` and a short
   expiry (default `PT10M`).
2. The caller `PUT`s the raw bytes to `uploadUrl` and then calls
   `POST /products/{id}/image` with the `uploadId` and an `alt` string.

Attach validates the stored bytes (content type, declared vs. actual size,
decoded type and dimensions, pixel limit of 16 million), writes the immutable
`media/products/{productId}/{assetId}/original.{ext}` object and replaces any
existing image. It returns the full product.

| Endpoint | Purpose |
| --- | --- |
| `POST /products/{id}/image/uploads` | Create a presigned upload ticket. |
| `POST /products/{id}/image` | Validate, store and attach an uploaded image. |
| `PATCH /products/{id}/image` | Update only the image alt text. |
| `DELETE /products/{id}/image` | Delete the stored object; returns the product with `image: null`. |

## Error envelope

```json
{
  "code": "PRODUCT_NOT_FOUND",
  "message": "Product not found"
}
```

Validation failures may add field details:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "details": [
    { "field": "price", "message": "must be greater than or equal to 0.01" }
  ]
}
```

`message` never contains stack traces, secrets or database internals. Unknown
fields in responses must be treated as forward compatible by consumers.

## Stable error codes

| Code | Status | Meaning |
| --- | --- | --- |
| `VALIDATION_ERROR` | `400` | Invalid body, path UUID or header. |
| `UNAUTHORIZED` | `401` | Missing, expired or invalid token. |
| `FORBIDDEN` | `403` | Authenticated but missing `ROLE_ADMIN`. |
| `PRODUCT_NOT_FOUND` | `404` | Product does not exist. |
| `IDEMPOTENCY_KEY_REUSED` | `409` | Key reused with a different body. |
| `IMAGE_ALREADY_EXISTS` | `409` | A validated image already exists for this upload. |
| `PAYLOAD_TOO_LARGE` | `413` | Body exceeds `MAX_REQUEST_BYTES`. |
| `DEPENDENCY_UNAVAILABLE` | `503` | PostgreSQL cannot serve the request. |
