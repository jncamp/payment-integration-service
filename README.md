# Payment Integration Service

A Spring Boot payment integration demo with full Stripe support including webhooks.

---

## Features

- Create PaymentIntent
- Confirm PaymentIntent
- Refund PaymentIntent
- Stripe Webhook Handling (real, verified)
- PostgreSQL + Flyway
- API Key Security
- Local + Railway Deployment

---

## Environment Variables

```bash
STRIPE_SECRET_KEY=sk_test_your_key
STRIPE_WEBHOOK_SECRET=whsec_your_webhook_secret
APP_API_KEY=change-me-dev-key
```

---

## Base URL

Local:
```bash
http://localhost:8080
```

Railway:
```bash
https://payment-integration-service-production-18ae.up.railway.app
```

---

## Authentication

```bash
-H "X-API-Key: change-me-dev-key"
```

---

# API Endpoints

## Create PaymentIntent

```bash
curl -X POST "$BASE_URL/api/payment_intents" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-001" \
  -H "X-API-Key: change-me-dev-key" \
  -d '{"amount":2000,"currency":"usd","customerName":"John Camp","customerEmail":"john@example.com"}'
```

---

## Confirm PaymentIntent

```bash
curl -X POST "$BASE_URL/api/payment_intents/pi_123/confirm" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: change-me-dev-key" \
  -d '{"paymentMethodId":"pm_card_visa"}'
```

---

## Refund

```bash
curl -X POST "$BASE_URL/api/refunds" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: change-me-dev-key" \
  -d '{"paymentIntentId":"pi_123"}'
```

---

# Webhooks (IMPORTANT)

## Start Stripe CLI

```bash
stripe listen --forward-to localhost:8080/api/webhooks/stripe
```

Copy the webhook secret:

```bash
whsec_...
```

Add it to your environment:

```bash
STRIPE_WEBHOOK_SECRET=whsec_...
```

Restart the app.

---

## Trigger Test Event

```bash
stripe trigger payment_intent.succeeded
```

---

## Expected Output

```bash
--> payment_intent.succeeded
<-- [200] POST /api/webhooks/stripe
```

---

## Flow

```text
Stripe CLI -> Spring Boot Webhook -> PaymentService -> Database
```

---

## Notes

- Webhook signature is verified using Stripe SDK
- Secret keys are never exposed to clients
- Designed for real-world backend payment systems

---

## Status

- Payments: Working
- Refunds: Working
- Webhooks: Working
- Railway Deployment: Working
- 