# Deploy Parley to Azure Container Apps (POK-25)

Host Parley on Azure Container Apps with **scale-to-zero** so idle cost is ~$0 compute.
This retires ngrok ([POK-10](https://linear.app/pokepitchshop/issue/POK-10)) and gives Twilio a stable HTTPS URL ([POK-11](https://linear.app/pokepitchshop/issue/POK-11)).

For Terraform layer details and cost discipline, see [`parley-infra/README.md`](../parley-infra/README.md).

## Prerequisites

- Azure subscription with `az login` (or HCP Terraform Azure OIDC configured)
- [Terraform CLI](https://developer.hashicorp.com/terraform/install) + HCP Terraform access to org `pokepitchshop-org`
- Docker (for `bootBuildImage` push to ACR)
- Three HCP workspaces: `parley-foundation`, `parley-platform`, `parley-app`
- MongoDB connection string (Atlas free tier is fine for dev) stored in Key Vault before the app apply

Register Container Apps once per subscription:

```bash
az provider register -n Microsoft.App --wait
```

## 1. Configure tfvars (non-secrets)

```bash
cp parley-infra/environments/dev.tfvars.example parley-infra/environments/dev.tfvars
# Edit subscription_id (and optional scaling overrides)
```

Cheap dev defaults are already in the example: `min_replicas = 0`, `log_analytics_daily_quota_gb = 0.5`.

## 2. HCP workspace variables (secrets — never in git)

Set on the **`parley-app`** workspace:

| Variable | Description |
|---|---|
| `twilio_account_sid` | Twilio Account SID (sensitive) |
| `openai_key_secret_id` | Key Vault versionless URI for Azure OpenAI key |
| `twilio_token_secret_id` | Key Vault versionless URI for Twilio auth token |
| `mongodb_uri_secret_id` | Key Vault versionless URI for MongoDB connection string |

Create the Key Vault secrets **after** the platform layer applies (vault URI is in platform outputs):

```bash
cd parley-infra/platform
KV_URI=$(terraform output -raw key_vault_uri)
KV_NAME="${KV_URI#https://}"
KV_NAME="${KV_NAME%%.vault.azure.net/}"

az keyvault secret set --vault-name "$KV_NAME" --name openai-key --value "<azure-openai-key>"
az keyvault secret set --vault-name "$KV_NAME" --name twilio-auth-token --value "<twilio-auth-token>"
az keyvault secret set --vault-name "$KV_NAME" --name mongodb-uri --value "<mongodb-connection-string>"
```

Use the versionless secret IDs Terraform expects, e.g.:

`https://parley-dev-kv.vault.azure.net/secrets/openai-key`

## 3. Apply Terraform (foundation → platform → app)

```bash
cd parley-infra/foundation && terraform init && terraform apply -var-file=../environments/dev.tfvars
cd ../platform   && terraform init && terraform apply -var-file=../environments/dev.tfvars
cd ../app        && terraform init && terraform apply -var-file=../environments/dev.tfvars
```

Capture the public URL:

```bash
cd parley-infra/app
terraform output -raw app_url
```

## 4. Build and deploy the container image

From the repo root (requires platform applied for ACR outputs):

```bash
chmod +x scripts/deploy-parley-azure.sh
./scripts/deploy-parley-azure.sh latest
```

The script:

1. Reads ACR login server from `parley-infra/platform` outputs
2. Runs `./gradlew bootBuildImage`
3. Pushes to ACR
4. Re-applies the `app` layer with the new `image_tag`

**Note:** With `min_replicas = 0`, the first request after idle triggers a cold start (JVM + Spring). Twilio may retry; for prod, consider `min_replicas = 1`.

## 5. Verify deployment

```bash
export APP_URL=$(cd parley-infra/app && terraform output -raw app_url)
./scripts/verify-azure-deployment.sh "$APP_URL"
```

Expect `/health` → `{"status":"ok"}` and `POST /voice` → valid TwiML.

## 6. Cut Twilio over (retire ngrok)

```bash
export PUBLIC_BASE_URL="$APP_URL"
export TWILIO_ACCOUNT_SID=...
export TWILIO_AUTH_TOKEN=...
export TWILIO_FROM_NUMBER=...
./scripts/repoint-twilio-voice-webhook.sh
```

Stop ngrok — Twilio now hits the stable Azure URL.

## Spring profiles

| Profile | Where | LLM |
|---|---|---|
| `local` | `./gradlew bootRun` + `.env` | OpenAI direct (`OPENAI_API_KEY`) |
| `azure` | Container Apps (`SPRING_PROFILES_ACTIVE=azure`) | Same OpenAI starter, `base-url` + deployment name from Azure OpenAI env vars |

## Troubleshooting

| Symptom | Check |
|---|---|
| Container App won't start | `az containerapp logs show` — missing Key Vault secret or bad Mongo URI |
| `/voice` 502/504 on first call | Cold start; wait and retry, or set `min_replicas = 1` |
| LLM errors in Azure | Key Vault `openai-key`, deployment name `gpt-4o-mini`, managed identity OpenAI role |
| Transcripts not saving | Key Vault `mongodb-uri` secret and network access from Container Apps egress |
