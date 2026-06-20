# Twilio public URL

Twilio voice webhooks must hit a **public HTTPS URL**.

| Environment | URL source | Guide |
|---|---|---|
| **Azure (stable)** | `terraform output app_url` from `parley-infra/app` | [docs/azure-deploy.md](azure-deploy.md) |
| **Local dev** | ngrok tunnel to `localhost:8080` | This doc (below) |

For production and to retire ngrok, deploy to Azure Container Apps first ([POK-25](https://linear.app/pokepitchshop/issue/POK-25)).

---

# Local dev (ngrok)

Twilio voice webhooks must hit a **public HTTPS URL**. For local development, tunnel Parley with [ngrok](https://ngrok.com/).

## Prerequisites

1. Parley running on port 8080:

   ```bash
   cp .env.example .env   # fill OPENAI_API_KEY, TWILIO_* as needed
   ./gradlew bootRun
   curl http://localhost:8080/health   # expect {"status":"ok"}
   ```

2. [ngrok](https://ngrok.com/download) installed and authenticated:

   ```bash
   ngrok config add-authtoken <your-token>
   ```

## Start the tunnel

In a second terminal:

```bash
ngrok http 8080
```

Copy the **HTTPS** forwarding URL from the ngrok dashboard or CLI output (for example `https://abc123.ngrok-free.app`).

Set it in `.env` so later issues (signature validation, Twilio console) can reference the same value:

```bash
PUBLIC_BASE_URL=https://abc123.ngrok-free.app
```

Free ngrok URLs change each time you restart the tunnel unless you use a reserved domain.

## Verify `/voice` over public HTTPS

Replace the host with your ngrok URL:

```bash
export PUBLIC_BASE_URL=https://abc123.ngrok-free.app
curl -X POST \
  -H "ngrok-skip-browser-warning: true" \
  "$PUBLIC_BASE_URL/voice"
```

Expect HTTP 200 and TwiML XML containing `<Response>`, `<Say>`, and `<Gather input="speech">`.

You can also probe the respond handler (requires `OPENAI_API_KEY` for a real LLM reply):

```bash
curl -X POST \
  -H "ngrok-skip-browser-warning: true" \
  -d "CallSid=CAtest&SpeechResult=hello" \
  "$PUBLIC_BASE_URL/voice/respond"
```

## Repoint Twilio from Retell to Parley (POK-11)

Previously the number routed to Retell via SIP domain `jmacretella.sip.twilio.com`. Cut over to Parley's webhook so inbound calls hit `/voice`.

### Before you cut over

1. Parley is running and reachable at `PUBLIC_BASE_URL` (ngrok or hosted `app_url`).
2. `.env` includes `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM_NUMBER`, and `PUBLIC_BASE_URL`.
3. Smoke-test the webhook Twilio will call:

   ```bash
   curl -X POST -H "ngrok-skip-browser-warning: true" "$PUBLIC_BASE_URL/voice"
   ```

### Option A: script (recommended)

```bash
chmod +x scripts/repoint-twilio-voice-webhook.sh
./scripts/repoint-twilio-voice-webhook.sh
```

The script clears any **Elastic SIP Trunk** association (`TrunkSid`), then sets **A call comes in → Webhook**, URL `{PUBLIC_BASE_URL}/voice`, method `POST`.

> **Important:** If the number still has a `trunk_sid` (e.g. Retell's `retellaTrunk`), Twilio **ignores** `voice_url` and routes calls to the trunk. Setting the webhook alone is not enough — the script must clear `trunk_sid`.

### Option B: Twilio Console

In the [Twilio Console](https://console.twilio.com/) → **Phone Numbers** → **Manage** → **Active numbers** → your number → **Voice configuration**:

| Field | Before (Retell) | After (Parley) |
|-------|-----------------|----------------|
| A call comes in | SIP / Trunk (e.g. `jmacretella.sip.twilio.com`) | **Webhook** |
| URL | *(SIP domain)* | `{PUBLIC_BASE_URL}/voice` |
| HTTP method | — | **POST** |

Save. Do not point the number back at the Retell SIP domain.

### Verify cutover

```bash
# Confirm voice_url and trunk_sid via API (requires .env)
source .env
curl -s -G "https://api.twilio.com/2010-04-01/Accounts/${TWILIO_ACCOUNT_SID}/IncomingPhoneNumbers.json" \
  --data-urlencode "PhoneNumber=${TWILIO_FROM_NUMBER}" \
  -u "${TWILIO_ACCOUNT_SID}:${TWILIO_AUTH_TOKEN}" \
  | python3 -c "import json,sys; n=json.load(sys.stdin)['incoming_phone_numbers'][0]; print('voice_url:', n.get('voice_url')); print('voice_method:', n.get('voice_method')); print('trunk_sid:', n.get('trunk_sid') or '<empty>')"
```

Expect `voice_url` → `{PUBLIC_BASE_URL}/voice`, `voice_method` → `POST`, and **`trunk_sid` empty**. Place a test call (POK-12) and confirm ngrok shows `POST /voice` from Twilio and you hear the Parley greeting, not Retell.

### Preflight before a live call (POK-86)

Run the automated checklist (health, ngrok URL match, Twilio config, `/voice` TwiML):

```bash
chmod +x scripts/verify-voice-preflight.sh
./scripts/verify-voice-preflight.sh
```

If ngrok restarted and the subdomain changed, update `PUBLIC_BASE_URL` in `.env` and re-run `./scripts/repoint-twilio-voice-webhook.sh`.

Then dial your Twilio number and watch the ngrok inspector at http://127.0.0.1:4040 for `POST /voice` and `POST /voice/respond`.

## Hosted path (later)

For Azure Container Apps, use `terraform output app_url` from [`parley-infra/`](../parley-infra/README.md) after POK-25 apply. Point Twilio to `{app_url}/voice` instead of ngrok — see POK-11.

## Notes

- **Signature validation (POK-13):** Twilio signs the *public* URL. Behind ngrok or a reverse proxy, validators must reconstruct the external `https://` URL, not `http://localhost:8080`.
- **Retire Retell (POK-11 / POK-81):** Remove the number from `retellaTrunk` (or clear `trunk_sid`) so inbound calls use the webhook, not `sip.retellai.com`.

## E2E checklist (First Call)

- [ ] `./gradlew bootRun` and `/health` returns 200
- [ ] ngrok tunnel running; `PUBLIC_BASE_URL` set in `.env`
- [ ] `curl -X POST "$PUBLIC_BASE_URL/voice"` returns valid TwiML over HTTPS
- [ ] Twilio number webhook → `{PUBLIC_BASE_URL}/voice` (POK-11) — run `./scripts/repoint-twilio-voice-webhook.sh` or use Console
- [ ] Run `./scripts/verify-voice-preflight.sh` (POK-86)
- [ ] Place a test call and hear the greeting (POK-12) — see [e2e-test-call.md](e2e-test-call.md)
