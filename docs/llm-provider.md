# LLM provider (POK-110)

Parley uses **Azure OpenAI only** (Microsoft Foundry). There is no OpenAI.com (`sk-…`) path.

## Auth by environment

| Environment | Profile | Auth |
|---|---|---|
| Local (`./gradlew bootRun`) | `local` (default) | `AZURE_OPENAI_API_KEY` + endpoint in `.env` (KEY 1 from portal) |
| Azure Container Apps | `azure` | **Keyless** — user-assigned managed identity + `Cognitive Services OpenAI User` RBAC |

## Spring AI wiring (2.0)

Spring AI 2.0 has no separate `azure-openai` starter. Use the OpenAI model modules with **Microsoft Foundry** properties:

```gradle
implementation 'org.springframework.ai:spring-ai-openai'
implementation 'org.springframework.ai:spring-ai-autoconfigure-model-openai'
implementation 'org.springframework.ai:spring-ai-client-chat'
implementation 'org.springframework.ai:spring-ai-autoconfigure-model-chat-client'
implementation 'org.springframework.ai:spring-ai-autoconfigure-model-chat-memory'
implementation 'com.azure:azure-identity'  // DefaultAzureCredential for keyless on Azure
```

| Profile | Config | Properties |
|---|---|---|
| `local` | `application-local.properties` | `base-url`, `microsoft-foundry=true`, `api-key`, `chat.microsoft-deployment-name` |
| `azure` | `application-azure.properties` | same endpoint/deployment — **no api-key** (managed identity) |

Container Apps set `SPRING_PROFILES_ACTIVE=azure`, `AZURE_CLIENT_ID`, and `SPRING_AI_OPENAI_*` env vars. See `parley-infra/app/main.tf`.

## Local dev checklist

1. Copy `.env.example` → `.env`
2. Portal → **parley-dev-openai** → **Keys and Endpoint** → set `AZURE_OPENAI_API_KEY` and `SPRING_AI_OPENAI_BASE_URL`
3. `./gradlew bootRun`
4. `./scripts/verify-voice-preflight.sh`

## Azure troubleshooting

| Symptom | Check |
|---|---|
| LLM 401/403 locally | `AZURE_OPENAI_API_KEY` is KEY 1 from Azure (not an OpenAI.com `sk-…`) |
| LLM 401/403 on Container App | Managed identity has **Cognitive Services OpenAI User**; `AZURE_CLIENT_ID` set |
| Wrong model | `SPRING_AI_OPENAI_CHAT_MICROSOFT_DEPLOYMENT_NAME=gpt-4.1-mini` |
