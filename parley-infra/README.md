# parley-infra

Terraform for standing up **Parley** on Azure, organized by lifecycle layer.
State + runs live in HCP Terraform (org: `pokepitchshop-org`).

## Layers
- `foundation/` — resource group + the managed identity Parley runs as. Rarely changes.
- `platform/`   — ACR, Key Vault, Azure OpenAI (gpt-4o-mini), Container Apps environment.
- `app/`        — the Parley Container App (ingress, identity, env/secrets).

Each layer is its own state (its own HCP workspace). The `app` layer reads `platform`/`foundation`
outputs via `terraform_remote_state`. Environments (dev/staging/prod) are the **same code** applied
to different workspaces with different values — not separate folders.

## Current Azure vs parley-infra (as of subscription audit)

**Parley has not been applied yet.** No `parley-*` resource groups exist. The subscription
currently runs legacy **Oldman** workloads in the `PokePitchShop` resource group (`centralus`):

| Live resource | Type | SKU / notes | parley-infra equivalent |
|---|---|---|---|
| *(none yet)* | Resource group | — | `foundation/` → `parley-dev-rg` |
| `identity-oldman` | User-assigned identity | — | `foundation/` → `parley-dev-id` |
| `oldman` | Linux App Service | F1 Free plan | **Replaced by** `app/` Container App |
| `plan-oldman` | App Service plan | F1 Free | **Not used** — ACA consumption billing |
| `intelligentoldmanbrain` | PostgreSQL Flexible | B1ms burstable | **Not in Parley** (no DB in v1) |
| `pokepitchshophub` | Event Hubs | Standard (~$13/mo MTD) | **Not in Parley** |
| `pokepitchstorage` | Storage account | Standard LRS | **Not in Parley** |
| `ws-*` + `appinsights-oldman` | Log Analytics + App Insights | — | `platform/` Log Analytics only |
| *(not deployed)* | ACR, Key Vault, Azure OpenAI, Container Apps | — | `platform/` + `app/` |

Parley is a **greenfield stack** in `eastus2` (OpenAI availability), separate from Oldman.
Before first apply, register Container Apps: `az provider register -n Microsoft.App --wait`.

## Cost model (cheap-by-default)

| Resource | Tier in code | Idle cost |
|---|---|---|
| Container App | 0.5 vCPU / 1Gi, `min_replicas = 0` (dev) | ~$0 compute when scaled to zero |
| Container Apps Environment | Consumption (no dedicated profile) | No base fee |
| ACR | Basic | ~$5/mo |
| Key Vault | Standard, RBAC | ~$0 at this volume |
| Azure OpenAI | gpt-4o-mini, pay-per-token | ~$0 idle; `openai_capacity` is TPM cap only |
| Log Analytics | PerGB2018, 0.5 GB/day quota, 30-day retention | Capped ingestion |

Set `min_replicas = 1` only in prod if cold-start latency exceeds Twilio's webhook timeout.

## One-time bootstrap
1. Have an Azure subscription and run `az login`.
2. Register providers: `az provider register -n Microsoft.App --wait`.
3. Set up Terraform -> Azure auth (an OIDC service principal, or ARM_* creds on the HCP workspaces).
4. In HCP Terraform (`pokepitchshop-org`) create three workspaces: `parley-foundation`, `parley-platform`, `parley-app`.
5. Put secrets (TWILIO_*, Azure OpenAI key, MongoDB URI) as **workspace variables** on `parley-app` — never in git.
   Store secret *values* in Key Vault after platform apply; reference versionless secret IDs in workspace vars.
6. Copy `environments/dev.tfvars.example` -> `dev.tfvars` and fill in `subscription_id`.

## Apply order (foundation -> platform -> app)
```
cd foundation && terraform init && terraform apply -var-file=../environments/dev.tfvars
cd ../platform && terraform init && terraform apply -var-file=../environments/dev.tfvars
cd ../app      && terraform init && terraform apply -var-file=../environments/dev.tfvars
```

## After the app applies
`terraform output app_url` gives the public HTTPS base. Point Twilio's voice webhook to
`<app_url>/voice` — that's POK-11, and it retires the ngrok step (POK-10).

Step-by-step apply, image deploy, and Twilio cutover: [`docs/azure-deploy.md`](../docs/azure-deploy.md).

## Deploy the app image (from the parley/ repo)
```
./gradlew bootBuildImage --imageName=<acr_login_server>/parley:latest
az acr login --name <acr-name>
docker push <acr_login_server>/parley:latest
```
Then bump `var.image_tag` (or re-apply `app`) to roll a new revision.

## Spring AI
Spring AI 2.0 uses the **OpenAI starter** for both local dev and Azure-hosted models. Locally,
set `OPENAI_API_KEY`; on Container Apps the `azure` profile points the same client at Azure OpenAI
via `SPRING_AI_AZURE_OPENAI_*` env vars (relaxed binding → `spring.ai.openai.*` in
`application-azure.properties`). Keyless via managed identity is the better end state.

## Notes / TODO
- Region defaults to `eastus2` (Azure OpenAI availability). Confirm model + version in your region.
- Verify the current `gpt-4o-mini` version in `platform/variables.tf`.
- Starter template: run `terraform fmt` and `terraform validate` in each layer before applying.
- For staging/prod, create matching HCP workspaces and apply with `staging.tfvars` / `prod.tfvars`.
