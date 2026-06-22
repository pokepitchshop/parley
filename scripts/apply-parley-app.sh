#!/usr/bin/env bash
# POK-92 / POK-114: Apply the app layer only (foundation + platform already applied).
# Seeds Key Vault from .env, exports TF_VAR_* for Twilio/Mongo secrets, applies parley-app.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck disable=SC1091
source "$ROOT/scripts/lib/load-dotenv.sh"
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

echo "=== Seeding Key Vault (idempotent) ==="
if [[ "${PARLEY_SKIP_KV_SEED:-}" == "1" || "${PARLEY_SKIP_KV_SEED:-}" == "true" ]]; then
	echo "Skipping Key Vault seed (PARLEY_SKIP_KV_SEED set)."
else
	"$ROOT/scripts/seed-parley-keyvault.sh"
fi

KV_URI=$(cd "$ROOT/parley-infra/platform" && terraform output -raw key_vault_uri)
: "${TWILIO_ACCOUNT_SID:?Set TWILIO_ACCOUNT_SID in .env before app apply}"

export TF_VAR_twilio_account_sid="$TWILIO_ACCOUNT_SID"
export TF_VAR_twilio_token_secret_id="${KV_URI}secrets/twilio-auth-token"
export TF_VAR_mongodb_uri_secret_id="${KV_URI}secrets/mongodb-uri"

if [[ "${PARLEY_SKIP_KV_PREFLIGHT:-}" != "1" && "${PARLEY_SKIP_KV_PREFLIGHT:-}" != "true" ]]; then
	"$ROOT/scripts/verify-parley-infra-ready.sh"
fi

echo ""
echo "=== Applying app layer ==="
cd "$ROOT/parley-infra/app"
terraform init -input=false
terraform apply "${APPLY_FLAGS[@]}"

APP_URL=$(terraform output -raw app_url)
echo ""
echo "OK   app_url=${APP_URL}"
echo "Next: ./scripts/deploy-parley-azure.sh latest (POK-93)"
