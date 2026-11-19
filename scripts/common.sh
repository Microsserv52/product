#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

PRODUCT_PORT="${PRODUCT_PORT:-8080}"
PRODUCT_IDENTITY_PORT="${PRODUCT_IDENTITY_PORT:-8081}"
COMPOSE=(docker compose -f compose.yml)
