#!/usr/bin/env bash
# Export TF_VAR_* for the parley-app Terraform layer (Key Vault secret IDs, not secret values).
export_parley_app_tfvars() {
	local root="${1:?repo root required}"
	local kv_uri
	kv_uri="$(cd "$root/parley-infra/platform" && terraform output -raw key_vault_uri)"
	: "${TWILIO_ACCOUNT_SID:?Set TWILIO_ACCOUNT_SID in .env before app apply}"
	export TF_VAR_twilio_account_sid="$TWILIO_ACCOUNT_SID"
	export TF_VAR_twilio_token_secret_id="${kv_uri}secrets/twilio-auth-token"
	export TF_VAR_mongodb_uri_secret_id="${kv_uri}secrets/mongodb-uri"
}
