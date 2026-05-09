#!/usr/bin/env bash
set -u

# Railway payment integration test battery
# Usage:
#   BASE_URL="https://payment-integration-service-production-18ae.up.railway.app" \
#   API_KEY="test123" \
#   bash railway_payment_battery.sh
#
# Optional:
#   INTERNAL_UUID="..."   # tests UUID refund route if you have internal DB UUID
#   IDEMPOTENCY_HEADER="Idempotency-Key"   # change if your API uses a different header name

BASE_URL="${BASE_URL:-https://payment-integration-service-production-18ae.up.railway.app}"
API_KEY="${API_KEY:-}"
INTERNAL_UUID="${INTERNAL_UUID:-}"
IDEMPOTENCY_HEADER="${IDEMPOTENCY_HEADER:-Idempotency-Key}"

if [[ -z "$API_KEY" ]]; then
  echo "ERROR: API_KEY is required."
  echo 'Example: API_KEY="test123" bash railway_payment_battery.sh'
  exit 1
fi

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

PASS_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0

LAST_STATUS=""
LAST_BODY=""
LAST_HEADERS=""

PI_ID=""
PI2_ID=""
PI3_ID=""

python_json_get() {
  local file="$1"
  local key="$2"
  python3 - "$file" "$key" <<'PY'
import json, sys
path = sys.argv[1]
key = sys.argv[2]
with open(path, 'r', encoding='utf-8') as f:
    data = json.load(f)
value = data
for part in key.split('.'):
    if isinstance(value, dict) and part in value:
        value = value[part]
    else:
        print("")
        sys.exit(0)
if value is None:
    print("")
elif isinstance(value, (dict, list)):
    print(json.dumps(value))
else:
    print(value)
PY
}

http_request() {
  local name="$1"
  shift
  local body="$WORKDIR/${name}.body"
  local headers="$WORKDIR/${name}.headers"

  set +e
  curl -sS -D "$headers" -o "$body" "$@"
  local curl_rc=$?
  set -e

  if [[ $curl_rc -ne 0 ]]; then
    LAST_STATUS="CURL_ERROR"
    LAST_BODY=""
    LAST_HEADERS=""
    return $curl_rc
  fi

  LAST_BODY="$body"
  LAST_HEADERS="$headers"
  LAST_STATUS="$(awk 'toupper($1) ~ /^HTTP/ {code=$2} END {print code}' "$headers")"
  return 0
}

pass() {
  local msg="$1"
  PASS_COUNT=$((PASS_COUNT + 1))
  echo "PASS: $msg"
}

fail() {
  local msg="$1"
  FAIL_COUNT=$((FAIL_COUNT + 1))
  echo "FAIL: $msg"
  if [[ -n "$LAST_STATUS" ]]; then
    echo "  HTTP: $LAST_STATUS"
  fi
  if [[ -n "$LAST_BODY" && -f "$LAST_BODY" ]]; then
    echo "  Body:"
    sed 's/^/    /' "$LAST_BODY"
  fi
}

skip() {
  local msg="$1"
  SKIP_COUNT=$((SKIP_COUNT + 1))
  echo "SKIP: $msg"
}

expect_status_in() {
  local actual="$1"
  shift
  for expected in "$@"; do
    if [[ "$actual" == "$expected" ]]; then
      return 0
    fi
  done
  return 1
}

assert_json_field_equals() {
  local file="$1"
  local key="$2"
  local expected="$3"
  local actual
  actual="$(python_json_get "$file" "$key")"
  [[ "$actual" == "$expected" ]]
}

assert_json_field_nonempty() {
  local file="$1"
  local key="$2"
  local actual
  actual="$(python_json_get "$file" "$key")"
  [[ -n "$actual" ]]
}

echo "== Railway Payment Battery =="
echo "BASE_URL=$BASE_URL"
echo

set -e

# 1. Health
echo "-- Test 1: health"
http_request health \
  -H "X-API-KEY: $API_KEY" \
  "$BASE_URL/actuator/health"
