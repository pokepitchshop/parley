#!/usr/bin/env bash
# Build Parley, push to ACR, and roll the Container App revision.
# Requires: az login, docker, terraform, platform+app infra already applied.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TAG="${1:-latest}"
ENV="${PARLEY_INFRA_ENV:-dev}"
TFVARS="$ROOT/parley-infra/environments/${ENV}.tfvars"

if [[ ! -f "$TFVARS" ]]; then
	echo "Missing $TFVARS — copy from dev.tfvars.example and fill in subscription_id." >&2
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

echo "Rolling Container App to ${TAG}..."
cd "$ROOT/parley-infra/app"
terraform apply -var-file="$TFVARS" -var="image_tag=${TAG}" -auto-approve

APP_URL=$(terraform output -raw app_url)
echo "Deployed. app_url=${APP_URL}"
echo "Verify: ./scripts/verify-azure-deployment.sh ${APP_URL}"
