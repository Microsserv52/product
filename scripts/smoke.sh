#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# shellcheck source=scripts/common.sh
. ./scripts/common.sh

BASE_URL="http://localhost:${PRODUCT_PORT}"
IDENTITY_URL="http://localhost:${PRODUCT_IDENTITY_PORT}"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

echo "Requesting a local administrator token..."
TOKEN="$(
  curl --fail --silent --show-error \
    -X POST "${IDENTITY_URL}/token" \
    -H 'Content-Type: application/json' \
    -d '{"sub":"0195ae95-5be7-7dd3-b35d-7a7d87c404fb","roles":["ROLE_ADMIN"]}' \
    | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p'
)"

if [[ -z "$TOKEN" ]]; then
  echo "The identity mock did not return an access token." >&2
  exit 1
fi

NAME="smoke-$(date +%s)-$$"
STATUS="$(
  curl --silent --show-error \
    -o "$WORK_DIR/create.json" \
    -w '%{http_code}' \
    -X POST "${BASE_URL}/products" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"${NAME}\",\"price\":10.12,\"unit\":\"kg\"}"
)"

if [[ "$STATUS" != "201" ]]; then
  echo "Expected 201 from POST /products, received ${STATUS}." >&2
  cat "$WORK_DIR/create.json" >&2
  exit 1
fi

PRODUCT_ID="$(sed -n 's/.*"id":"\([^"]*\)".*/\1/p' "$WORK_DIR/create.json")"
if [[ -z "$PRODUCT_ID" ]]; then
  echo "POST /products response did not contain an id." >&2
  cat "$WORK_DIR/create.json" >&2
  exit 1
fi

curl --fail --silent --show-error "${BASE_URL}/products/${PRODUCT_ID}" >/dev/null
curl --fail --silent --show-error \
  -X DELETE "${BASE_URL}/products/${PRODUCT_ID}" \
  -H "Authorization: Bearer ${TOKEN}" >/dev/null

DELETED_STATUS="$(
  curl --silent --show-error \
    -o /dev/null \
    -w '%{http_code}' \
    "${BASE_URL}/products/${PRODUCT_ID}"
)"
if [[ "$DELETED_STATUS" != "404" ]]; then
  echo "Expected 404 after delete, received ${DELETED_STATUS}." >&2
  exit 1
fi

echo "Product smoke test passed (created, read, deleted ${PRODUCT_ID})."