if expect_status_in "$LAST_STATUS" 200 && assert_json_field_equals "$LAST_BODY" "status" "UP"; then
  pass "health endpoint is UP"
else
  fail "health endpoint check failed"
fi
echo

# 2. Missing API key
echo "-- Test 2: missing API key"
http_request no_api_key \
  "$BASE_URL/api/payment_intents"
if expect_status_in "$LAST_STATUS" 401 403; then
  pass "missing API key rejected"
else
  fail "missing API key was not rejected as expected"
fi
echo

# 3. Bad API key
echo "-- Test 3: bad API key"
http_request bad_api_key \
  -H "X-API-KEY: wrong-key" \
  "$BASE_URL/api/payment_intents"
if expect_status_in "$LAST_STATUS" 401 403; then
  pass "bad API key rejected"
else
  fail "bad API key was not rejected as expected"
fi
echo

# 4. Create payment intent happy path
echo "-- Test 4: create payment intent"
http_request create_pi \
  -X POST "$BASE_URL/api/payment_intents" \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: $API_KEY" \
  -d '{
    "amount": 2500,
    "currency": "usd",
    "customerEmail": "railway@example.com",
    "customerName": "Railway Test",
    "description": "Happy path create"
  }'
if expect_status_in "$LAST_STATUS" 200 201 \
  && assert_json_field_nonempty "$LAST_BODY" "id" \
  && assert_json_field_equals "$LAST_BODY" "status" "requires_payment_method"; then
  PI_ID="$(python_json_get "$LAST_BODY" "id")"
  pass "create payment intent succeeded ($PI_ID)"
else
  fail "create payment intent failed"
fi
echo

# 5. Read payment intent
echo "-- Test 5: read payment intent"
http_request read_pi \
  -H "X-API-KEY: $API_KEY" \
  "$BASE_URL/api/payment_intents/$PI_ID"
if expect_status_in "$LAST_STATUS" 200 \
  && assert_json_field_equals "$LAST_BODY" "id" "$PI_ID" \
  && assert_json_field_equals "$LAST_BODY" "refundedAmount" "0"; then
  pass "read payment intent succeeded"
else
  fail "read payment intent failed"
fi
echo

# 6. Confirm payment happy path
echo "-- Test 6: confirm payment"
http_request confirm_pi \
  -X POST "$BASE_URL/api/payment_intents/$PI_ID/confirm" \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: $API_KEY" \
  -d '{
    "paymentMethodId": "pm_card_visa"
  }'
if expect_status_in "$LAST_STATUS" 200 201 \
  && assert_json_field_equals "$LAST_BODY" "status" "succeeded" \
  && assert_json_field_nonempty "$LAST_BODY" "latestCharge"; then
  pass "confirm payment succeeded"
else
  fail "confirm payment failed"
fi
echo

# 7. Confirm duplicate behavior
echo "-- Test 7: duplicate confirm behavior"
http_request confirm_pi_again \
  -X POST "$BASE_URL/api/payment_intents/$PI_ID/confirm" \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: $API_KEY" \
  -d '{
    "paymentMethodId": "pm_card_visa"
  }'
if expect_status_in "$LAST_STATUS" 200 201 409 400; then
  pass "duplicate confirm returned controlled response ($LAST_STATUS)"
else
  fail "duplicate confirm returned unexpected response"
fi
echo

# 8. Partial refund
echo "-- Test 8: partial refund"
http_request partial_refund \
  -X POST "$BASE_URL/api/refunds" \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: $API_KEY" \
  -d "{
    \"paymentIntentId\": \"$PI_ID\",
    \"amount\": 1000,
    \"reason\": \"requested_by_customer\"
  }"
if expect_status_in "$LAST_STATUS" 200 201 \
  && assert_json_field_equals "$LAST_BODY" "status" "succeeded" \
  && assert_json_field_equals "$LAST_BODY" "amount" "1000"; then
  pass "partial refund succeeded"
else
  fail "partial refund failed"
fi
echo

# 9. Verify partially refunded status
echo "-- Test 9: verify partial refund state"
http_request read_after_partial \
  -H "X-API-KEY: $API_KEY" \
  "$BASE_URL/api/payment_intents/$PI_ID"
