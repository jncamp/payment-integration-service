# Real Stripe Payment Flow Patch

This patch changes `/api/payments/create` so it no longer auto-succeeds a payment.

## New behavior
- `POST /api/payments/create` creates a Stripe PaymentIntent only
- the response should include a `clientSecret`
- the initial status should be `REQUIRES_PAYMENT_METHOD` or `REQUIRES_CONFIRMATION`
- confirming the payment now belongs to `POST /api/payment_intents/{id}/confirm`

## Intended flow
1. Create payment intent
2. Collect payment method in frontend using Stripe Elements / Payment Element
3. Confirm the payment intent
4. Listen for Stripe webhooks for final state changes
