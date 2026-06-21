# Azure subscription discovery audit (POK-111)

Pre-apply gap report comparing the live Azure subscription to [`parley-infra/`](../parley-infra/).
Re-run anytime with:

```bash
chmod +x scripts/audit-azure-subscription.sh
./scripts/audit-azure-subscription.sh
```

## Audit snapshot

| Field | Value |
|---|---|
| **Date** | 2026-06-21 |
| **Subscription** | Azure subscription 1 (`14868609-f4cf-481b-bd0a-133227ca13f9`) |
| **Matches `dev.tfvars.example`** | Yes |
| **Region target** | `eastus2` (greenfield Parley stack) |

## Parley footprint

**Status: absent (greenfield confirmed)**

Zero resources matching `parley-*` in name or resource group. No naming collisions for planned resources (`parleydevacr`, `parley-dev-kv`, `parley-dev-openai`, etc.).

## Legacy Oldman inventory (`PokePitchShop`, `centralus`)

| Resource | Type | SKU | Notes |
|---|---|---|---|
| `oldman` | App Service | F1 plan | Replaced by Container App |
| `plan-oldman` | App Service plan | F1 | Not used by Parley |
| `pokepitchshophub` | Event Hubs | Standard | ~$13/mo; retire in POK-115 |
| `pokepitchstorage` | Storage account | Standard_LRS | Retire in POK-115 |
| `identity-oldman` | Managed identity | — | Retire in POK-115 |
| `appinsights-oldman` | Application Insights | — | Retire or retain for logs |
| `ws-14868609-centralus` | Log Analytics | — | Retire or retain for logs |
| `eventhubs`, `office365` | Logic App connections | — | Review before delete |
| Application Insights Smart Detection | Action group | — | Auto-created |

**PostgreSQL `intelligentoldmanbrain`:** not found in subscription (already removed).

**Estimated Oldman baseline:** ~$13–15/mo (Event Hubs Standard is the main cost; App Service F1 is free tier).

## Provider registration

| Provider | State (2026-06-21 post-POK-112) | Required by |
|---|---|---|
| `Microsoft.App` | Registered | Container Apps |
| `Microsoft.ContainerRegistry` | Registered | ACR |
| `Microsoft.KeyVault` | Registered | Key Vault |
| `Microsoft.CognitiveServices` | Registering → re-run `./scripts/bootstrap-hcp-terraform.sh` | Azure OpenAI |
| `Microsoft.OperationalInsights` | Registered | Log Analytics |

Register any stragglers:

```bash
./scripts/bootstrap-hcp-terraform.sh
```

## OpenAI in eastus2

No existing Cognitive Services accounts in `eastus2`. Parley will create `parley-dev-openai` on platform apply.

## Blockers before POK-113 (validate)

1. **`terraform login`** + create HCP workspaces `parley-foundation`, `parley-platform`, `parley-app` — see [`docs/hcp-terraform-bootstrap.md`](hcp-terraform-bootstrap.md)
2. **`Microsoft.CognitiveServices`** — confirm Registered (bootstrap script)

## Next steps

| Issue | Action |
|---|---|
| [POK-112](https://linear.app/pokepitchshop/issue/POK-112) | Finish: `terraform login` + HCP workspaces; re-run bootstrap script |
| [POK-113](https://linear.app/pokepitchshop/issue/POK-113) | `terraform fmt` + `validate` all layers |
| [POK-114](https://linear.app/pokepitchshop/issue/POK-114) | `./scripts/apply-parley-infra.sh` |
