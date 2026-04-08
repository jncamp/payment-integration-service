# Payment Integration Service (Production-Grade Demo)

A production-style payment backend built with **Java 17 + Spring Boot**, demonstrating real-world patterns used in payment systems such as Stripe and PayPal.

This project showcases **idempotent payment processing, webhook handling, API security, and clean architecture** — designed to be used as a portfolio piece for backend/API integration work.

---

## 🚀 Features

### Core Payment Functionality

* Create payment
* Refund payment
* Retrieve payment by ID

### Production-Grade Capabilities

* **Idempotency (Header-based)**
  Prevents duplicate charges using `Idempotency-Key` header
* **Correct HTTP Semantics**

    * `201 Created` for new payments
    * `200 OK` for idempotent replays
* **Webhook Processing**
  Handles async payment provider events
* **Webhook Signature Validation Pattern**
  Validates requests using raw payload + signature header
* **API Key Security**
  Protects endpoints using `X-API-Key` header
* **Database Migrations (Flyway)**
  Version-controlled schema changes
* **Clean Architecture**

    * Controller → Service → Repository → Provider

---

## 🏗️ Tech Stack

* Java 17
* Spring Boot 3
* Spring Data JPA (Hibernate)
* PostgreSQL
* Flyway
* Maven

---

## 🔐 Security

### API Key Protection

All endpoints (except webhooks) require:

```
X-API-Key: change-me-dev-key
```

Configured via:

```yaml
app:
  security:
    api-key: ${APP_API_KEY:change-me-dev-key}
```

---

## 🔁 Idempotency

Use header:

```
Idempotency-Key: your-unique-key
```

### Behavior:

* First request → creates payment (`201`)
* Second request (same key) → returns existing payment (`200`)
* No duplicate charges

---

## 📡 API Endpoints

### Create Payment

```bash
curl -i -X POST http://localhost:8081/api/payments/create \
-H "Content-Type: application/json" \
-H "X-API-Key: change-me-dev-key" \
-H "Idempotency-Key: test-key-1" \
-d '{
  "amount": 1700,
  "currency": "USD",
  "customerEmail": "user@example.com",
  "customerName": "John Doe",
  "description": "Test payment"
}'
```

---

### Get Payment

```bash
curl http://localhost:8081/api/payments/{id}
```

---

### Refund Payment

```bash
curl -X POST http://localhost:8081/api/payments/{id}/refund \
-H "Content-Type: application/json" \
-H "X-API-Key: change-me-dev-key" \
-d '{
  "reason": "Customer request"
}'
```

---

### Webhook Endpoint

```bash
curl -X POST http://localhost:8081/api/webhooks/payment-provider \
-H "Content-Type: application/json" \
-H "X-Webhook-Signature: testsig" \
-d '{
  "providerPaymentId": "mock_pay_123",
  "eventType": "payment.refunded"
}'
```

Webhook endpoint is intentionally **not protected by API key**, but instead uses signature validation.

---

## 🔄 Webhook Events Supported

* `payment.succeeded`
* `payment.failed`
* `payment.refunded`

---

## 🧠 Design Highlights

### Idempotency

Ensures safe retries and prevents duplicate charges — a critical requirement in payment systems.

### Separation of Concerns

* Controllers handle HTTP
* Services handle business logic
* Providers simulate external payment gateways

### Extensibility

Mock provider can be replaced with:

* Stripe
* PayPal
* Square

---

## ⚙️ Running Locally

### Prerequisites

* Java 17
* PostgreSQL running locally

### Start application

```bash
./mvnw spring-boot:run
```

---

## 📦 Database

* PostgreSQL
* Managed via Flyway migrations
* Schema auto-versioned

---

## 🌐 Deployment (Railway Ready)

This project is designed for easy deployment on Railway:

1. Push to GitHub
2. Create Railway project
3. Add PostgreSQL service
4. Set environment variables:

    * `APP_API_KEY`
    * `DATABASE_URL`
    * `DB_USERNAME`
    * `DB_PASSWORD`
5. Deploy

---

## 💼 Why This Matters (For Clients)

This project demonstrates:

* Safe payment handling (idempotency)
* Secure API design
* Real-world webhook patterns
* Production-ready architecture
* Experience with payment gateway integration concepts

---

## 📬 Contact

Available for:

* Payment gateway integrations (Stripe, PayPal, etc.)
* Backend API development
* Spring Boot microservices
* Production issue debugging

---

## 📄 License

MIT
