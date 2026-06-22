#!/usr/bin/env bash
# Preflight before app apply: confirm platform outputs and Key Vault secrets exist.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV="${PARLEY_INFRA_ENV:-dev}"
TFVARS="$ROOT/parley-infra/environments/${ENV}.tfvars"

if [[ ! -f "$TFVARS" ]]; then
	echo "FAIL Missing $TFVARS" >&2
	exit 1
fi

cd "$ROOT/parley-infra/platform"
KV_URI=$(terraform output -raw key_vault_uri)
KV_NAME="${KV_URI#https://}"
KV_NAME="${KV_NAME%%.vault.azure.net/}"

check_secret() {
	local name="$1"
	if az keyvault secret show --vault-name "$KV_NAME" --name "$name" --query id -o tsv >/dev/null 2>&1; then
		echo "OK   Key Vault secret ${name}"
		return 0
	fi
	echo "FAIL Key Vault secret '${name}' missing or not readable in ${KV_NAME}." >&2
	echo "     Run ./scripts/seed-parley-keyvault.sh (requires Key Vault Secrets Officer on the vault)." >&2
	return 1
}

echo "=== Parley infra preflight (app layer) ==="
az group show -n parley-dev-rg --query name -o tsv >/dev/null 2>&1 || {
	echo "FAIL parley-dev-rg not found — apply foundation + platform first." >&2
	exit 1
}
echo "OK   Resource group parley-dev-rg"

FAIL=0
check_secret twilio-auth-token || FAIL=1
check_secret mongodb-uri || FAIL=1

if [[ "$FAIL" -ne 0 ]]; then
	exit 1
fi

echo "Ready for ./scripts/apply-parley-app.sh"
