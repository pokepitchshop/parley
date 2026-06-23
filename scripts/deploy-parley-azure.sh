#!/usr/bin/env bash
# Build Parley, push to ACR, and roll the Container App revision (POK-93).
# Requires: az login, docker, terraform, platform+app infra already applied.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck disable=SC1091
source "$ROOT/scripts/lib/load-dotenv.sh"
# shellcheck disable=SC1091
source "$ROOT/scripts/lib/export-app-tfvars.sh"
TAG="${1:-latest}"
ENV="${PARLEY_INFRA_ENV:-dev}"
TFVARS="$ROOT/parley-infra/environments/${ENV}.tfvars"

if [[ ! -f "$TFVARS" ]]; then
	echo "Missing $TFVARS — copy from dev.tfvars.example and fill in subscription_id." >&2
	exit 1
fi

if [[ -f "$ROOT/.env" ]]; then
	load_dotenv "$ROOT/.env"
fi

"$ROOT/scripts/push-parley-image.sh" "$TAG"

export_parley_app_tfvars "$ROOT"

echo "Rolling Container App to ${TAG}..."
cd "$ROOT/parley-infra/app"
terraform apply -var-file="$TFVARS" -var="image_tag=${TAG}" -input=false -auto-approve

APP_URL=$(terraform output -raw app_url)
echo "Deployed. app_url=${APP_URL}"
echo "Verify: ./scripts/verify-azure-deployment.sh ${APP_URL}"
