# parley-infra

Terraform for standing up **Parley** on Azure, organized by lifecycle layer.
State + runs live in HCP Terraform (org: `pokepitchshop`).

## Layers
- `foundation/` — resource group + the managed identity Parley runs as. Rarely changes.
- `platform/`   — ACR, Key Vault, Azure OpenAI (gpt-4.1-mini), Container Apps environment.
- `app/`        — the Parley Container App (ingress, identity, env/secrets).

Each layer is its own state (its own HCP workspace). The `app` layer reads `platform`/`foundation`
outputs via `terraform_remote_state`. Environments (dev/staging/prod) are the **same code** applied
to different workspaces with different values — not separate folders.

## Current Azure state (as of 2026-06-23 — POK-118)

**Parley dev stack: applied and live.** Twilio voice webhook points at Container Apps (POK-94); ngrok is **local dev only** ([`docs/twilio-public-url.md`](../docs/twilio-public-url.md)).

Re-run the live audit anytime: `./scripts/audit-azure-subscription.sh` — full report in [`docs/azure-discovery-audit.md`](../docs/azure-discovery-audit.md).

| Field | Value |
|---|---|
| **Applied** | 2026-06-23 (foundation → platform → app; POK-114) |
| **Resource group** | `parley-dev-rg` (`eastus2`) |
| **`app_url`** | `https://parley-dev-app.agreeablesmoke-7ebf43ac.eastus2.azurecontainerapps.io` |
| **Voice webhook** | `{app_url}/voice` (POST) |
| **Active revision** | `parley-dev-app--0000003` (Healthy) |

### Parley resources (`parley-dev-rg`, `eastus2`)

| Live resource | Type | Layer | Status |
|---|---|---|---|
| `parley-dev-rg` | Resource group | foundation | Applied |
| `parley-dev-id` | User-assigned identity | foundation | Applied |
| `parleydevacr` | Container Registry (Basic) | platform | Applied |
| `parley-dev-kv` | Key Vault (Standard, RBAC) | platform | Applied |
| `parley-dev-openai` | Azure OpenAI (gpt-4.1-mini) | platform | Applied |
| `parley-dev-logs` | Log Analytics (0.5 GB/day cap) | platform | Applied |
| `parley-dev-cae` | Container Apps Environment | platform | Applied |
| `parley-dev-app` | Container App (0.5 vCPU / 1Gi) | app | Applied |

### Legacy Oldman (`PokePitchShop`, `centralus`) — retired from production

