#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# shellcheck source=scripts/common.sh
. ./scripts/common.sh

confirmed=false
for argument in "$@"; do
  if [[ "$argument" == "--yes" || "$argument" == "-y" ]]; then
    confirmed=true
  fi
done

if [[ "$confirmed" != true ]]; then
  echo "This removes the Product Compose containers and the named volumes"
  echo "product_product-db-data and product_product-redis-data."
  read -r -p "Continue? [y/N] " answer
  case "$answer" in
    y|Y|yes|YES) ;;
    *) echo "Aborted."; exit 1 ;;
  esac
fi

"${COMPOSE[@]}" down --volumes --remove-orphans
echo "Product local containers and volumes were removed."
