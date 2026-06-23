# First Call milestone acceptance (POK-12)

End-to-end test that Parley handles a real phone call: greeting, turn loop, and memory within one call.

## Prerequisites

Complete [twilio-public-url.md](twilio-public-url.md):

- `./gradlew bootRun` with Azure OpenAI vars in `.env` (see [llm-provider.md](llm-provider.md))
- ngrok (or hosted `app_url`) with `PUBLIC_BASE_URL` in `.env`
- Twilio number webhooks to `{PUBLIC_BASE_URL}/voice` (POST)

## Voice tuning defaults

Configured in `application.properties` (override locally if needed):

| Property | Default | Purpose |
|----------|---------|---------|
| `parley.voice.say-voice` | `POLLY_JOANNA_NEURAL` | Neural TTS voice on `<Say>` |
| `parley.voice.speech-timeout` | `3` | Seconds of silence after speech before Twilio sends `SpeechResult` |

System prompt in `ChatClientConfig` is tuned for short, spoken answers and same-call recall via `ChatMemory` keyed by `CallSid`.

## Acceptance test script

1. **Dial** your Twilio number from a mobile phone.

2. **Greeting** — Hear: *"Hi, you're through to Poke Pitch Shop. What can I help you with?"* (Polly Joanna neural voice).

3. **Turn one** — Ask something simple, e.g. *"What are your hours?"*  
   Expect a short spoken answer (not Retell, not an error message).

4. **Turn two (memory)** — Without hanging up, ask: *"What did I just ask you about?"* or *"Repeat my last question."*  
   Expect Parley to recall your first question from this call.

5. **Empty speech** — Stay silent when prompted; Parley should redirect and re-greet (no crash).

## Troubleshooting

| Symptom | Check |
|---------|--------|
| Call fails / busy | `bootRun` running; ngrok tunnel up; webhook URL matches `PUBLIC_BASE_URL` |
| Robotic error / application error | App logs; `AZURE_OPENAI_API_KEY` + `SPRING_AI_OPENAI_BASE_URL` set; Twilio debugger for webhook 4xx/5xx |
| `/voice/respond` 500 on first turn | Usually LLM config (Azure key/deployment), not caller lookup — anonymous `CallerContext` is normal for new callers |
| Repeat caller still gets generic greeting | Needs a completed call with successful turns + `/voice/status` callback; see POK-23 |
| No memory on turn two | POK-9 `ChatMemory` + `CallSid` on `/voice/respond`; same call (don't hang up) |
| Agent reply in Mongo but silent on phone | Twilio webhook timeout (15s). Parley acks with "One moment." then runs the LLM on `/voice/reply`; redeploy if missing |
| Long awkward pause after you speak | Lower `parley.voice.speech-timeout` (try `2`) |
| Cut off mid-sentence | Raise `speech-timeout` slightly (try `4`) |

## Milestone done when

- [ ] Real call completes greeting + two-turn loop
- [ ] Turn two shows recall of turn one in the same call
- [ ] Voice and pacing feel acceptable for a phone agent (tweak `parley.voice.*` if not)

When this passes, **First Call** milestone is accepted.
