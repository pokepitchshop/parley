#!/usr/bin/env bash
# Build Parley and push to ACR. Run before first app apply (POK-114) — Container Apps need the image tag in ACR.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TAG="${1:-latest}"
ENV="${PARLEY_INFRA_ENV:-dev}"
TFVARS="$ROOT/parley-infra/environments/${ENV}.tfvars"

if [[ ! -f "$TFVARS" ]]; then
	echo "Missing $TFVARS — run ./scripts/bootstrap-hcp-terraform.sh first." >&2
	exit 1
fi

echo "Reading ACR outputs from parley-infra/platform..."
cd "$ROOT/parley-infra/platform"
ACR_SERVER=$(terraform output -raw acr_login_server)
ACR_NAME=$(terraform output -raw acr_name)
IMAGE="${ACR_SERVER}/parley:${TAG}"

echo "Building image ${IMAGE}..."
cd "$ROOT"
./gradlew bootBuildImage --imageName="$IMAGE"

echo "Logging in to ACR ${ACR_NAME}..."
az acr login --name "$ACR_NAME"

echo "Pushing ${IMAGE}..."
docker push "$IMAGE"

echo "OK   Image pushed: ${IMAGE}"
echo "Next: PARLEY_TF_AUTO_APPROVE=1 ./scripts/apply-parley-app.sh"