if expect_status_in "$LAST_STATUS" 200 \
  && assert_json_field_equals "$LAST_BODY" "status" "partially_refunded" \
  && assert_json_field_equals "$LAST_BODY" "refundedAmount" "1000"; then
  pass "payment intent reflects partial refund"
else
  fail "payment intent did not reflect partial refund correctly"
fi
echo

# 10. Full refund remaining balance
echo "-- Test 10: refund remaining balance"
http_request full_refund \
  -X POST "$BASE_URL/api/refunds" \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: $API_KEY" \
  -d "{
    \"paymentIntentId\": \"$PI_ID\",
    \"amount\": 1500,
    \"reason\": \"requested_by_customer\"
  }"
if expect_status_in "$LAST_STATUS" 200 201 \
  && assert_json_field_equals "$LAST_BODY" "status" "succeeded" \
  && assert_json_field_equals "$LAST_BODY" "amount" "1500"; then
  pass "full refund remainder succeeded"
else
  fail "full refund remainder failed"
fi
echo

# 11. Verify fully refunded state
echo "-- Test 11: verify refunded state"
http_request read_after_full \
  -H "X-API-KEY: $API_KEY" \
  "$BASE_URL/api/payment_intents/$PI_ID"
if expect_status_in "$LAST_STATUS" 200 \
  && assert_json_field_equals "$LAST_BODY" "status" "refunded" \
  && assert_json_field_equals "$LAST_BODY" "refundedAmount" "2500"; then
  pass "payment intent reflects full refund"
else
  fail "payment intent did not reflect full refund correctly"
fi
echo

# 12. Over-refund rejection (fresh succeeded PI)
echo "-- Test 12: over-refund rejection"
http_request create_pi2 \
  -X POST "$BASE_URL/api/payment_intents" \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: $API_KEY" \
  -d '{
    "amount": 2200,
    "currency": "usd",
    "customerEmail": "overrefund@example.com",
    "customerName": "Over Refund",
    "description": "Over refund test"
  }'
PI2_ID="$(python_json_get "$LAST_BODY" "id")"

http_request confirm_pi2 \
  -X POST "$BASE_URL/api/payment_intents/$PI2_ID/confirm" \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: $API_KEY" \
  -d '{
    "paymentMethodId": "pm_card_visa"
  }'

http_request over_refund \
  -X POST "$BASE_URL/api/refunds" \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: $API_KEY" \
  -d "{
    \"paymentIntentId\": \"$PI2_ID\",
    \"amount\": 999999,
    \"reason\": \"requested_by_customer\"
  }"
if expect_status_in "$LAST_STATUS" 400 409; then
  pass "over-refund was rejected cleanly"
else
  fail "over-refund was not rejected as expected"
fi
echo

# 13. Refund already fully refunded payment
echo "-- Test 13: refund fully refunded payment"
http_request refund_fully_refunded \
  -X POST "$BASE_URL/api/refunds" \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: $API_KEY" \
  -d "{
    \"paymentIntentId\": \"$PI_ID\",
    \"amount\": 1,
    \"reason\": \"requested_by_customer\"
  }"
if expect_status_in "$LAST_STATUS" 400 409; then
  pass "fully refunded payment cannot be refunded again"
else
  fail "fully refunded payment accepted extra refund unexpectedly"
fi
echo

# 14. Refund before confirmation
echo "-- Test 14: refund before confirmation"
http_request create_pi3 \
  -X POST "$BASE_URL/api/payment_intents" \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: $API_KEY" \
  -d '{
    "amount": 1800,
    "currency": "usd",
    "customerEmail": "preconfirm@example.com",
    "customerName": "Pre Confirm",
    "description": "Refund before confirm"
  }'
PI3_ID="$(python_json_get "$LAST_BODY" "id")"

http_request refund_before_confirm \
  -X POST "$BASE_URL/api/refunds" \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: $API_KEY" \
  -d "{
    \"paymentIntentId\": \"$PI3_ID\",
    \"amount\": 500,
    \"reason\": \"requested_by_customer\"
  }"
