# LLM provider decision (POK-110)

Parley uses a **profile split**: different LLM backends for local dev vs Azure production, wired explicitly through Spring profiles — not left implicit in shared config.

## Decision

| Environment | Profile | Provider | Key source |
|---|---|---|---|
| Local (`./gradlew bootRun`) | `local` (default) | [OpenAI.com](https://platform.openai.com) | `OPENAI_API_KEY` in `.env` — an `sk-…` key from platform.openai.com |
| Azure Container Apps | `azure` | Azure OpenAI (Cognitive Services) | Key Vault secret `openai-key` — **Key 1** from the `parley-*-openai` resource (seeded by `./scripts/seed-parley-keyvault.sh`) |

**Why not one provider everywhere?**

- **Local:** OpenAI.com is simplest for ngrok dev — one `sk-…` key, no Azure dependency on the hot path.
- **Prod:** Azure OpenAI keeps inference in-region (`eastus2`), aligns with `parley-infra` platform layer, and supports keyless auth via managed identity (RBAC already granted; key in KV is the interim path).

**Why not drop Azure OpenAI from infra?** Container Apps already run on Azure; provisioning Azure OpenAI avoids shipping a platform.openai.com key into prod Key Vault and matches data-residency expectations as Parley scales.

## Spring AI wiring

Single starter for both profiles (Spring AI 2.0 pattern):

```gradle
implementation 'org.springframework.ai:spring-ai-starter-model-openai'
```

| Profile | Config file | Properties |
|---|---|---|
| `local` | `application-local.properties` | `spring.ai.openai.api-key=${OPENAI_API_KEY}` → api.openai.com |
| `azure` | `application-azure.properties` | `base-url` + `deployment-name` + API key from env → Azure OpenAI endpoint |

Container Apps set `SPRING_PROFILES_ACTIVE=azure` and inject `SPRING_AI_AZURE_OPENAI_*` (relaxed binding → `spring.ai.openai.*` in the azure profile file). See `parley-infra/app/main.tf`.

## Key Vault: `openai-key` is **not** an OpenAI.com key

After platform apply, `./scripts/seed-parley-keyvault.sh` stores:

- **`openai-key`** — Azure Cognitive Services **Key 1** for `parley-{env}-openai` (fetched via `az cognitiveservices account keys list`), **not** a platform.openai.com `sk-…` key.
- **`mongodb-uri`** — Atlas connection string from `.env` (`SPRING_DATA_MONGODB_URI`).
- **`twilio-auth-token`** — Twilio auth token from `.env`.

HCP workspace variable `openai_key_secret_id` on `parley-app` is the versionless URI, e.g. `https://parley-dev-kv.vault.azure.net/secrets/openai-key`.

## Local dev checklist

1. Copy `.env.example` → `.env`
2. Set `OPENAI_API_KEY=sk-…` from [platform.openai.com/account/api-keys](https://platform.openai.com/account/api-keys)
3. `./gradlew bootRun` — Gradle loads `.env` into the bootRun process automatically
4. `./scripts/verify-voice-preflight.sh` — confirms key is set before a live call

## Future: keyless on Azure

`parley-infra/platform` grants **Cognitive Services OpenAI User** to the app managed identity. The better end state is Entra token auth with no stored key; until then, Key Vault `openai-key` + `SPRING_AI_AZURE_OPENAI_API_KEY` works today.
