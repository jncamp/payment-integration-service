# Architecture Cleanup Notes

This build continues the incremental, battery-tested cleanup of the payment integration service.

## Completed phases

1. Initial cleanup and documentation
2. Stripe gateway extraction groundwork
3. Webhook service extraction
4. Payment response mapper extraction
5. Confirm payment service extraction
6. Webhook package migration
7. Refund package migration
8. Payment package migration
9. Shared error package migration

## Current structure direction

```text
com.example.payment
├── config
├── controller              # remaining general/demo/auth controllers
├── dto                     # remaining shared/legacy DTOs
├── entity                  # remaining shared persistence entities
├── enums
├── payment                 # payment feature package
├── provider                # provider/gateway boundary
├── refund                  # refund feature package
├── repository              # shared repositories still being migrated gradually
├── security
├── shared
│   └── error               # centralized exception + API error handling
└── webhook                 # webhook feature package
```

## Phase 8 / shared error cleanup

Moved the global error-handling classes from:

```text
com.example.payment.exception
```

to:

```text
com.example.payment.shared.error
```

This is a safer long-term package because these classes are shared by payment, refund, webhook, provider, and auth flows.

Classes moved:

- `ApiException`
- `GlobalExceptionHandler`
- `ValidationErrorResponse`

Behavior should be unchanged. The public API error shapes are preserved for the existing batteries.

## Phase 9 - Payment Provider Gateway Interface

Introduced `PaymentProviderGateway` as the service-facing provider boundary.

Business services now depend on the provider interface rather than the concrete
`StripePaymentGatewayClient` implementation. Stripe remains the active provider,
but the application now has a cleaner seam for future providers such as PayPal,
Square, or Authorize.Net.

Current provider flow:

```text
PaymentService / ConfirmPaymentService / RefundService
        -> PaymentProviderGateway
        -> StripePaymentGatewayClient
        -> Stripe SDK
```

This keeps endpoint behavior unchanged while reducing direct dependency on the
concrete Stripe adapter.
