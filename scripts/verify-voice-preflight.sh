#!/usr/bin/env bash
# POK-86 preflight: confirm Parley + ngrok + Twilio are ready for a live test call.
# Requires: TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, TWILIO_FROM_NUMBER, PUBLIC_BASE_URL
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -f "$ROOT/.env" ]]; then
	set -a
	# shellcheck disable=SC1091
	source "$ROOT/.env"
	set +a
fi

: "${TWILIO_ACCOUNT_SID:?Set TWILIO_ACCOUNT_SID}"
: "${TWILIO_AUTH_TOKEN:?Set TWILIO_AUTH_TOKEN}"
: "${TWILIO_FROM_NUMBER:?Set TWILIO_FROM_NUMBER}"
: "${PUBLIC_BASE_URL:?Set PUBLIC_BASE_URL}"

VOICE_URL="${PUBLIC_BASE_URL%/}/voice"
FAIL=0

pass() { echo "OK   $*"; }
fail() { echo "FAIL $*"; FAIL=1; }

echo "=== Parley health ==="
if curl -sf "http://localhost:8080/health" >/dev/null; then
	pass "bootRun responding on :8080/health"
else
	fail "bootRun not reachable at http://localhost:8080/health — run ./gradlew bootRun"
fi

echo ""
echo "=== ngrok tunnel ==="
NGROK_URL=""
if curl -sf "http://127.0.0.1:4040/api/tunnels" >/dev/null 2>&1; then
	NGROK_URL=$(python3 -c "
import json, urllib.request
data = json.load(urllib.request.urlopen('http://127.0.0.1:4040/api/tunnels'))
tunnels = data.get('tunnels', [])
https = next((t['public_url'] for t in tunnels if t.get('public_url', '').startswith('https://')), '')
print(https)
")
	if [[ -n "$NGROK_URL" ]]; then
		pass "ngrok forwarding ${NGROK_URL} -> localhost:8080"
	else
		fail "ngrok running but no HTTPS tunnel found"
	fi
else
	fail "ngrok inspector not reachable at http://127.0.0.1:4040 — run: ngrok http 8080"
fi

if [[ -n "$NGROK_URL" && "$NGROK_URL" != "${PUBLIC_BASE_URL%/}" ]]; then
	fail "PUBLIC_BASE_URL (${PUBLIC_BASE_URL}) != ngrok (${NGROK_URL}) — update .env and re-run ./scripts/repoint-twilio-voice-webhook.sh"
fi

echo ""
echo "=== Twilio number config ==="
LOOKUP=$(curl -s -G \
	"https://api.twilio.com/2010-04-01/Accounts/${TWILIO_ACCOUNT_SID}/IncomingPhoneNumbers.json" \
	--data-urlencode "PhoneNumber=${TWILIO_FROM_NUMBER}" \
	-u "${TWILIO_ACCOUNT_SID}:${TWILIO_AUTH_TOKEN}")

read -r TWILIO_VOICE TWILIO_METHOD TWILIO_TRUNK <<<"$(python3 -c "
import json, sys
n = json.load(sys.stdin)['incoming_phone_numbers'][0]
print(n.get('voice_url') or '', n.get('voice_method') or '', n.get('trunk_sid') or '')
" <<<"$LOOKUP")"

if [[ -n "$TWILIO_TRUNK" ]]; then
	fail "trunk_sid still set (${TWILIO_TRUNK}) — run ./scripts/repoint-twilio-voice-webhook.sh"
else
	pass "trunk_sid empty (webhook routing active)"
fi

if [[ "$TWILIO_VOICE" == "$VOICE_URL" && "$TWILIO_METHOD" == "POST" ]]; then
	pass "Twilio voice_url -> ${VOICE_URL} (POST)"
else
	fail "Twilio voice_url is '${TWILIO_VOICE}' (${TWILIO_METHOD}), expected '${VOICE_URL}' (POST)"
fi

echo ""
echo "=== /voice TwiML smoke test ==="
TWIML=$(curl -s -X POST -H "ngrok-skip-browser-warning: true" "$VOICE_URL" || true)
if echo "$TWIML" | grep -q "Poke Pitch Shop" && echo "$TWIML" | grep -q "<Gather"; then
	pass "POST ${VOICE_URL} returns Parley greeting + Gather"
else
	fail "POST ${VOICE_URL} did not return expected TwiML"
fi

echo ""
echo "=== LLM (turn loop) ==="
if [[ -n "${AZURE_OPENAI_API_KEY:-}" && -n "${SPRING_AI_OPENAI_BASE_URL:-}" ]]; then
	pass "Azure OpenAI env set (AZURE_OPENAI_API_KEY + SPRING_AI_OPENAI_BASE_URL)"
else
	fail "Set AZURE_OPENAI_API_KEY and SPRING_AI_OPENAI_BASE_URL in .env — /voice/respond will 401"
fi

echo ""
if [[ "$FAIL" -eq 0 ]]; then
	echo "Preflight passed. Dial ${TWILIO_FROM_NUMBER} and confirm ngrok shows POST /voice (not Retell)."
	exit 0
fi

echo "Preflight failed — fix the items above before placing a test call."
exit 1