if expect_status_in "$LAST_STATUS" 400 409; then
  pass "refund before confirm was rejected cleanly"
else
  fail "refund before confirm was not rejected as expected"
fi
echo

# 15. Invalid refund reason
echo "-- Test 15: invalid refund reason"
http_request invalid_refund_reason \
  -X POST "$BASE_URL/api/refunds" \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: $API_KEY" \
  -d "{
    \"paymentIntentId\": \"$PI2_ID\",
    \"amount\": 100,
    \"reason\": \"not_a_real_reason\"
  }"
if expect_status_in "$LAST_STATUS" 400 409; then
  pass "invalid refund reason handled cleanly"
else
  fail "invalid refund reason was not handled as expected"
fi
echo

# 16. Invalid refund amount zero
echo "-- Test 16: invalid refund amount zero"
http_request invalid_refund_zero \
  -X POST "$BASE_URL/api/refunds" \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: $API_KEY" \
  -d "{
    \"paymentIntentId\": \"$PI2_ID\",
    \"amount\": 0,
    \"reason\": \"requested_by_customer\"
  }"
if expect_status_in "$LAST_STATUS" 400 409; then
  pass "zero refund amount rejected"
else
  fail "zero refund amount was not rejected as expected"
fi
echo

# 17. Invalid refund amount negative
echo "-- Test 17: invalid refund amount negative"
http_request invalid_refund_negative \
  -X POST "$BASE_URL/api/refunds" \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: $API_KEY" \
  -d "{
    \"paymentIntentId\": \"$PI2_ID\",
    \"amount\": -5,
    \"reason\": \"requested_by_customer\"
  }"
if expect_status_in "$LAST_STATUS" 400 409; then
  pass "negative refund amount rejected"
else
  fail "negative refund amount was not rejected as expected"
fi
echo

# 18. Nonexistent payment intent lookup
echo "-- Test 18: nonexistent payment intent lookup"
http_request nonexistent_pi_lookup \
  -H "X-API-KEY: $API_KEY" \
  "$BASE_URL/api/payment_intents/pi_does_not_exist"
if expect_status_in "$LAST_STATUS" 404 400; then
  pass "nonexistent payment intent lookup handled cleanly"
else
  fail "nonexistent payment intent lookup was not handled as expected"
fi
echo

# 19. Nonexistent refund target
echo "-- Test 19: nonexistent refund target"
http_request nonexistent_refund_target \
  -X POST "$BASE_URL/api/refunds" \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: $API_KEY" \
  -d '{
    "paymentIntentId": "pi_does_not_exist",
    "amount": 100,
    "reason": "requested_by_customer"
  }'
if expect_status_in "$LAST_STATUS" 404 400; then
  pass "nonexistent refund target handled cleanly"
else
  fail "nonexistent refund target was not handled as expected"
fi
echo

# 20. Create validation: missing amount
echo "-- Test 20: create validation missing amount"
http_request create_missing_amount \
  -X POST "$BASE_URL/api/payment_intents" \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: $API_KEY" \
  -d '{
    "currency": "usd",
    "customerEmail": "railway@example.com",
    "customerName": "Railway Test"
  }'
if expect_status_in "$LAST_STATUS" 400 422; then
  pass "missing amount rejected"
else
  fail "missing amount was not rejected as expected"
fi
echo

# 21. Create validation: bad currency
echo "-- Test 21: create validation bad currency"
http_request create_bad_currency \
  -X POST "$BASE_URL/api/payment_intents" \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: $API_KEY" \
  -d '{
    "amount": 2500,
    "currency": "zzz",
    "customerEmail": "railway@example.com",
    "customerName": "Railway Test"
  }'
if expect_status_in "$LAST_STATUS" 400 422; then
  pass "bad currency rejected"
else
  fail "bad currency was not rejected as expected"
fi
echo

# 22. Create validation: zero amount
echo "-- Test 22: create validation zero amount"
http_request create_zero_amount \
  -X POST "$BASE_URL/api/payment_intents" \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: $API_KEY" \
  -d '{
    "amount": 0,
    "currency": "usd",
    "customerEmail": "railway@example.com",
    "customerName": "Railway Test"
  }'
