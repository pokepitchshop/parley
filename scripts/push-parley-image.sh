#!/usr/bin/env bash
# Build Parley and push to ACR. Run before first app apply (POK-114) — Container Apps need the image tag in ACR.
#
# Default: az acr build (native linux/amd64 on Azure — reliable from Apple Silicon Macs).
# Override: PARLEY_IMAGE_BUILD=local for bootBuildImage + docker push (linux/amd64 cross-build).
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

BUILD_METHOD="${PARLEY_IMAGE_BUILD:-acr}"
if [[ "$BUILD_METHOD" != "acr" && "$BUILD_METHOD" != "local" ]]; then
	echo "PARLEY_IMAGE_BUILD must be 'acr' or 'local' (got: $BUILD_METHOD)" >&2
	exit 1
fi

push_image_acr() {
	echo "Building boot JAR locally..."
	cd "$ROOT"
	./gradlew bootJar --no-daemon

	echo "Building ${IMAGE} in ACR (native linux/amd64)..."
	az acr build -r "$ACR_NAME" -t "parley:${TAG}" -f "$ROOT/Dockerfile" "$ROOT"
}

push_image_local() {
	local platform="${PARLEY_IMAGE_PLATFORM:-linux/amd64}"
	echo "Building image ${IMAGE} locally (${platform})..."
	cd "$ROOT"
	./gradlew bootBuildImage --imageName="$IMAGE" --imagePlatform="$platform" --no-daemon

	echo "Logging in to ACR ${ACR_NAME}..."
	az acr login --name "$ACR_NAME"

	echo "Pushing ${IMAGE}..."
	docker push "$IMAGE"
}

case "$BUILD_METHOD" in
acr) push_image_acr ;;
local) push_image_local ;;
esac

echo "OK   Image pushed: ${IMAGE}"
echo "Next: PARLEY_TF_AUTO_APPROVE=1 ./scripts/apply-parley-app.sh"
