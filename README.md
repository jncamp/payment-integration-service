# Payment Integration Service

Railway-ready Java Spring Boot demo showing a payment integration workflow with validation, persistence, refund handling, webhook processing, and clean error responses.

## What this project demonstrates

- Java 17 + Spring Boot backend design
- PostgreSQL persistence with Flyway migrations
- Payment creation endpoint
- Refund endpoint
- Webhook endpoint with simple signature verification
- Clean validation and error handling
- Docker-ready deployment for Railway

## Important note

This starter uses a **mock payment gateway client** so you can run and demonstrate the workflow without live gateway credentials. The code is structured so you can later swap in Stripe or another provider by replacing `MockPaymentGatewayClient` with a real implementation.

## API Endpoints

### Create payment

`POST /api/payments/create`

Example body:

```json
{
  "amount": 49.99,
  "currency": "USD",
  "customerEmail": "john@example.com",
  "customerName": "John Camp"
}
```

### Get payment

`GET /api/payments/{id}`

### Refund payment

`POST /api/payments/{id}/refund`

Example body:

```json
{
  "reason": "Customer requested refund"
}
```

### Webhook

`POST /api/webhooks/payment-provider`

Required header:

`X-Webhook-Signature: demo-secret`

Example body:

```json
{
  "providerPaymentId": "mock_pay_xxx",
  "eventType": "payment.refunded"
}
```

## Local development

### 1. Start Postgres

Example Docker command:

```bash
docker run --name payment_demo_db \
  -e POSTGRES_DB=payment_demo \
  -e POSTGRES_USER=ecommerce \
  -e POSTGRES_PASSWORD=ecommerce \
  -p 5432:5432 \
  -d postgres:16
```

### 2. Run the app

```bash
./mvnw spring-boot:run
```

or if Maven is installed:

```bash
mvn spring-boot:run
```

### 3. Test with curl

Create payment:

```bash
curl -i -X POST http://localhost:8080/api/payments/create \
-H "Content-Type: application/json" \
-d '{
  "amount":49.99,
  "currency":"USD",
  "customerEmail":"john@example.com",
  "customerName":"John Camp"
}'
```

Refund payment:

```bash
curl -i -X POST http://localhost:8080/api/payments/{id}/refund \
-H "Content-Type: application/json" \
-d '{
  "reason":"Customer requested refund"
}'
```

Webhook update:

```bash
curl -i -X POST http://localhost:8080/api/webhooks/payment-provider \
-H "Content-Type: application/json" \
-H "X-Webhook-Signature: demo-secret" \
-d '{
  "providerPaymentId":"mock_pay_xxx",
  "eventType":"payment.refunded"
}'
```

## Railway deployment notes

Set these environment variables in Railway:

- `DATABASE_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `PAYMENT_WEBHOOK_SECRET`
- `PORT` (Railway usually injects this automatically)

## Good next upgrades

- Replace mock client with Stripe integration
- Add provider signature verification using the provider SDK
- Add OpenAPI / Swagger
- Add tests for service and controller layers
- Add idempotency keys for payment creation
