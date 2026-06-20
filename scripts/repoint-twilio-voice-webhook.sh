#!/usr/bin/env bash
# Repoint a Twilio phone number from Retell (SIP trunk) to Parley POST /voice.
# Clears trunk_sid — Twilio ignores voice_url while a trunk is attached.
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
: "${TWILIO_FROM_NUMBER:?Set TWILIO_FROM_NUMBER (E.164, e.g. +14155551234)}"
: "${PUBLIC_BASE_URL:?Set PUBLIC_BASE_URL (ngrok or hosted app_url, no trailing slash)}"

VOICE_URL="${PUBLIC_BASE_URL%/}/voice"
STATUS_URL="${PUBLIC_BASE_URL%/}/voice/status"
API="https://api.twilio.com/2010-04-01/Accounts/${TWILIO_ACCOUNT_SID}/IncomingPhoneNumbers.json"

echo "Looking up ${TWILIO_FROM_NUMBER}..."
LOOKUP=$(curl -s -G "$API" \
	--data-urlencode "PhoneNumber=${TWILIO_FROM_NUMBER}" \
	-u "${TWILIO_ACCOUNT_SID}:${TWILIO_AUTH_TOKEN}")

PHONE_SID=$(python3 -c "import json,sys; d=json.load(sys.stdin); nums=d.get('incoming_phone_numbers',[]); print(nums[0]['sid'] if nums else '')" <<<"$LOOKUP")

if [[ -z "$PHONE_SID" ]]; then
	echo "No incoming phone number found for ${TWILIO_FROM_NUMBER}" >&2
	exit 1
fi

read -r CURRENT_URL CURRENT_TRUNK <<<"$(python3 -c "
import json, sys
n = json.load(sys.stdin)['incoming_phone_numbers'][0]
print(n.get('voice_url') or '', n.get('trunk_sid') or '')
" <<<"$LOOKUP")"

echo "Current voice_url: ${CURRENT_URL:-<empty>}"
echo "Current trunk_sid: ${CURRENT_TRUNK:-<empty>}"
echo "Updating to: ${VOICE_URL} (POST), status ${STATUS_URL} (POST), clearing trunk_sid"

UPDATE=$(curl -s -X POST \
	"https://api.twilio.com/2010-04-01/Accounts/${TWILIO_ACCOUNT_SID}/IncomingPhoneNumbers/${PHONE_SID}.json" \
	--data-urlencode "TrunkSid=" \
	--data-urlencode "VoiceUrl=${VOICE_URL}" \
	--data-urlencode "VoiceMethod=POST" \
	--data-urlencode "StatusCallback=${STATUS_URL}" \
	--data-urlencode "StatusCallbackMethod=POST" \
	-u "${TWILIO_ACCOUNT_SID}:${TWILIO_AUTH_TOKEN}")

read -r NEW_URL NEW_METHOD NEW_TRUNK <<<"$(python3 -c "
import json, sys
n = json.load(sys.stdin)
print(n.get('voice_url', ''), n.get('voice_method', ''), n.get('trunk_sid') or '')
" <<<"$UPDATE")"

if [[ "$NEW_URL" != "$VOICE_URL" ]] || [[ "$NEW_METHOD" != "POST" ]]; then
	echo "Update failed:" >&2
	echo "$UPDATE" >&2
	exit 1
fi

if [[ -n "$NEW_TRUNK" ]]; then
	echo "trunk_sid still set (${NEW_TRUNK}); voice_url is ignored by Twilio while a trunk is attached." >&2
	echo "$UPDATE" >&2
	exit 1
fi

echo "Done. ${TWILIO_FROM_NUMBER} now webhooks to ${NEW_URL} (${NEW_METHOD}), status ${STATUS_URL}, trunk_sid cleared."
