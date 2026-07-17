-- Per-tenant-schema Stripe columns (idempotent). search_path is set by the migration runner.

-- A fee invoice paid via Stripe stores its Stripe invoice id (hosted page + sync).
ALTER TABLE fee_invoice ADD COLUMN IF NOT EXISTS stripe_invoice_id VARCHAR(64);

-- 'stripe' joins the allowed payment methods.
ALTER TABLE fee_payment DROP CONSTRAINT IF EXISTS fee_payment_method_check;
ALTER TABLE fee_payment ADD CONSTRAINT fee_payment_method_check
    CHECK (method IN ('cash','transfer','paystack','stripe'));
