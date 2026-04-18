# 🚀 Payment Integration Service

> Production-grade Spring Boot payment API integrating with Stripe, deployed live on Railway with PostgreSQL persistence, Flyway migrations, refund workflows, idempotency support, and battle-tested API validation.

![Java](https://img.shields.io/badge/Java-17-blue)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-green)
![Stripe](https://img.shields.io/badge/Stripe-Integrated-purple)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Database-blue)
![Railway](https://img.shields.io/badge/Railway-Deployed-black)
![Tests](https://img.shields.io/badge/Test_Battery-25%2F25-success)

---

# 🔥 Live Production Deployment

**Base URL**

```text
https://payment-integration-service-production-18ae.up.railway.app
```

**Health Check**

```bash
curl https://payment-integration-service-production-18ae.up.railway.app/actuator/health \
-H "X-API-KEY: <API_KEY>"
```

---

# ✅ Fully Verified End-to-End

Automated production battery against Railway:

```text
Passed: 25
Failed: 0
Skipped: 1 (internal UUID route only)
```

## Validated Scenarios

### Payments
- Create payment intent
- Confirm payment using Stripe test card
- Retrieve payment status
- Duplicate confirm safety

### Refunds
- Partial refund
- Full refund
- Status transitions:
    - succeeded
    - partially_refunded
    - refunded
- Over-refund rejection
- Refund-before-confirm rejection

### Security
- Missing API key rejected
- Invalid API key rejected

### Validation
- Invalid refund reason → 400
- Invalid currency → 400
- Invalid payment method handled
- Missing required fields rejected

### Reliability
- Idempotency behavior verified
- Webhook invalid payload rejected cleanly

---

# ⚡ API Examples

## Create Payment Intent

```bash
curl -X POST $BASE_URL/api/payment_intents \
-H "Content-Type: application/json" \
-H "X-API-KEY: <API_KEY>" \
-d '{
  "amount": 2500,
  "currency": "usd",
  "customerEmail": "customer@example.com",
  "customerName": "Jane Doe",
  "description": "Order #1001"
}'
```

## Confirm Payment

```bash
curl -X POST $BASE_URL/api/payment_intents/{id}/confirm \
-H "Content-Type: application/json" \
-H "X-API-KEY: <API_KEY>" \
-d '{
  "paymentMethodId": "pm_card_visa"
}'
```

## Refund Payment

```bash
curl -X POST $BASE_URL/api/refunds \
-H "Content-Type: application/json" \
-H "X-API-KEY: <API_KEY>" \
-d '{
  "paymentIntentId": "{id}",
  "amount": 1000,
  "reason": "requested_by_customer"
}'
```

---

# 📦 Example Response

```json
{
  "id": "pi_123",
  "amount": 2500,
  "currency": "usd",
  "status": "succeeded",
  "refundedAmount": 0
}
```

---

# 🧠 Architecture

```text
Client App
   ↓
Spring Boot REST API
   ↓
Service Layer
   ├── Stripe SDK
   ├── PostgreSQL
   └── Flyway Migrations
```

---

# 🛡 Error Handling

Consistent JSON errors:

```json
{
  "error": {
    "type": "invalid_request_error",
    "message": "Unsupported refund reason",
    "code": "request_error"
  }
}
```

### HTTP Semantics

| Code | Meaning |
|------|---------|
| 400 | Invalid client input |
| 401/403 | Unauthorized |
| 404 | Resource not found |
| 409 | Business rule conflict |
| 502 | Upstream provider issue |

---

# 🛠 Local Development

## Requirements

- Java 17+
- Maven
- PostgreSQL
- Stripe test keys

## Run

```bash
mvn spring-boot:run
```

## Execute Full Test Battery

```bash
API_KEY="test123" ./railway_payment_battery.sh
```

---

# 🏆 Why This Project Stands Out

This is not a CRUD demo.

It demonstrates:

- Real-world third-party payment integration
- Stateful transaction workflows
- Idempotent API design
- Validation-first architecture
- Cloud deployment experience
- Database migration discipline
- Production debugging
- Automated end-to-end testing

---

# 🚀 Future Upgrades

- Swagger / OpenAPI docs
- Rate limiting
- Retry queues
- Webhook reconciliation workers
- Multi-provider abstraction (Stripe / PayPal / Adyen)
- Metrics + tracing dashboards
- Admin portal

---

## 👨‍💻 Author

**John Camp**

Built as a serious backend portfolio project focused on production payments engineering.