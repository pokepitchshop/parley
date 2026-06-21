# HCP Terraform bootstrap (POK-112)

One-time setup before applying `parley-infra`. Automates Azure provider registration and local `dev.tfvars`; HCP workspace creation is done in the Terraform Cloud UI (or API).

```bash
chmod +x scripts/bootstrap-hcp-terraform.sh
./scripts/bootstrap-hcp-terraform.sh
```

## 1. Azure CLI

```bash
az login
./scripts/audit-azure-subscription.sh   # POK-111 — confirm greenfield + subscription
```

The bootstrap script registers these providers if needed:

- `Microsoft.App` (Container Apps)
- `Microsoft.ContainerRegistry` (ACR)
- `Microsoft.KeyVault`
- `Microsoft.CognitiveServices` (Azure OpenAI)

## 2. Local tfvars

Creates `parley-infra/environments/dev.tfvars` from the example (gitignored) with your active `subscription_id`. Non-secret only — never put Twilio keys or Mongo URI here.

## 3. HCP Terraform login

```bash
terraform login
```

Opens a browser to generate a token for `app.terraform.io`. Token is stored in `~/.terraform.d/credentials.tfrc.json`.

You need access to org **`pokepitchshop`**.

## 4. Create three workspaces (HCP UI)

In [HCP Terraform](https://app.terraform.io) → org `pokepitchshop` → **New workspace** → **Version control workflow** or **CLI-driven workflow**:

| Workspace name | Terraform directory | `providers.tf` cloud block |
|---|---|---|
| `parley-foundation` | `parley-infra/foundation/` | `workspaces { name = "parley-foundation" }` |
| `parley-platform` | `parley-infra/platform/` | `workspaces { name = "parley-platform" }` |
| `parley-app` | `parley-infra/app/` | `workspaces { name = "parley-app" }` |

**CLI-driven workflow** is simplest for dev: runs are triggered from your machine via `terraform apply` after `terraform init`.

For each workspace:

1. **Execution mode:** CLI (local) or Remote — either works; repo uses HCP for state.
2. **Azure authentication** (pick one):
   - **Recommended:** Dynamic provider credentials (OIDC) — [Azure OIDC for HCP Terraform](https://developer.hashicorp.com/terraform/cloud-docs/workspaces/dynamic-provider-credentials/azure-configuration)
   - **Dev shortcut:** Environment variables on the workspace (or export locally before apply):
     - `ARM_CLIENT_ID`, `ARM_CLIENT_SECRET`, `ARM_TENANT_ID`, `ARM_SUBSCRIPTION_ID`
     - Or run applies locally with `az login` and user credentials (Terraform Azure provider uses Azure CLI auth when no SP env vars are set)

## 5. Workspace variables on `parley-app` (before app apply — POK-114)

Set in HCP UI → `parley-app` → Variables. Mark sensitive values as **Sensitive**.

| Variable | When | Example |
|---|---|---|
| `twilio_account_sid` | Before app apply | `AC...` |
| `openai_key_secret_id` | After platform apply + KV seed | `https://parley-dev-kv.vault.azure.net/secrets/openai-key` |
| `twilio_token_secret_id` | After platform apply + KV seed | `https://parley-dev-kv.vault.azure.net/secrets/twilio-auth-token` |
| `mongodb_uri_secret_id` | After platform apply + KV seed | `https://parley-dev-kv.vault.azure.net/secrets/mongodb-uri` |

Do **not** set these in git or `dev.tfvars`.

## 6. Verify init

Re-run the bootstrap script after steps 3–4:

```bash
./scripts/bootstrap-hcp-terraform.sh
```

Expect `terraform init` to succeed in all three layers.

## Next steps

| Issue | Action |
|---|---|
| [POK-113](https://linear.app/pokepitchshop/issue/POK-113) | `terraform fmt` + `validate` all layers |
| [POK-114](https://linear.app/pokepitchshop/issue/POK-114) | Apply foundation → platform → app |

See also [`docs/azure-deploy.md`](azure-deploy.md).
