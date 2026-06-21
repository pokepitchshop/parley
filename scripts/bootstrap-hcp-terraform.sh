#!/usr/bin/env bash
# POK-112: One-time bootstrap for parley-infra on HCP Terraform + Azure.
# Registers providers, creates local dev.tfvars, verifies terraform init per layer.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TFVARS="$ROOT/parley-infra/environments/dev.tfvars"
TFVARS_SRC="$ROOT/parley-infra/environments/dev.tfvars.example"
ORG="pokepitchshop"

WORKSPACES=(
	"parley-foundation:parley-infra/foundation"
	"parley-platform:parley-infra/platform"
	"parley-app:parley-infra/app"
)

PROVIDERS=(
	Microsoft.App
	Microsoft.ContainerRegistry
	Microsoft.KeyVault
	Microsoft.CognitiveServices
)

pass() { echo "OK   $*"; }
warn() { echo "WARN $*"; }
fail() { echo "FAIL $*"; exit 1; }

echo "=== POK-112: HCP Terraform bootstrap ==="

if ! command -v az >/dev/null 2>&1; then
	fail "Azure CLI not found. Install and run: az login"
fi
if ! az account show >/dev/null 2>&1; then
	fail "Not logged in. Run: az login"
fi
if ! command -v terraform >/dev/null 2>&1; then
	fail "Terraform CLI not found. Install from https://developer.hashicorp.com/terraform/install"
fi

SUB_ID=$(az account show --query id -o tsv)
echo "Subscription: $(az account show --query name -o tsv) ($SUB_ID)"
echo ""

echo "=== Register Azure providers ==="
NEED=0
for provider in "${PROVIDERS[@]}"; do
	state=$(az provider show -n "$provider" --query registrationState -o tsv 2>/dev/null || echo "Unknown")
	if [[ "$state" == "Registered" ]]; then
		pass "$provider: Registered"
	else
		warn "$provider: $state — registering (may take a few minutes)..."
		az provider register -n "$provider" --wait >/dev/null
		state=$(az provider show -n "$provider" --query registrationState -o tsv)
		if [[ "$state" == "Registered" ]]; then
			pass "$provider: Registered"
		else
			warn "$provider: still $state"
			NEED=1
		fi
	fi
done
[[ "$NEED" -eq 0 ]] || warn "Some providers still registering; re-run this script later."
echo ""

echo "=== Local dev.tfvars ==="
if [[ -f "$TFVARS" ]]; then
	pass "dev.tfvars already exists ($TFVARS)"
else
	cp "$TFVARS_SRC" "$TFVARS"
	if [[ "$(uname)" == "Darwin" ]]; then
		sed -i '' "s/subscription_id = \".*\"/subscription_id = \"$SUB_ID\"/" "$TFVARS"
	else
		sed -i "s/subscription_id = \".*\"/subscription_id = \"$SUB_ID\"/" "$TFVARS"
	fi
	pass "Created dev.tfvars with subscription_id=$SUB_ID"
fi
echo ""

echo "=== HCP Terraform login ==="
if [[ -f "$HOME/.terraform.d/credentials.tfrc.json" ]] && grep -q "app.terraform.io" "$HOME/.terraform.d/credentials.tfrc.json" 2>/dev/null; then
	pass "Terraform Cloud credentials found (~/.terraform.d/credentials.tfrc.json)"
else
	warn "Not logged in to HCP Terraform. Run: terraform login"
	echo "      Then create workspaces in org $ORG (see docs/hcp-terraform-bootstrap.md):"
	for entry in "${WORKSPACES[@]}"; do
		echo "        - ${entry%%:*}"
	done
	echo ""
	echo "      Re-run this script after login + workspace creation to verify init."
	exit 0
fi

echo ""
echo "=== Terraform init (verify HCP workspaces) ==="
INIT_OK=1
for entry in "${WORKSPACES[@]}"; do
	ws="${entry%%:*}"
	dir="${entry##*:}"
	echo "--- $ws ($dir) ---"
	if (cd "$ROOT/$dir" && terraform init -input=false); then
		pass "init OK: $ws"
	else
		warn "init failed: $ws — create workspace in HCP UI or fix permissions"
		INIT_OK=0
	fi
done

echo ""
if [[ "$INIT_OK" -eq 1 ]]; then
	pass "Bootstrap complete. Next: POK-113 (terraform validate) then POK-114 (apply)."
else
	warn "Fix failed inits, then re-run: ./scripts/bootstrap-hcp-terraform.sh"
	exit 1
fi
