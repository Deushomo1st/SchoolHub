-- Stripe integration columns (idempotent).
-- Platform schema: run once. Tenant part (below the marker) is run per tenant schema
-- by setup/Migrate-StripeColumns.ps1.

-- Payers (students/guardians) get one Stripe Customer each.
ALTER TABLE platform.app_user ADD COLUMN IF NOT EXISTS stripe_customer_id VARCHAR(64);

-- Platform Billing: each plan maps to a Stripe Product + recurring Price (NGN).
ALTER TABLE platform.subscription_plan ADD COLUMN IF NOT EXISTS stripe_product_id VARCHAR(64);
ALTER TABLE platform.subscription_plan ADD COLUMN IF NOT EXISTS stripe_price_id   VARCHAR(64);

-- Each school is a Stripe Customer with (at most) one subscription.
ALTER TABLE platform.tenant ADD COLUMN IF NOT EXISTS stripe_customer_id     VARCHAR(64);
ALTER TABLE platform.tenant ADD COLUMN IF NOT EXISTS stripe_subscription_id VARCHAR(64);
ALTER TABLE platform.tenant ADD COLUMN IF NOT EXISTS stripe_sub_status      VARCHAR(32);
