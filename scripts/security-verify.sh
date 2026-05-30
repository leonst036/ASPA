#!/usr/bin/env bash
set -euo pipefail

API_URL="${API_URL:-http://localhost:8080}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-ChangeMe123!}"
PLUGIN_DATA_DIR="${PLUGIN_DATA_DIR:-./plugins/ASPA}"

echo "Checking setup status..."
setup_status="$(curl -fsS "${API_URL}/api/v1/setup/status")"
setup_required="$(SETUP_STATUS="$setup_status" python - <<'PY'
import json, os, sys
data = json.loads(os.environ["SETUP_STATUS"])
print("true" if data.get("setupRequired") else "false")
PY
)"

token=""
if [[ "$setup_required" == "true" ]]; then
  echo "Running initial setup..."
  setup_resp="$(curl -fsS -X POST "${API_URL}/api/v1/setup" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"${ADMIN_USER}\",\"password\":\"${ADMIN_PASS}\"}")"
  token="$(SETUP_RESP="$setup_resp" python - <<'PY'
import json, os
data = json.loads(os.environ["SETUP_RESP"])
print(data.get("token") or "")
PY
)"
else
  echo "Logging in..."
  login_resp="$(curl -fsS -X POST "${API_URL}/api/v1/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"${ADMIN_USER}\",\"password\":\"${ADMIN_PASS}\"}")"
  token="$(LOGIN_RESP="$login_resp" python - <<'PY'
import json, os
data = json.loads(os.environ["LOGIN_RESP"])
print(data.get("token") or "")
PY
)"
fi

if [[ -z "$token" ]]; then
  echo "Failed to retrieve an API token from setup/login." >&2
  exit 1
fi

echo "Validating authenticated access..."
curl -fsS -H "Authorization: Bearer ${token}" "${API_URL}/api/v1/users/me" > /dev/null

if [[ -f "${PLUGIN_DATA_DIR}/config.yml" ]]; then
  if grep -q "ASPA_SECURE_API_GATEWAY_TOKEN_12345" "${PLUGIN_DATA_DIR}/config.yml"; then
    echo "Detected insecure default API token in ${PLUGIN_DATA_DIR}/config.yml" >&2
    exit 1
  fi
  if grep -q 'api-token: ""' "${PLUGIN_DATA_DIR}/config.yml"; then
    echo "Detected empty API token in ${PLUGIN_DATA_DIR}/config.yml" >&2
    exit 1
  fi
fi

if command -v sqlite3 >/dev/null 2>&1 && [[ -f "${PLUGIN_DATA_DIR}/analytics.db" ]]; then
  hash="$(sqlite3 "${PLUGIN_DATA_DIR}/analytics.db" "select password_hash from aspa_users where username='${ADMIN_USER}' limit 1;")"
  if [[ -n "$hash" && "$hash" != PBKDF2:* ]]; then
    echo "Password hash was not upgraded to the new PBKDF2 format." >&2
    exit 1
  fi
fi

echo "Security verification completed successfully."
