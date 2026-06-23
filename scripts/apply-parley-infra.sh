#!/usr/bin/env bash
# POK-114: Apply parley-infra layers (foundation → platform → seed KV → app).
# Requires: az login, terraform login, dev.tfvars, HCP workspaces in Local execution mode.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck disable=SC1091
source "$ROOT/scripts/lib/load-dotenv.sh"
# shellcheck disable=SC1091
source "$ROOT/scripts/lib/export-app-tfvars.sh"
ENV="${PARLEY_INFRA_ENV:-dev}"
TFVARS="$ROOT/parley-infra/environments/${ENV}.tfvars"
AUTO="${PARLEY_TF_AUTO_APPROVE:-}"

if [[ ! -f "$TFVARS" ]]; then
	echo "Missing $TFVARS — run ./scripts/bootstrap-hcp-terraform.sh first." >&2
	exit 1
fi

if [[ -f "$ROOT/.env" ]]; then
	load_dotenv "$ROOT/.env"
fi

APPLY_FLAGS=(-var-file="$TFVARS" -input=false)
if [[ "$AUTO" == "1" || "$AUTO" == "true" ]]; then
	APPLY_FLAGS+=(-auto-approve)
fi

apply_layer() {
	local layer="$1"
	echo ""
	echo "=== Applying ${layer} ==="
	cd "$ROOT/parley-infra/${layer}"
	terraform init -input=false
	terraform apply "${APPLY_FLAGS[@]}"
}

echo "=== POK-114: Apply parley-infra ==="
echo "HCP workspaces must use Local execution mode (Azure CLI auth). See docs/hcp-terraform-bootstrap.md"

"$ROOT/scripts/validate-parley-infra.sh"

apply_layer foundation
apply_layer platform

echo ""
echo "=== Seeding Key Vault ==="
"$ROOT/scripts/seed-parley-keyvault.sh"

if ! az acr repository show-tags -n "$(cd "$ROOT/parley-infra/platform" && terraform output -raw acr_name)" \
	--repository parley --query "[?@=='latest']" -o tsv 2>/dev/null | grep -q .; then
	echo "WARN: parley:latest not in ACR yet — run ./scripts/push-parley-image.sh before app apply." >&2
fi

export_parley_app_tfvars "$ROOT"

apply_layer app

APP_URL=$(cd "$ROOT/parley-infra/app" && terraform output -raw app_url)
echo ""
echo "OK   app_url=${APP_URL}"
echo "Next: ./scripts/deploy-parley-azure.sh latest (POK-93)"
