# Payment Integration API (Stripe-Style)

A production-style payment processing API built with Java Spring Boot and PostgreSQL, designed to mimic Stripe’s Payment Intent workflow.

## 🚀 Features

* Stripe-style Payment Intent lifecycle

  * create → confirm → retrieve
* Idempotent request handling (prevents duplicate payments)
* Refund support
* Webhook simulation
* API key security
* PostgreSQL persistence with Flyway migrations
* Deployed to cloud (Railway)

---

## 🧪 API Endpoints

### Create Payment Intent

POST /api/payment_intents

```bash
curl -X POST https://payment-integration-service-production-18ae.up.railway.app/api/payment_intents \
-H "Content-Type: application/json" \
-H "X-API-KEY: change-me-dev-key" \
-H "Idempotency-Key: demo-123" \
-d '{
  "amount": 1000,
  "currency": "usd",
  "customerName": "John Doe",
  "customerEmail": "john@example.com"
}'
```

Response:

```json
{
  "id": "pi_123",
  "object": "payment_intent",
  "status": "requires_confirmation",
  "clientSecret": "pi_123_secret_abc"
}
```

---

### Confirm Payment Intent

POST /api/payment_intents/{id}/confirm

```bash
curl -X POST https://payment-integration-service-production-18ae.up.railway.app/api/payment_intents/pi_123/confirm \
-H "X-API-KEY: change-me-dev-key"
```

---

### Get Payment Intent

GET /api/payment_intents/{id}

```bash
curl https://payment-integration-service-production-18ae.up.railway.app/api/payment_intents/pi_123 \
-H "X-API-KEY: change-me-dev-key"
```

---

### Refund

POST /api/refunds

```bash
curl -X POST https://payment-integration-service-production-18ae.up.railway.app/api/refunds \
-H "Content-Type: application/json" \
-H "X-API-KEY: change-me-dev-key" \
-d '{
  "paymentIntentId": "pi_123",
  "amount": 1000
}'
```

---

### Webhook Simulation

POST /api/webhooks/stripe

```bash
curl -X POST https://payment-integration-service-production-18ae.up.railway.app/api/webhooks/stripe \
-H "Content-Type: application/json" \
-d '{
  "id": "evt_test_123",
  "type": "payment_intent.succeeded",
  "data": {
    "object": {
      "id": "pi_123",
      "status": "succeeded"
    }
  }
}'
```

---

## 🧠 Architecture

Controller → Service → Repository → PostgreSQL

* Spring Boot (REST API)
* JPA / Hibernate (ORM)
* Flyway (database migrations)
* Railway (cloud deployment)

---

## 💡 Why this project?

This project demonstrates real-world backend capabilities:

* Payment processing workflows
* API design and validation
* Database integration
* Idempotency and reliability
* Production deployment

---

## 👤 Author

John Camp
Senior Java Developer (20+ years experience)
