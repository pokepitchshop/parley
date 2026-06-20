#!/usr/bin/env bash
# Repoint a Twilio phone number from Retell (SIP) to Parley POST /voice.
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

CURRENT_URL=$(python3 -c "import json,sys; d=json.load(sys.stdin); print(d['incoming_phone_numbers'][0].get('voice_url') or '')" <<<"$LOOKUP")
echo "Current voice_url: ${CURRENT_URL:-<empty>}"
echo "Updating to: ${VOICE_URL} (POST)"

UPDATE=$(curl -s -X POST \
	"https://api.twilio.com/2010-04-01/Accounts/${TWILIO_ACCOUNT_SID}/IncomingPhoneNumbers/${PHONE_SID}.json" \
	--data-urlencode "VoiceUrl=${VOICE_URL}" \
	--data-urlencode "VoiceMethod=POST" \
	-u "${TWILIO_ACCOUNT_SID}:${TWILIO_AUTH_TOKEN}")

NEW_URL=$(python3 -c "import json,sys; print(json.load(sys.stdin).get('voice_url',''))" <<<"$UPDATE")
NEW_METHOD=$(python3 -c "import json,sys; print(json.load(sys.stdin).get('voice_method',''))" <<<"$UPDATE")

if [[ "$NEW_URL" != "$VOICE_URL" ]] || [[ "$NEW_METHOD" != "POST" ]]; then
	echo "Update failed:" >&2
	echo "$UPDATE" >&2
	exit 1
fi

echo "Done. ${TWILIO_FROM_NUMBER} now webhooks to ${NEW_URL} (${NEW_METHOD})."
echo "SIP domain jmacretella.sip.twilio.com is no longer used for this number."
