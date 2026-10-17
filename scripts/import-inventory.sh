#!/usr/bin/env bash
# POK-480: Import an eBay Seller Hub "All active listings" CSV into Mongo inventory_items.
# Uses SPRING_DATA_MONGODB_URI from .env (same database the app reads). Runs locally only.
#
#   scripts/import-inventory.sh ~/Downloads/eBay-all-active-listings-report-....csv --dry-run
#   scripts/import-inventory.sh ~/Downloads/eBay-all-active-listings-report-....csv
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ $# -lt 1 || $# -gt 2 ]]; then
	echo "Usage: $0 <active-listings.csv> [--dry-run]" >&2
	exit 2
fi

CSV="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
if [[ ! -f "$CSV" ]]; then
	echo "No such file: $1" >&2
	exit 1
fi
DRY_RUN="${2:-}"
if [[ -n "$DRY_RUN" && "$DRY_RUN" != "--dry-run" ]]; then
	echo "Unknown option: $DRY_RUN (expected --dry-run)" >&2
	exit 2
fi

cd "$ROOT"
./gradlew -q bootRun --args="--spring.profiles.active=local,import $CSV $DRY_RUN"
