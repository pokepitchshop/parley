# Deploy Parley to Azure Container Apps (POK-25)

Host Parley on Azure Container Apps with **scale-to-zero** so idle cost is ~$0 compute.
This retires ngrok ([POK-10](https://linear.app/pokepitchshop/issue/POK-10)) and gives Twilio a stable HTTPS URL ([POK-11](https://linear.app/pokepitchshop/issue/POK-11)).

For Terraform layer details and cost discipline, see [`parley-infra/README.md`](../parley-infra/README.md).

## Prerequisites

- Azure subscription with `az login` (or HCP Terraform Azure OIDC configured)
- Run `./scripts/audit-azure-subscription.sh` first (POK-111) — see [`docs/azure-discovery-audit.md`](azure-discovery-audit.md)
- Run `./scripts/bootstrap-hcp-terraform.sh` (POK-112) — see [`docs/hcp-terraform-bootstrap.md`](hcp-terraform-bootstrap.md)
- Run `./scripts/validate-parley-infra.sh` (POK-113) before apply
- [Terraform CLI](https://developer.hashicorp.com/terraform/install) + HCP Terraform access to org `pokepitchshop`
- Docker (for `bootBuildImage` push to ACR)
- Three HCP workspaces: `parley-foundation`, `parley-platform`, `parley-app`
- MongoDB connection string (Atlas free tier is fine for dev) stored in Key Vault before the app apply

Register Container Apps once per subscription (or use `./scripts/bootstrap-hcp-terraform.sh` which registers all required providers):

```bash
./scripts/bootstrap-hcp-terraform.sh
```

## 1. Configure tfvars (non-secrets)

The bootstrap script creates `parley-infra/environments/dev.tfvars` from the example. To recreate manually:

```bash
cp parley-infra/environments/dev.tfvars.example parley-infra/environments/dev.tfvars
# Edit subscription_id (and optional scaling overrides)
```

Cheap dev defaults are already in the example: `min_replicas = 0`, `log_analytics_daily_quota_gb = 0.5`.

### MongoDB (Atlas)

Parley stores callers, transcripts, and summaries in MongoDB. **Azure Container Apps cannot use `localhost`** — use [MongoDB Atlas](https://www.mongodb.com/atlas) (free M0 tier is enough for dev).

1. Create a cluster (any cloud region; pick one close to `eastus2` if unsure).
2. **Database Access** → add a user with read/write on the `parley` database.
3. **Network Access** → for dev, add `0.0.0.0/0` (Container Apps use dynamic egress IPs).
4. **Connect** → **Drivers** → copy the `mongodb+srv://…` URI.
5. Replace `<password>` and set the database path to `/parley`:

```text
mongodb+srv://myuser:PASSWORD@cluster0.xxxxx.mongodb.net/parley?retryWrites=true&w=majority
```

6. Add to repo-root `.env` (never commit):

```bash
SPRING_DATA_MONGODB_URI=mongodb+srv://...
```

`./scripts/seed-parley-keyvault.sh` (or `./scripts/apply-parley-infra.sh`) copies this into Key Vault as `mongodb-uri`.

## 2. HCP workspace variables (secrets — never in git)

Set on the **`parley-app`** workspace:

| Variable | Description |
|---|---|
| `twilio_account_sid` | Twilio Account SID (sensitive) |
| `twilio_token_secret_id` | Key Vault versionless URI for Twilio auth token |
| `mongodb_uri_secret_id` | Key Vault versionless URI for MongoDB connection string |

Create the Key Vault secrets **after** the platform layer applies (vault URI is in platform outputs):

```bash
cd parley-infra/platform
KV_URI=$(terraform output -raw key_vault_uri)
KV_NAME="${KV_URI#https://}"
KV_NAME="${KV_NAME%%.vault.azure.net/}"

./scripts/seed-parley-keyvault.sh
```

Or manually:

```bash
az keyvault secret set --vault-name "$KV_NAME" --name twilio-auth-token --value "<twilio-auth-token>"
az keyvault secret set --vault-name "$KV_NAME" --name mongodb-uri --value "<mongodb-connection-string>"
```

Use the versionless secret IDs Terraform expects, e.g.:

`https://parley-dev-kv.vault.azure.net/secrets/twilio-auth-token`

Azure OpenAI uses **keyless** managed-identity auth — no `openai-key` secret or HCP variable.

## 3. Apply Terraform (foundation → platform → app)

```bash
chmod +x scripts/apply-parley-infra.sh scripts/seed-parley-keyvault.sh
PARLEY_TF_AUTO_APPROVE=1 ./scripts/apply-parley-infra.sh
```

The script validates (POK-113), applies foundation and platform, seeds Key Vault from `.env`, exports `TF_VAR_*` for the app layer, then applies app.

If foundation and platform are already applied (POK-114 partial), apply the app layer only:

```bash
chmod +x scripts/apply-parley-app.sh
PARLEY_TF_AUTO_APPROVE=1 ./scripts/apply-parley-app.sh
```

Manual steps (equivalent):

```bash
cd parley-infra/foundation && terraform init && terraform apply -var-file=../environments/dev.tfvars
cd ../platform   && terraform init && terraform apply -var-file=../environments/dev.tfvars
./scripts/seed-parley-keyvault.sh
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

See [llm-provider.md](llm-provider.md) for the full decision (POK-110).

| Profile | Where | LLM |
|---|---|---|
| `local` | `./gradlew bootRun` + `.env` | Azure OpenAI — KEY 1 + endpoint in `.env` |
| `azure` | Container Apps (`SPRING_PROFILES_ACTIVE=azure`) | Azure OpenAI — keyless managed identity |

## Troubleshooting

| Symptom | Check |
|---|---|
| Container App won't start | `az containerapp logs show` — missing Key Vault secret or bad Mongo URI |
| App apply: KV secret fetch failed | Run `./scripts/seed-parley-keyvault.sh` — needs **Key Vault Secrets Officer** on `parley-dev-kv`; then `./scripts/verify-parley-infra-ready.sh` |
| `.env` parse error in scripts | MongoDB URI contains `&` — scripts use `scripts/lib/load-dotenv.sh` (safe loader) |
| `/voice` 502/504 on first call | Cold start; wait and retry, or set `min_replicas = 1` |
| LLM errors in Azure | Managed identity **Cognitive Services OpenAI User** role, `AZURE_CLIENT_ID` set, deployment `gpt-4.1-mini` — see [llm-provider.md](llm-provider.md) |
| Transcripts not saving | Key Vault `mongodb-uri` secret and network access from Container Apps egress |
