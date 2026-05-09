#!/usr/bin/env bash

set -u

BASE_URL="${BASE_URL:-http://localhost:8080}"
API_KEY="${API_KEY:-change-me-dev-key}"
USERNAME="${USERNAME:-admin}"
PASSWORD="${PASSWORD:-password}"

PASS=0
FAIL=0
SKIP=0

GREEN="\033[0;32m"
RED="\033[0;31m"
YELLOW="\033[1;33m"
NC="\033[0m"

need_jq() {
  if ! command -v jq >/dev/null 2>&1; then
    echo -e "${RED}jq is required. Install with: sudo apt install jq${NC}"
    exit 1
  fi
}

pass() {
  echo -e "${GREEN}PASS${NC} - $1"
  PASS=$((PASS + 1))
}

fail() {
  echo -e "${RED}FAIL${NC} - $1"
  echo "       $2"
  FAIL=$((FAIL + 1))
}

skip() {
  echo -e "${YELLOW}SKIP${NC} - $1"
  SKIP=$((SKIP + 1))
}

request() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  local headers="${4:-}"

  if [ -n "$body" ]; then
    eval curl --max-time 30 -s -w "'\n%{http_code}'" -X "$method" "'$url'" \
      -H "'Content-Type: application/json'" \
      $headers \
      -d "'$body'"
  else
    eval curl --max-time 30 -s -w "'\n%{http_code}'" -X "$method" "'$url'" \
      $headers
  fi
}

check_status() {
  local name="$1"
  local expected="$2"
  local actual="$3"
  local body="$4"

  if [ "$actual" = "$expected" ]; then
    pass "$name"
  else
    fail "$name expected HTTP $expected but got HTTP $actual" "$body"
  fi
}

need_jq

echo "========================================"
echo " Payment Integration Service Battery Test"
echo " BASE_URL=$BASE_URL"
echo "========================================"
echo

# 1. Health
RESP=$(request GET "$BASE_URL/actuator/health")
BODY=$(echo "$RESP" | sed '$d')
CODE=$(echo "$RESP" | tail -n1)
check_status "Health endpoint" "200" "$CODE" "$BODY"

# 2. Swagger docs
RESP=$(request GET "$BASE_URL/v3/api-docs")
BODY=$(echo "$RESP" | sed '$d')
CODE=$(echo "$RESP" | tail -n1)
check_status "OpenAPI docs available" "200" "$CODE" "$BODY"

# 3. Auth login
LOGIN_BODY='{"username":"'"$USERNAME"'","password":"'"$PASSWORD"'"}'
RESP=$(request POST "$BASE_URL/api/auth/login" "$LOGIN_BODY")
BODY=$(echo "$RESP" | sed '$d')
CODE=$(echo "$RESP" | tail -n1)
TOKEN=$(echo "$BODY" | jq -r '.token // empty')

if [ "$CODE" = "200" ] && [ -n "$TOKEN" ]; then
  pass "JWT login returns token"
else
  fail "JWT login returns token" "$BODY"
fi

# 4. Protected endpoint without auth should fail
CREATE_BODY='{
  "amount": 1000,
  "currency": "usd",
  "customerName": "No Auth Test",
  "customerEmail": "noauth@example.com",
  "description": "Should fail without auth"
}'

RESP=$(request POST "$BASE_URL/api/payment_intents" "$CREATE_BODY")
BODY=$(echo "$RESP" | sed '$d')
CODE=$(echo "$RESP" | tail -n1)

if [ "$CODE" = "401" ] || [ "$CODE" = "403" ]; then
  pass "Protected endpoint rejects missing auth"
else
  fail "Protected endpoint should reject missing auth" "$BODY"
fi

# 5. Create PaymentIntent with API key
CREATE_BODY='{
  "amount": 1000,
  "currency": "usd",
  "customerName": "API Key Test",
  "customerEmail": "apikey@example.com",
  "description": "API key create payment intent test",
  "metadata": {
    "testRun": "battery",
    "auth": "api-key"
  }
}'

RESP=$(request POST "$BASE_URL/api/payment_intents" "$CREATE_BODY" "-H 'X-API-Key: $API_KEY'")
BODY=$(echo "$RESP" | sed '$d')
CODE=$(echo "$RESP" | tail -n1)
PI_ID=$(echo "$BODY" | jq -r '.id // .paymentIntentId // empty')
STATUS=$(echo "$BODY" | jq -r '.status // empty')

if [ "$CODE" = "200" ] || [ "$CODE" = "201" ]; then
  if [ -n "$PI_ID" ]; then
    pass "Create PaymentIntent with API key"
  else
    fail "Create PaymentIntent returned no id" "$BODY"
  fi
else
  fail "Create PaymentIntent with API key" "$BODY"
fi

# 6. Retrieve PaymentIntent
if [ -n "${PI_ID:-}" ]; then
  RESP=$(request GET "$BASE_URL/api/payment_intents/$PI_ID" "" "-H 'X-API-Key: $API_KEY'")
  BODY=$(echo "$RESP" | sed '$d')
  CODE=$(echo "$RESP" | tail -n1)
  check_status "Retrieve PaymentIntent by id" "200" "$CODE" "$BODY"
else
  skip "Retrieve PaymentIntent by id"
fi

