# LLM provider decision (POK-110)

Parley uses a **profile split**: different LLM backends for local dev vs Azure production, wired explicitly through Spring profiles — not left implicit in shared config.

## Decision

| Environment | Profile | Provider | Auth |
|---|---|---|---|
| Local (`./gradlew bootRun`) | `local` (default) | [OpenAI.com](https://platform.openai.com) | `OPENAI_API_KEY` in `.env` — an `sk-…` key from platform.openai.com |
| Azure Container Apps | `azure` | Azure OpenAI (Microsoft Foundry) | **Keyless** — user-assigned managed identity + `Cognitive Services OpenAI User` RBAC |

**Why not one provider everywhere?**

- **Local:** OpenAI.com is simplest for ngrok dev — one `sk-…` key, no Azure dependency on the hot path.
- **Prod:** Azure OpenAI keeps inference in-region (`eastus2`), aligns with `parley-infra` platform layer, and uses Entra token auth (no API key in Key Vault or env).

## Spring AI wiring (2.0)

Spring AI **2.0 removed** `spring-ai-starter-model-azure-openai`. Azure OpenAI uses the **same** OpenAI starter with **Microsoft Foundry** properties:

```gradle
implementation 'org.springframework.ai:spring-ai-starter-model-openai'
implementation 'com.azure:azure-identity'  // DefaultAzureCredential for keyless on Azure
```

| Profile | Config file | Properties |
|---|---|---|
| `local` | `application-local.properties` | `spring.ai.openai.api-key` + `chat.model` → api.openai.com |
| `azure` | `application-azure.properties` | `spring.ai.openai.base-url` + `microsoft-foundry=true` + `chat.microsoft-deployment-name` — **no api-key** |

Container Apps set `SPRING_PROFILES_ACTIVE=azure`, `AZURE_CLIENT_ID` (user-assigned identity), and relaxed-binding env vars matching the properties above. See `parley-infra/app/main.tf`.

## Key Vault secrets (app layer)

After platform apply, `./scripts/seed-parley-keyvault.sh` stores:

- **`twilio-auth-token`** — Twilio auth token from `.env`
- **`mongodb-uri`** — Atlas connection string from `.env` (`SPRING_DATA_MONGODB_URI`)

Azure OpenAI does **not** use a Key Vault API key — the app authenticates with its managed identity.

## Local dev checklist

1. Copy `.env.example` → `.env`
2. Set `OPENAI_API_KEY=sk-…` from [platform.openai.com/account/api-keys](https://platform.openai.com/account/api-keys)
3. `./gradlew bootRun` — Gradle loads `.env` into the bootRun process automatically
4. `./scripts/verify-voice-preflight.sh` — confirms key is set before a live call

## Azure troubleshooting

| Symptom | Check |
|---|---|
| LLM 401/403 on Container App | Managed identity has **Cognitive Services OpenAI User** on `parley-*-openai`; `AZURE_CLIENT_ID` matches foundation identity |
| Wrong model | `SPRING_AI_OPENAI_CHAT_MICROSOFT_DEPLOYMENT_NAME` matches Terraform deployment (`gpt-4.1-mini`) |
| Endpoint errors | `SPRING_AI_OPENAI_BASE_URL` from platform output; `SPRING_AI_OPENAI_MICROSOFT_FOUNDRY=true` |
