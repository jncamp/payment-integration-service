# JUnit Test Suite

This project includes focused tests for the authentication and controller layer.

## Run all tests

```bash
mvn test
```

## Current coverage

- `JwtServiceTest`
  - token generation
  - username extraction
  - invalid token rejection

- `AuthControllerTest`
  - successful demo login returns a JWT
  - bad credentials return `401 Unauthorized`

- `PaymentIntentControllerSecurityTest`
  - protected payment intent endpoint rejects unauthenticated requests
  - valid `X-API-Key` authenticates successfully
  - valid `Authorization: Bearer <jwt>` authenticates successfully
  - idempotent replay returns `200 OK`
  - validation errors use Stripe-style error JSON

- `RefundControllerSecurityTest`
  - protected refund endpoint rejects unauthenticated requests
  - valid `X-API-Key` authenticates successfully
  - missing `paymentIntentId` returns Stripe-style validation error JSON

## Notes

These tests mock `PaymentService`, so they do not call Stripe, Railway, or Postgres.
They are safe to run locally and in CI.