# 7. Confirm PaymentIntent
if [ -n "${PI_ID:-}" ]; then
  CONFIRM_BODY='{"paymentMethodId":"pm_card_visa"}'
  RESP=$(request POST "$BASE_URL/api/payment_intents/$PI_ID/confirm" "$CONFIRM_BODY" "-H 'X-API-Key: $API_KEY'")
  BODY=$(echo "$RESP" | sed '$d')
  CODE=$(echo "$RESP" | tail -n1)
  CONFIRM_STATUS=$(echo "$BODY" | jq -r '.status // empty')

  if [ "$CODE" = "200" ] && [ "$CONFIRM_STATUS" = "succeeded" ]; then
    pass "Confirm PaymentIntent"
  else
    fail "Confirm PaymentIntent" "$BODY"
  fi
else
  skip "Confirm PaymentIntent"
fi

# 8. Duplicate confirm should not explode
if [ -n "${PI_ID:-}" ]; then
  CONFIRM_BODY='{"paymentMethodId":"pm_card_visa"}'
  RESP=$(request POST "$BASE_URL/api/payment_intents/$PI_ID/confirm" "$CONFIRM_BODY" "-H 'X-API-Key: $API_KEY'")
  BODY=$(echo "$RESP" | sed '$d')
  CODE=$(echo "$RESP" | tail -n1)

  if [ "$CODE" = "200" ] || [ "$CODE" = "409" ] || [ "$CODE" = "400" ]; then
    pass "Duplicate confirm handled safely"
  else
    fail "Duplicate confirm handled safely" "$BODY"
  fi
else
  skip "Duplicate confirm handled safely"
fi

# 9. Partial refund
if [ -n "${PI_ID:-}" ]; then
  REFUND_BODY='{
    "paymentIntentId": "'"$PI_ID"'",
    "amount": 500,
    "reason": "requested_by_customer"
  }'

  RESP=$(request POST "$BASE_URL/api/refunds" "$REFUND_BODY" "-H 'X-API-Key: $API_KEY'")
  BODY=$(echo "$RESP" | sed '$d')
  CODE=$(echo "$RESP" | tail -n1)

  if [ "$CODE" = "200" ] || [ "$CODE" = "201" ]; then
    pass "Partial refund"
  else
    fail "Partial refund" "$BODY"
  fi
else
  skip "Partial refund"
fi

# 10. Invalid refund reason should fail
if [ -n "${PI_ID:-}" ]; then
  BAD_REFUND_BODY='{
    "paymentIntentId": "'"$PI_ID"'",
    "amount": 100,
    "reason": "not_a_valid_reason"
  }'

  RESP=$(request POST "$BASE_URL/api/refunds" "$BAD_REFUND_BODY" "-H 'X-API-Key: $API_KEY'")
  BODY=$(echo "$RESP" | sed '$d')
  CODE=$(echo "$RESP" | tail -n1)

  if [ "$CODE" = "400" ]; then
    pass "Invalid refund reason rejected"
  else
    fail "Invalid refund reason rejected" "$BODY"
  fi
else
  skip "Invalid refund reason rejected"
fi

# 11. Full refund remainder
if [ -n "${PI_ID:-}" ]; then
  REFUND_BODY='{
    "paymentIntentId": "'"$PI_ID"'",
    "amount": 500,
    "reason": "requested_by_customer"
  }'

  RESP=$(request POST "$BASE_URL/api/refunds" "$REFUND_BODY" "-H 'X-API-Key: $API_KEY'")
  BODY=$(echo "$RESP" | sed '$d')
  CODE=$(echo "$RESP" | tail -n1)

  if [ "$CODE" = "200" ] || [ "$CODE" = "201" ]; then
    pass "Full refund remainder"
  else
    fail "Full refund remainder" "$BODY"
  fi
else
  skip "Full refund remainder"
fi

# 12. Invalid currency should fail
BAD_CURRENCY_BODY='{
  "amount": 1000,
  "currency": "zzz",
  "customerName": "Bad Currency",
  "customerEmail": "badcurrency@example.com",
  "description": "Invalid currency test"
}'

RESP=$(request POST "$BASE_URL/api/payment_intents" "$BAD_CURRENCY_BODY" "-H 'X-API-Key: $API_KEY'")
BODY=$(echo "$RESP" | sed '$d')
CODE=$(echo "$RESP" | tail -n1)

if [ "$CODE" = "400" ]; then
  pass "Invalid currency rejected"
else
  fail "Invalid currency rejected" "$BODY"
fi

# 13. JWT protected create
if [ -n "${TOKEN:-}" ]; then
  JWT_CREATE_BODY='{
    "amount": 1300,
    "currency": "usd",
    "customerName": "JWT Battery",
    "customerEmail": "jwtbattery@example.com",
    "description": "JWT protected battery test"
  }'

  RESP=$(request POST "$BASE_URL/api/payment_intents" "$JWT_CREATE_BODY" "-H 'Authorization: Bearer $TOKEN'")
  BODY=$(echo "$RESP" | sed '$d')
  CODE=$(echo "$RESP" | tail -n1)

  if [ "$CODE" = "200" ] || [ "$CODE" = "201" ]; then
    pass "JWT protected PaymentIntent create"
  else
    fail "JWT protected PaymentIntent create" "$BODY"
  fi
else
  skip "JWT protected PaymentIntent create"
fi

echo
echo "========================================"
echo " Results"
echo " Passed:  $PASS"
echo " Failed:  $FAIL"
echo " Skipped: $SKIP"
echo "========================================"

if [ "$FAIL" -eq 0 ]; then
  exit 0
else
  exit 1
fi

