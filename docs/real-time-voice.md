# Real-Time Voice milestone (POK-127)

Parley’s production voice path is **Twilio ConversationRelay**: one WebSocket, native barge-in, streamed LLM replies. The old turn-based `<Gather>` / `<Say>` loop remains available for local dev only.

## Milestone acceptance (POK-31)

- [x] Live call on ConversationRelay — natural back-and-forth
- [x] Interrupt mid-sentence — agent stops and handles new input
- [x] Latency feels conversational (tuned via streaming + sentence chunking)
- [x] Per-turn logs: `relay.turn.latency` (see [conversation-relay.md](conversation-relay.md))

## Architecture

```text
Inbound call
  → Twilio POST /voice          (default: ConversationRelay TwiML)
  → <Connect><ConversationRelay url="wss://…/relay" welcomeGreeting="…"/></Connect>
  → Twilio WSS → /relay
  → VoiceReplyService (streaming ChatClient + guardrails + ChatMemory)
  → text tokens → Twilio TTS
  → POST /voice/status on hangup → call summary
```

Detailed runbook: [conversation-relay.md](conversation-relay.md)

## Default webhook

Twilio should point at **`{PUBLIC_BASE_URL}/voice`** (POST) — not `/voice/relay`. Both paths return ConversationRelay TwiML; `/voice` is the production default after POK-130.

```bash
./scripts/repoint-twilio-voice-webhook.sh
# Sets voice_url → {PUBLIC_BASE_URL}/voice (server returns ConversationRelay TwiML)
```

## Configuration

| Setting | Default | Purpose |
|---------|---------|---------|
| `parley.voice.mode` | `relay` | `relay` = ConversationRelay on `POST /voice`; `turn` = legacy Gather/Say loop |
| `PUBLIC_BASE_URL` | — | Public HTTPS base (no `/voice` suffix); required for signatures + WSS URL |
| `TWILIO_AUTH_TOKEN` | — | Enables signature validation on `/voice/**` and WSS `/relay` |

Local legacy loop (First Call debugging):

```properties
parley.voice.mode=turn
```

See [e2e-test-call.md](e2e-test-call.md) for turn-based acceptance steps.

## Work breakdown

| Issue | Status | Notes |
|-------|--------|-------|
| [POK-27](https://linear.app/pokepitchshop/issue/POK-27) | Done | ConversationRelay over Media Streams |
| [POK-13](https://linear.app/pokepitchshop/issue/POK-13) | Done | HTTP + WSS signature validation |
| [POK-28](https://linear.app/pokepitchshop/issue/POK-28) | Done | WebSocket + TwiML transport |
| [POK-29](https://linear.app/pokepitchshop/issue/POK-29) | Done | Streaming ChatClient bridge |
| [POK-30](https://linear.app/pokepitchshop/issue/POK-30) | Done | Barge-in + latency logging |
| [POK-31](https://linear.app/pokepitchshop/issue/POK-31) | Done | E2E acceptance |
| [POK-130](https://linear.app/pokepitchshop/issue/POK-130) | Done | `/voice` → ConversationRelay + docs |
| [POK-128](https://linear.app/pokepitchshop/issue/POK-128) | Done | Guardrails, transcripts, caller greeting, status summaries on relay |
| [POK-129](https://linear.app/pokepitchshop/issue/POK-129) | Backlog | WS reconnect TwiML, prod `min_replicas=1` — see follow-ups below |

### Capable Agent on relay (POK-128)

Already wired on the streaming path:

- **Welcome:** `CallerService.contextFor(from)` → `welcomeGreeting` on `<ConversationRelay>`
- **Per-turn:** `VoiceReplyService` — guardrails, `ChatMemory` keyed by `CallSid`, `TranscriptService.appendTurn`
- **Hangup:** `POST /voice/status` → `CallSummaryService.onCallCompleted`

Arcade tools deferred to Agent Actions milestone.

## Verify

### Preflight (local)

```bash
./scripts/verify-voice-preflight.sh
```

Expect `POST /voice` to return `<ConversationRelay …/>` (not `<Gather>`).

### Live acceptance

1. Deploy latest to Azure (`./scripts/deploy-parley-azure.sh`)
2. Confirm Twilio webhook → `{app_url}/voice`
3. Dial — ask a question, interrupt mid-answer, ask again
4. Tail logs for `relay.turn.latency` — see [conversation-relay.md](conversation-relay.md)

### Latency tuning

Compare Parley `relay.turn.latency` fields with Twilio Voice Insights **Conversation Relay Call Summary** (STT / Application / TTS buckets).

Target feel: response starts within ~1–2s perceived; barge-in stops TTS immediately.

## Follow-ups (post-milestone)

- **POK-129:** `<Connect action="…">` reconnect TwiML when WSS drops; document ACA 240s timeout; `min_replicas=1` in prod
- **Agent Actions:** Arcade tool execution on streaming path
- **POK-131:** Research baseline doc (optional deep-dive)

## Related docs

- [conversation-relay.md](conversation-relay.md) — deploy, troubleshoot, latency logs
- [twilio-public-url.md](twilio-public-url.md) — ngrok, signatures, webhook cutover
- [e2e-test-call.md](e2e-test-call.md) — legacy turn-based acceptance (`parley.voice.mode=turn`)
- [azure-deploy.md](azure-deploy.md) — Container Apps deploy
