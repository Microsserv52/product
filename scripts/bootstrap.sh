#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

for command in git docker curl; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "Missing required command: $command" >&2
    exit 1
  fi
done

if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose v2 is required (docker compose version failed)." >&2
  exit 1
fi

if [[ ! -f .env ]]; then
  cp .env.example .env
  echo "Created .env from .env.example. Update JWT_SECRET before sharing this environment."
fi

# shellcheck source=scripts/common.sh
. ./scripts/common.sh

echo "Building and starting the standalone Product stack..."
"${COMPOSE[@]}" up --detach --build

echo "Waiting for Product readiness on http://localhost:${PRODUCT_PORT}..."
ready=false
for _ in $(seq 1 90); do
  if curl --fail --silent --show-error "http://localhost:${PRODUCT_PORT}/actuator/health/readiness" >/dev/null; then
    ready=true
    break
  fi
  sleep 2
done

if [[ "$ready" != true ]]; then
  echo "Product did not become ready. Recent logs:" >&2
  "${COMPOSE[@]}" logs --tail=80 product >&2 || true
  exit 1
fi

./scripts/smoke.sh

echo
echo "Product is running at http://localhost:${PRODUCT_PORT}"
echo "Local identity mock: http://localhost:${PRODUCT_IDENTITY_PORT}"
echo "Stop with ./scripts/stop.sh; remove local data with ./scripts/reset.sh --yes."
