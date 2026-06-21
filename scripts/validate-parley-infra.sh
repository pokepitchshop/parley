#!/usr/bin/env bash
# POK-113: fmt + validate all parley-infra Terraform layers.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LAYERS=(foundation platform app)

pass() { echo "OK   $*"; }
fail() { echo "FAIL $*"; exit 1; }

echo "=== POK-113: parley-infra validation ==="

if ! command -v terraform >/dev/null 2>&1; then
	fail "Terraform CLI not found"
fi

echo "--- terraform fmt ---"
if terraform fmt -check -recursive "$ROOT/parley-infra/"; then
	pass "fmt check passed"
else
	fail "fmt check failed — run: terraform fmt -recursive parley-infra/"
fi

echo ""
echo "--- terraform validate (per layer) ---"
for layer in "${LAYERS[@]}"; do
	dir="$ROOT/parley-infra/$layer"
	echo ">> $layer"
	if ! (cd "$dir" && terraform init -input=false >/dev/null && terraform validate); then
		fail "validate failed: $layer"
	fi
	pass "$layer"
done

echo ""
echo "--- RBAC role assignments (platform) ---"
PLATFORM="$ROOT/parley-infra/platform/main.tf"
for role in "AcrPull" "Key Vault Secrets User" "Cognitive Services OpenAI User"; do
	if grep -q "$role" "$PLATFORM"; then
		pass "role: $role"
	else
		fail "missing role assignment: $role in platform/main.tf"
	fi
done

echo ""
pass "All validation checks passed. Ready for POK-114 (apply)."
