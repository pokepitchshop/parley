#!/usr/bin/env bash
# POK-111: Azure subscription discovery audit before parley-infra apply.
# Runs Azure Resource Graph queries and provider checks. Requires: az login.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TFVARS_EXAMPLE="$ROOT/parley-infra/environments/dev.tfvars.example"

if ! command -v az >/dev/null 2>&1; then
	echo "ERROR: Azure CLI (az) not found. Install and run: az login" >&2
	exit 1
fi

if ! az account show >/dev/null 2>&1; then
	echo "ERROR: Not logged in. Run: az login" >&2
	exit 1
fi

az extension add --name resource-graph -y >/dev/null 2>&1 || true

SUB_ID=$(az account show --query id -o tsv)
SUB_NAME=$(az account show --query name -o tsv)
EXPECTED_SUB=$(grep -E '^\s*subscription_id\s*=' "$TFVARS_EXAMPLE" | sed -E 's/.*"([0-9a-f-]{36})".*/\1/' | head -1)

echo "=== Azure subscription discovery audit (POK-111) ==="
echo "Date:     $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo "Account:  $(az account show --query user.name -o tsv)"
echo "Active:   $SUB_NAME ($SUB_ID)"
echo ""

if [[ -n "$EXPECTED_SUB" && "$SUB_ID" != "$EXPECTED_SUB" ]]; then
	echo "WARN  Active subscription ($SUB_ID) != dev.tfvars.example ($EXPECTED_SUB)"
else
	echo "OK    Subscription matches dev.tfvars.example"
fi

echo ""
echo "=== Provider registration (required for parley-infra) ==="
NEED_REGISTER=0
for provider in Microsoft.App Microsoft.ContainerRegistry Microsoft.KeyVault Microsoft.OperationalInsights Microsoft.CognitiveServices; do
	state=$(az provider show -n "$provider" --query registrationState -o tsv 2>/dev/null || echo "Unknown")
	if [[ "$state" == "Registered" ]]; then
		echo "OK    $provider: $state"
	else
		echo "WARN  $provider: $state"
		NEED_REGISTER=1
	fi
done

if [[ "$NEED_REGISTER" -eq 1 ]]; then
	echo ""
	echo "Fix: az provider register -n Microsoft.App --wait"
	echo "     az provider register -n Microsoft.ContainerRegistry --wait"
	echo "     az provider register -n Microsoft.KeyVault --wait"
	echo "     az provider register -n Microsoft.CognitiveServices --wait"
fi

echo ""
echo "=== Parley footprint (expect zero pre-apply) ==="
PARLEY_JSON=$(az graph query -q "Resources | where resourceGroup startswith 'parley-' or name startswith 'parley' | project name, type, resourceGroup, location | order by resourceGroup asc, name asc" -o json)
PARLEY_COUNT=$(echo "$PARLEY_JSON" | python3 -c "import json,sys; print(json.load(sys.stdin).get('count', 0))")
if [[ "$PARLEY_COUNT" == "0" ]]; then
	echo "OK    No parley-* resources found (greenfield)"
else
	echo "WARN  Found $PARLEY_COUNT parley-* resource(s):"
	echo "$PARLEY_JSON" | python3 -c "import json,sys; d=json.load(sys.stdin); [print(f\"  {r['name']} ({r['type']}) in {r['resourceGroup']}\") for r in d.get('data',[])]"
fi

echo ""
echo "=== Naming collision check ==="
az graph query -q "Resources | where name in ('parleydevacr', 'parley-dev-kv', 'parley-dev-openai', 'parley-dev-rg', 'parley-dev-id', 'parley-dev-app', 'parley-dev-cae', 'parley-dev-logs') | project name, type, resourceGroup, location | order by name asc" -o table

echo ""
echo "=== Legacy Oldman inventory (PokePitchShop RG) ==="
az graph query -q "Resources | where resourceGroup =~ 'PokePitchShop' | project name, type, sku=sku.name, location | order by type asc, name asc" -o table

echo ""
echo "=== PostgreSQL flexible servers (subscription-wide) ==="
PG_JSON=$(az graph query -q "Resources | where type =~ 'microsoft.dbforpostgresql/flexibleservers' | project name, resourceGroup, location, sku=sku.name" -o json)
PG_COUNT=$(echo "$PG_JSON" | python3 -c "import json,sys; print(json.load(sys.stdin).get('count', 0))")
if [[ "$PG_COUNT" == "0" ]]; then
	echo "OK    No PostgreSQL flexible servers (intelligentoldmanbrain already removed)"
else
	echo "$PG_JSON" | python3 -c "import json,sys; d=json.load(sys.stdin); [print(f\"  {r['name']} in {r['resourceGroup']}\") for r in d.get('data',[])]"
fi

echo ""
echo "=== OpenAI accounts in eastus2 ==="
az graph query -q "Resources | where type =~ 'microsoft.cognitiveservices/accounts' and location =~ 'eastus2' | project name, resourceGroup, location, sku=sku.name" -o table

echo ""
echo "=== HCP Terraform (manual check) ==="
if terraform login -help >/dev/null 2>&1; then
	echo "      Run 'terraform login' then 'terraform init' in each parley-infra layer."
	echo "      Workspaces needed: parley-foundation, parley-platform, parley-app (pokepitchshop)"
else
	echo "WARN  Terraform CLI not found"
fi

echo ""
echo "Done. See docs/azure-discovery-audit.md for the latest committed gap report."
