# Phase 1 Upgrade Notes

This package upgrades the project from a single `payments` table model to a more client-grade schema.

## Added schema
- `api_clients`
- `customers`
- `payment_intents`
- `charges`
- `refunds`
- `webhook_events`

## Added migration
- `src/main/resources/db/migration/V5__client_grade_payment_model.sql`

## Code changes
- New JPA entities for the new schema
- New repositories for the new schema
- `PaymentService` refactored to use `payment_intents`, `charges`, `refunds`, and `webhook_events`
- Existing controllers and endpoints kept in place to reduce breakage
- Legacy data from `payments` is migrated into the new tables in V5

## Important note
This is a practical Phase 1 refactor intended to move your project onto a stronger schema while preserving the current API surface. It is not yet a full multi-tenant production rewrite.

## Recommended next step
- Add integration tests for create / confirm / refund / webhook flows
- Remove legacy `payments` entity usage entirely in Phase 2
- Improve webhook event payload persistence and replay handling
- Add API-client aware auth instead of a single global API key
