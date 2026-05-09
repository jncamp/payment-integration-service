# Payment Integration Service

A production-style payment orchestration backend built with Java 17, Spring Boot, Stripe, PostgreSQL, and Docker.

This project demonstrates modern backend engineering practices including:

* Clean architecture
* Feature-based package structure
* Payment provider abstraction
* Stripe PaymentIntent integration
* Idempotent payment creation
* Refund orchestration
* Webhook processing
* JWT authentication
* Centralized error handling
* OpenAPI / Swagger documentation
* Dockerized PostgreSQL
* Continuous regression testing via automated battery scripts

---

# Features

## Payments

* Create PaymentIntents
* Retrieve PaymentIntents
* Confirm PaymentIntents
* Idempotent create requests
* Duplicate confirm protection
* Metadata support
* Currency validation

## Refunds

* Partial refunds
* Full refunds
* Refund reason validation
* Refund status tracking

## Webhooks

* Stripe webhook endpoint
* Signature verification support
* Payment success processing
* Refund event handling
* Event persistence

## Security

* JWT authentication
* API-key protected endpoints
* Role-ready security architecture

## Architecture

* Feature-based package structure
* Provider abstraction layer
* Service orchestration layer
* Dedicated webhook service
* Dedicated confirmation service
* Shared error architecture
* DTO/mapper separation

---

# Tech Stack

| Technology        | Purpose                        |
| ----------------- | ------------------------------ |
| Java 17           | Backend runtime                |
| Spring Boot 3     | REST API framework             |
| Spring Security   | Authentication / authorization |
| Stripe SDK        | Payment processing             |
| PostgreSQL        | Persistence                    |
| Flyway            | Database migrations            |
| Docker            | Database containerization      |
| OpenAPI / Swagger | API documentation              |
| Maven             | Build system                   |
| Railway           | Cloud deployment               |

---

# Project Structure

```text
src/main/java/com/example/payment/

auth/
payment/
refund/
webhook/
shared/
```

Example feature structure:

```text
payment/
    controller/
    service/
    gateway/
    dto/
    mapper/
```

---

# Architecture Highlights

## Provider Abstraction

The system uses a provider abstraction layer to isolate business logic from payment providers.

```text
Controller
    ↓
Orchestration Service
    ↓
PaymentProviderGateway
    ↓
StripePaymentGatewayClient
```

This architecture enables future support for:

* PayPal
* Square
* Authorize.Net
* Mock/Test providers
* Multi-tenant provider selection

---

# API Endpoints

## Authentication

### Login

```http
POST /api/auth/login
```

---

## Payment Intents

### Create PaymentIntent

```http
POST /api/payment_intents
```

### Retrieve PaymentIntent

```http
GET /api/payment_intents/{id}
```

### Confirm PaymentIntent

```http
POST /api/payment_intents/{id}/confirm
```

---

## Refunds

### Create Refund

```http
POST /api/refunds
```

---

## Webhooks

### Stripe Webhook Endpoint

```http
POST /api/webhooks/stripe
```

---

# Example Requests

## Create PaymentIntent

```bash
curl -X POST http://localhost:8080/api/payment_intents \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: change-me-dev-key" \
  -d '{
    "amount": 1000,
    "currency": "usd",
    "customerName": "John Doe",
    "customerEmail": "john@example.com",
    "description": "Test Payment"
  }'
```

---

## Confirm PaymentIntent

```bash
curl -X POST http://localhost:8080/api/payment_intents/pi_xxx/confirm \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: change-me-dev-key" \
  -d '{
    "paymentMethodId": "pm_card_visa"
  }'
```

---

## Create Refund

```bash
curl -X POST http://localhost:8080/api/refunds \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: change-me-dev-key" \
  -d '{
    "paymentIntentId": "pi_xxx",
    "amount": 500,
    "reason": "requested_by_customer"
  }'
```

---

# Local Development

## Prerequisites

* Java 17
* Maven
* Docker
* PostgreSQL
* Stripe test account

---

## Environment Variables

```bash
export STRIPE_SECRET_KEY="sk_test_your_key"
export APP_API_KEY="change-me-dev-key"
export USERNAME="admin"
export PASSWORD="password"
```

---

## Start PostgreSQL

```bash
docker compose up -d
```

---

## Run Application

```bash
mvn spring-boot:run
```

---

# Swagger / OpenAPI

Swagger UI:

```text
http://localhost:8080/swagger-ui/index.html
```

OpenAPI Docs:

```text
http://localhost:8080/v3/api-docs
```

---

# Automated Regression Batteries

The project includes automated battery scripts for regression validation.

Battery coverage includes:

* Health endpoint
* OpenAPI availability
* JWT authentication
* API-key validation
* PaymentIntent creation
* Retrieval flow
* Confirmation flow
* Duplicate confirmation handling
* Partial refunds
* Full refunds
* Invalid currency rejection
* Invalid refund reason rejection

Example:

```bash
API_KEY="change-me-dev-key" USERNAME="admin" PASSWORD="password" ./local_payment_battery.sh
```

---

# Railway Deployment

The application has been validated on Railway cloud deployment.

Deployment includes:

* PostgreSQL
* Stripe integration
* Health checks
* Environment-based configuration

---

# Future Enhancements

Planned architectural improvements:

* PayPal provider support
* Async webhook processing
* Redis idempotency layer
* Event-driven processing
* Dead-letter retry queues
* Distributed tracing
* Metrics/observability
* SDK generation from OpenAPI
* Integration test containers

---

# Why This Project Exists

This project was built to demonstrate:

* Production-style backend engineering
* Clean payment architecture
* Incremental refactoring discipline
* Continuous regression validation
* Provider abstraction design
* Real-world payment workflows

The architecture intentionally evolved through multiple verified refactor phases while maintaining behavioral compatibility.

---

# License

MIT License
