#!/usr/bin/env bash
# Smoke-test a deployed Parley instance (Azure or any public HTTPS base URL).
set -euo pipefail

APP_URL="${1:?Usage: $0 <app_url>}"
BASE="${APP_URL%/}"

echo "GET ${BASE}/health"
HEALTH=$(curl -sf "${BASE}/health")
echo "$HEALTH"
echo "$HEALTH" | grep -q '"status":"ok"'

echo "POST ${BASE}/voice"
TWIML=$(curl -sf -X POST "${BASE}/voice")
echo "$TWIML" | grep -q '<Response>'
echo "$TWIML" | grep -q '<Gather'

echo "OK — health and /voice TwiML look good."
