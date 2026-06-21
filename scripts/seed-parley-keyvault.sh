#!/usr/bin/env bash
# Seed Key Vault secrets after platform apply. Values from env / Azure — never committed.
# openai-key = Azure Cognitive Services Key 1 (NOT platform.openai.com sk-…). See docs/llm-provider.md.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -f "$ROOT/.env" ]]; then
	set -a
	# shellcheck disable=SC1091
	source "$ROOT/.env"
	set +a
fi

: "${TWILIO_AUTH_TOKEN:?Set TWILIO_AUTH_TOKEN in .env or env}"

# Accept MONGODB_URI as alias (Atlas connection string)
SPRING_DATA_MONGODB_URI="${SPRING_DATA_MONGODB_URI:-${MONGODB_URI:-}}"
if [[ -z "${SPRING_DATA_MONGODB_URI}" ]]; then
	echo "Missing SPRING_DATA_MONGODB_URI in .env" >&2
	echo "  Azure Container Apps need a cloud MongoDB (MongoDB Atlas M0 is fine)." >&2
	echo "  1. Create a free cluster at https://www.mongodb.com/cloud/atlas/register" >&2
	echo "  2. Network Access → allow 0.0.0.0/0 (dev) or Azure egress IPs" >&2
	echo "  3. Database Access → create a user" >&2
	echo "  4. Connect → Drivers → copy URI, set database name to parley" >&2
	echo "  5. Add to .env: SPRING_DATA_MONGODB_URI=mongodb+srv://..." >&2
	echo "  See docs/azure-deploy.md § MongoDB (Atlas)" >&2
	exit 1
fi
if [[ "${SPRING_DATA_MONGODB_URI}" == *localhost* || "${SPRING_DATA_MONGODB_URI}" == *127.0.0.1* ]]; then
	echo "WARN: localhost MongoDB will not work from Azure Container Apps — use Atlas for dev deploy." >&2
fi

cd "$ROOT/parley-infra/platform"
KV_URI=$(terraform output -raw key_vault_uri)
KV_NAME="${KV_URI#https://}"
KV_NAME="${KV_NAME%%.vault.azure.net/}"
RG=$(terraform output -raw resource_group_name)

PROJECT="${PARLEY_PROJECT:-parley}"
ENV="${PARLEY_INFRA_ENV:-dev}"
OPENAI_ACCOUNT="${PROJECT}-${ENV}-openai"

if [[ -z "${AZURE_OPENAI_KEY:-}" ]]; then
	echo "Fetching Azure OpenAI key from ${OPENAI_ACCOUNT}..."
	AZURE_OPENAI_KEY=$(az cognitiveservices account keys list \
		-g "$RG" -n "$OPENAI_ACCOUNT" --query key1 -o tsv)
fi

echo "Seeding Key Vault ${KV_NAME}..."
az keyvault secret set --vault-name "$KV_NAME" --name openai-key --value "$AZURE_OPENAI_KEY" >/dev/null
az keyvault secret set --vault-name "$KV_NAME" --name twilio-auth-token --value "$TWILIO_AUTH_TOKEN" >/dev/null
az keyvault secret set --vault-name "$KV_NAME" --name mongodb-uri --value "$SPRING_DATA_MONGODB_URI" >/dev/null

echo "OK   Secrets set: openai-key, twilio-auth-token, mongodb-uri"
echo "     openai_key_secret_id=${KV_URI}secrets/openai-key"
echo "     twilio_token_secret_id=${KV_URI}secrets/twilio-auth-token"
echo "     mongodb_uri_secret_id=${KV_URI}secrets/mongodb-uri"