Superseded by Parley on Azure. Resources **still present** pending teardown ([POK-117](https://linear.app/pokepitchshop/issue/POK-117)); do not route traffic here.

| Live resource | Type | Status |
|---|---|---|
| `oldman` | Linux App Service (F1) | Retired — superseded by `parley-dev-app` |
| `plan-oldman` | App Service plan (F1) | Retired |
| `identity-oldman` | User-assigned identity | Retired |
| `pokepitchshophub` | Event Hubs (Standard) | Retired — delete in POK-117 |
| `pokepitchstorage` | Storage account (LRS) | Retired — delete in POK-117 |
| `appinsights-oldman` / `ws-*` | App Insights + Log Analytics | Retired — delete or archive in POK-117 |
| `intelligentoldmanbrain` | PostgreSQL Flexible | Already removed |

**Bootstrap complete (POK-112).** HCP workspaces `parley-foundation`, `parley-platform`, `parley-app` are in use. Providers registered in `eastus2`.

## Cost model (cheap-by-default)

| Resource | Tier in code | Idle cost |
|---|---|---|
| Container App | 0.5 vCPU / 1Gi, `min_replicas = 0` (dev) | ~$0 compute when scaled to zero |
| Container Apps Environment | Consumption (no dedicated profile) | No base fee |
| ACR | Basic | ~$5/mo |
| Key Vault | Standard, RBAC | ~$0 at this volume |
| Azure OpenAI | gpt-4.1-mini (`Standard` SKU in dev), pay-per-token | ~$0 idle; `openai_capacity` is TPM cap only. gpt-4o-mini 2024-07-18 deprecated in eastus2. |
| Log Analytics | PerGB2018, 0.5 GB/day quota, 30-day retention | Capped ingestion |

Set `min_replicas = 1` only in prod if cold-start latency exceeds Twilio's webhook timeout.

## One-time bootstrap (POK-112)

Run the bootstrap script (registers Azure providers, creates `dev.tfvars`, verifies `terraform init`):

```bash
./scripts/bootstrap-hcp-terraform.sh
```

Manual checklist and HCP workspace details: [`docs/hcp-terraform-bootstrap.md`](../docs/hcp-terraform-bootstrap.md).

1. Have an Azure subscription and run `az login`.
2. Register providers (script does this) — `Microsoft.App`, `Microsoft.ContainerRegistry`, `Microsoft.KeyVault`, `Microsoft.CognitiveServices`.
3. Run `terraform login` and set up Azure auth on HCP workspaces (OIDC preferred; `ARM_*` OK for dev).
4. In HCP Terraform (`pokepitchshop`) create three workspaces: `parley-foundation`, `parley-platform`, `parley-app`.
5. Put secrets (TWILIO_*, Azure OpenAI key, MongoDB URI) as **workspace variables** on `parley-app` — never in git.
   Store secret *values* in Key Vault after platform apply; reference versionless secret IDs in workspace vars.
6. `dev.tfvars` is created from `dev.tfvars.example` by the bootstrap script (gitignored).

## Apply order (foundation → platform → app)

Validate: `./scripts/validate-parley-infra.sh`

From the repo root (recommended — seeds Key Vault, pushes image, wires TF vars):

```bash
./scripts/apply-parley-infra.sh          # foundation + platform (once)
./scripts/seed-parley-keyvault.sh        # after platform
./scripts/push-parley-image.sh latest    # ACR build (linux/amd64)
./scripts/apply-parley-app.sh            # app layer
```

Manual layer apply:

```
cd foundation && terraform init && terraform apply -var-file=../environments/dev.tfvars
cd ../platform && terraform init && terraform apply -var-file=../environments/dev.tfvars
cd ../app      && terraform init && terraform apply -var-file=../environments/dev.tfvars
```

Full walkthrough: [`docs/azure-deploy.md`](../docs/azure-deploy.md).

## After the app applies

```bash
cd parley-infra/app && terraform output -raw app_url
```

Point Twilio's voice webhook to `<app_url>/voice` (POST):

```bash
./scripts/repoint-twilio-voice-webhook.sh "$(terraform output -raw app_url)"
```

Production traffic uses the stable Azure URL above. ngrok is for **local dev only** — see [`docs/twilio-public-url.md`](../docs/twilio-public-url.md).

## Deploy / roll the app image (from the parley/ repo)

```bash
./scripts/push-parley-image.sh latest    # default: az acr build (native linux/amd64)
./scripts/deploy-parley-azure.sh latest  # push + apply app layer
```

Or app layer only after a new image tag:

```bash
PARLEY_TF_AUTO_APPROVE=1 ./scripts/apply-parley-app.sh
```

## Spring AI

Decision and auth: [`docs/llm-provider.md`](../docs/llm-provider.md) (POK-110).

Single OpenAI model modules; Azure OpenAI via Microsoft Foundry (Spring AI 2.0 — no separate azure artifact):

```gradle
implementation 'org.springframework.ai:spring-ai-openai'
implementation 'org.springframework.ai:spring-ai-autoconfigure-model-openai'
implementation 'org.springframework.ai:spring-ai-client-chat'
implementation 'org.springframework.ai:spring-ai-autoconfigure-model-chat-client'
implementation 'org.springframework.ai:spring-ai-autoconfigure-model-chat-memory'
implementation 'com.azure:azure-identity'
```

- **Local:** `AZURE_OPENAI_API_KEY` + endpoint from portal
- **Azure:** keyless managed identity (`AZURE_CLIENT_ID` + platform RBAC)

## Notes / TODO
- Region defaults to `eastus2` (Azure OpenAI availability). Confirm model + version in your region.
- Verify the current model version in `platform/variables.tf` (`az cognitiveservices account list-models`).
- Starter template: run `terraform fmt` and `terraform validate` in each layer before applying.
- For staging/prod, create matching HCP workspaces and apply with `staging.tfvars` / `prod.tfvars`.
