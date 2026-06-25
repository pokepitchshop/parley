# ConversationRelay voice (POK-28)

Real-time voice via Twilio **ConversationRelay**: Twilio opens a WebSocket to Parley, streams caller speech as JSON `prompt` messages, and speaks back `text` tokens from the LLM.

Turn-based `/voice` (Gather + Say) remains for fallback; production calls should use **`/voice/relay`**.

## Architecture

```text
Inbound call
  → Twilio POST /voice/relay (TwiML webhook)
  → Parley returns <Connect><ConversationRelay url="wss://…/relay" …/></Connect>
  → Twilio WSS → /relay (ConversationRelayHandler)
  → VoiceReplyService.streamReplyToUtterance (streaming ChatClient + guardrails)
  → sentence-chunked `text` tokens back to Twilio → TTS starts early
```

## Required configuration

### `PUBLIC_BASE_URL` (critical)

Parley uses `PUBLIC_BASE_URL` → `parley.public-url.base` for:

1. **ConversationRelay TwiML** — builds `wss://<host>/relay` in `<ConversationRelay url="…"/>`
2. **Twilio signature validation** — signs/validates against the public HTTPS URL (not internal Container Apps hostnames)

| Rule | Example |
|------|---------|
| Base URL only | `https://parley-dev-app….azurecontainerapps.io` |
| **No** `/voice` suffix | ~~`…/voice`~~ breaks signatures and WSS URL construction |
| **No** trailing slash | Strip `/` at end |

**Azure:** Terraform sets this automatically in `parley-infra/app/main.tf`:

```hcl
env {
  name  = "PUBLIC_BASE_URL"
  value = "https://${azurerm_container_app.parley.ingress[0].fqdn}"
}
```

After `./scripts/deploy-parley-azure.sh`, confirm:

```bash
az containerapp show \
  --name parley-dev-app \
  --resource-group parley-dev-rg \
  --query "properties.template.containers[0].env[?name=='PUBLIC_BASE_URL']" \
  -o json
```

**Local:** set in repo-root `.env` to your ngrok HTTPS URL (no `/voice` suffix).

### Twilio voice webhook

Point the number at **relay**, not turn-based `/voice`:

| Setting | Value |
|---------|--------|
| A call comes in | Webhook |
| URL | `{app_url}/voice/relay` |
| Method | POST |
| Status callback | `{app_url}/voice/status` (POST) |
| SIP trunk | **cleared** (`trunk_sid` empty) |

```bash
# Fix .env: PUBLIC_BASE_URL=<app_url>  (no /voice suffix)
./scripts/repoint-twilio-voice-webhook.sh   # still sets /voice — override in Console for relay
```

