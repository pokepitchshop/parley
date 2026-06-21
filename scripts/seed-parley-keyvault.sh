#!/usr/bin/env bash
# POK-114: Seed Key Vault secrets after platform apply. Values from env — never committed.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -f "$ROOT/.env" ]]; then
	set -a
	# shellcheck disable=SC1091
	source "$ROOT/.env"
	set +a
fi

: "${TWILIO_AUTH_TOKEN:?Set TWILIO_AUTH_TOKEN in .env or env}"
: "${SPRING_DATA_MONGODB_URI:?Set SPRING_DATA_MONGODB_URI in .env or env}"

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
