# Twilio public URL (local dev)

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

## Next: point Twilio at the webhook (POK-11)

In the [Twilio Console](https://console.twilio.com/) → Phone Numbers → your number → **Voice configuration**:

| Field | Value |
|-------|-------|
| A call comes in | Webhook |
| URL | `{PUBLIC_BASE_URL}/voice` |
| HTTP method | `POST` |

Use the same ngrok HTTPS base URL Twilio will call in production-like tests.

## Hosted path (later)

For Azure Container Apps, use `terraform output app_url` from [`parley-infra/`](../parley-infra/README.md) after POK-25 apply. Point Twilio to `{app_url}/voice` instead of ngrok — see POK-11.

## Notes

- **Signature validation (POK-13):** Twilio signs the *public* URL. Behind ngrok or a reverse proxy, validators must reconstruct the external `https://` URL, not `http://localhost:8080`.
- **Retire Retell (POK-11):** SIP domain `jmacretella.sip.twilio.com` is unused once the number webhook points at Parley.

## E2E checklist (First Call)

- [ ] `./gradlew bootRun` and `/health` returns 200
- [ ] ngrok tunnel running; `PUBLIC_BASE_URL` set in `.env`
- [ ] `curl -X POST "$PUBLIC_BASE_URL/voice"` returns valid TwiML over HTTPS
- [ ] Twilio number webhook → `{PUBLIC_BASE_URL}/voice` (POK-11)
- [ ] Place a test call and hear the greeting (POK-12)
