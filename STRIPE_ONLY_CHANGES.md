# Stripe-only refactor

This package updates the project to favor Stripe-only flows:

- `/api/payments/create` now creates a Stripe PaymentIntent instead of using the mock provider.
- Optional `paymentMethodId` was added to `CreatePaymentRequest` so `/api/payments/create` can create-and-confirm in one call.
- `PaymentResponse` now includes `clientSecret`.
- `/api/webhooks/payment-provider` is disabled and returns 410 Gone. Use `/api/webhooks/stripe`.
- Refunds from `/api/payments/{id}/refund` now delegate to the Stripe refund flow.

Recommended routes:

- `POST /api/payment_intents`
- `POST /api/payment_intents/{id}/confirm`
- `POST /api/refunds`
- `POST /api/webhooks/stripe`

Compatibility note:

The old mock classes still exist in the source tree, but the main service path no longer uses them.