if expect_status_in "$LAST_STATUS" 400 422; then
  pass "zero amount rejected"
else
  fail "zero amount was not rejected as expected"
fi
echo

# 23. Confirm with invalid payment method
echo "-- Test 23: confirm with invalid payment method"
http_request confirm_invalid_pm \
  -X POST "$BASE_URL/api/payment_intents/$PI3_ID/confirm" \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: $API_KEY" \
  -d '{
    "paymentMethodId": "pm_not_real"
  }'
if expect_status_in "$LAST_STATUS" 400 404 409; then
  pass "invalid payment method handled cleanly"
else
  fail "invalid payment method was not handled as expected"
fi
echo

# 24. UUID refund route test
echo "-- Test 24: UUID refund route"
if [[ -n "$INTERNAL_UUID" ]]; then
  http_request uuid_refund \
    -X POST "$BASE_URL/api/payment_intents/$INTERNAL_UUID/refund" \
    -H "Content-Type: application/json" \
    -H "X-API-KEY: $API_KEY" \
    -d '{
      "amount": 100,
      "reason": "requested_by_customer"
    }'
  if expect_status_in "$LAST_STATUS" 200 201 400 404 409; then
    pass "UUID refund route reached and returned controlled response ($LAST_STATUS)"
  else
    fail "UUID refund route returned unexpected response"
  fi
else
  skip "UUID refund route skipped because INTERNAL_UUID is not set"
fi
echo

# 25. Idempotency create behavior
echo "-- Test 25: idempotency create behavior"
IDEMP_VALUE="railway-test-$(date +%s)"
http_request idem_create_1 \
  -X POST "$BASE_URL/api/payment_intents" \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: $API_KEY" \
  -H "$IDEMPOTENCY_HEADER: $IDEMP_VALUE" \
  -d '{
    "amount": 2600,
    "currency": "usd",
    "customerEmail": "idem@example.com",
    "customerName": "Idem Test",
    "description": "Idempotency test"
  }'
IDEM1_ID="$(python_json_get "$LAST_BODY" "id")"
IDEM1_REPLAY="$(python_json_get "$LAST_BODY" "idempotentReplay")"

http_request idem_create_2 \
  -X POST "$BASE_URL/api/payment_intents" \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: $API_KEY" \
  -H "$IDEMPOTENCY_HEADER: $IDEMP_VALUE" \
  -d '{
    "amount": 2600,
    "currency": "usd",
    "customerEmail": "idem@example.com",
    "customerName": "Idem Test",
    "description": "Idempotency test"
  }'
IDEM2_ID="$(python_json_get "$LAST_BODY" "id")"
IDEM2_REPLAY="$(python_json_get "$LAST_BODY" "idempotentReplay")"

if expect_status_in "$LAST_STATUS" 200 201 && [[ -n "$IDEM1_ID" && -n "$IDEM2_ID" ]]; then
  if [[ "$IDEM1_ID" == "$IDEM2_ID" || "$IDEM2_REPLAY" == "True" || "$IDEM2_REPLAY" == "true" ]]; then
    pass "idempotency appears to be working"
  else
    fail "idempotency test did not show replay/same resource"
  fi
else
  fail "idempotency test requests failed"
fi
echo

# 26. Webhook invalid payload/signature
echo "-- Test 26: webhook invalid payload/signature"
http_request webhook_invalid \
  -X POST "$BASE_URL/api/webhooks/stripe" \
  -H "Content-Type: application/json" \
  -d '{}'
if expect_status_in "$LAST_STATUS" 400 401 403; then
  pass "webhook invalid payload/signature rejected cleanly"
else
  fail "webhook invalid payload/signature was not rejected as expected"
fi
echo

echo "== Summary =="
echo "Passed: $PASS_COUNT"
echo "Failed: $FAIL_COUNT"
echo "Skipped: $SKIP_COUNT"

if [[ $FAIL_COUNT -gt 0 ]]; then
  exit 1
fi