For ConversationRelay, set the webhook URL manually in [Twilio Console](https://console.twilio.com) → Phone Numbers → `{number}` → **Voice** → `{app_url}/voice/relay`.

### Other secrets (unchanged)

- `TWILIO_AUTH_TOKEN` — Key Vault → Container App (enables signature validation on `/voice/**` and WSS upgrade)
- Azure OpenAI + Mongo — see [azure-deploy.md](azure-deploy.md)

## Verify before a live call

### 1. Health

```bash
APP_URL=$(cd parley-infra/app && terraform output -raw app_url)
curl -sf "$APP_URL/health"   # {"status":"ok"}
```

### 2. Unsigned curl → 403 (expected)

Signature validation is on when `TWILIO_AUTH_TOKEN` is set:

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST "$APP_URL/voice/relay"
# 403
```

### 3. Signed curl → ConversationRelay TwiML

```bash
source scripts/lib/load-dotenv.sh && load_dotenv .env
URL="${APP_URL}/voice/relay"
SIG=$(python3 -c "
import base64, hashlib, hmac, os
t = os.environ['TWILIO_AUTH_TOKEN']
u = '$URL'
print(base64.b64encode(hmac.new(t.encode(), u.encode(), hashlib.sha1).digest()).decode())
")
curl -s -X POST "$URL" -H "X-Twilio-Signature: $SIG"
```

**Pass:** XML containing `<ConversationRelay url="wss://…/relay" welcomeGreeting="…"/>`.

**Fail:** see [Troubleshooting](#troubleshooting) below.

### 4. Live call

1. Dial the Twilio number.
2. Hear welcome greeting (from TwiML `welcomeGreeting`).
3. Ask a shop question — expect a **real LLM answer** (not “You said: …”).
4. Interrupt mid-sentence — expect agent to stop; new question gets a fresh answer.

Watch logs:

```bash
az containerapp logs show \
  --name parley-dev-app \
  --resource-group parley-dev-rg \
  --follow
```

Look for: `ConversationRelay setup`, `prompt`, `interrupt`, `relay.turn.latency`, and no `IllegalStateException` for `PUBLIC_BASE_URL`.

## Turn latency logs (POK-30)

Each completed relay turn emits a structured log line:

```text
relay.turn.latency callSid=CA… turnId=1 sttMs=180 llmFirstTokenMs=420 llmCompleteMs=980 ttsHandoffMs=450 interrupted=false
```

| Field | Meaning |
|-------|---------|
| `sttMs` | Partial prompt → final prompt (Twilio STT); `null` when only a final prompt arrived |
| `llmFirstTokenMs` | Final prompt → first LLM token |
| `llmCompleteMs` | Final prompt → LLM stream finished |
| `ttsHandoffMs` | Final prompt → first `text` chunk sent to Twilio (proxy for TTS start; Twilio synthesizes after handoff) |
| `interrupted` | `true` when the caller barged in before the turn finished |

Barge-in: Twilio sends `interrupt`, Parley bumps the generation counter (stops in-flight LLM + further `text` sends), then handles the next `prompt`.

Query in Azure Log Analytics:

```kusto
ContainerAppConsoleLogs_CL
| where Log_s contains "relay.turn.latency"
| project TimeGenerated, Log_s
| order by TimeGenerated desc
```

Compare with Twilio Voice Insights **Conversation Relay Call Summary** (STT / Application / TTS buckets) when tuning.

## Troubleshooting

### “An application error has occurred” (Twilio)

Twilio [error 11217](https://www.twilio.com/docs/api/errors/11217): your webhook returned **4xx/5xx**.

| HTTP | Cause | Fix |
|------|--------|-----|
| **500** on `POST /voice/relay` | **`PUBLIC_BASE_URL` missing** — `relayWebSocketUrl()` throws before TwiML is returned | Set env var (see above); re-run `apply-parley-app` after Terraform fix |
| **403** | Signature validation failed | Fix `PUBLIC_BASE_URL` (no `/voice` suffix); ensure Key Vault Twilio token matches Twilio Console |
| **404** | Old image / wrong path | Deploy latest; webhook must be `/voice/relay` |
| **502/504** | Cold start | Wait and retry; or `min_replicas = 1` |

**Diagnose:**

```bash
# Azure logs (look for IllegalStateException: PUBLIC_BASE_URL)
az containerapp logs show --name parley-dev-app --resource-group parley-dev-rg --tail 50

# Twilio Debugger
# https://console.twilio.com/us1/monitor/debugger
```

**Known incident (2026-06-23):** `./scripts/deploy-parley-azure.sh` re-applied Terraform without `PUBLIC_BASE_URL` in `main.tf`, wiping a manual `az containerapp update`. Symptom: signed `POST /voice/relay` returned **500** with:

```text
IllegalStateException: parley.public-url.base (PUBLIC_BASE_URL) must be set
  to build the ConversationRelay WebSocket URL
```

**Fix applied:** `PUBLIC_BASE_URL` added to `parley-infra/app/main.tf` so deploys persist it.

**One-off recovery** (if Terraform not yet applied):

```bash
APP_URL=$(cd parley-infra/app && terraform output -raw app_url)
az containerapp update \
  --name parley-dev-app \
  --resource-group parley-dev-rg \
  --set-env-vars "PUBLIC_BASE_URL=${APP_URL}"
```

### Greeting works, then silence

- WebSocket `/relay` failed (signature, ingress, or crash) — check Container App logs and Twilio Debugger for WebSocket errors (e.g. [64105](https://www.twilio.com/docs/api/errors/64105)).
- LLM error after connect — Azure OpenAI / Mongo config (separate from webhook 500).

### Still hearing echo bot (“You said: …”)

Old container image — redeploy after merging LLM wiring (PR #24):

```bash
./scripts/deploy-parley-azure.sh latest
```

## Deploy checklist

- [ ] `PUBLIC_BASE_URL` present on Container App (Terraform or `az containerapp show`)
- [ ] Latest image deployed (`./scripts/deploy-parley-azure.sh`)
- [ ] Twilio webhook → `{app_url}/voice/relay` (POST)
- [ ] Signed curl returns `<ConversationRelay …/>` TwiML
- [ ] Live call: LLM answer + interrupt behavior OK

## Related docs

- [azure-deploy.md](azure-deploy.md) — Container Apps deploy runbook
- [twilio-public-url.md](twilio-public-url.md) — ngrok local dev, signature notes
- [e2e-test-call.md](e2e-test-call.md) — turn-based `/voice` acceptance (legacy path)
