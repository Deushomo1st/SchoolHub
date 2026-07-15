package com.schoolhub.schoolservice.payment;

/**
 * The payment PORT. FeeService depends on this interface, never on a concrete gateway.
 * Providers (Paystack, Flutterwave, Stripe...) are ADAPTERS selected by config, so adding a
 * country/provider is a config change, not a domain rewrite.
 *
 * Non-negotiables enforced by callers, not adapters:
 *   - the browser redirect is never trusted; server-side verify() is the source of truth
 *   - references are idempotent at the DB level (see uq_payment_paystack_ref)
 */
public interface PaymentProvider {

    /** Provider key stored on the payment row (paystack | manual). */
    String key();

    /** Begin a charge: returns a reference the caller can resume/verify. */
    InitResult initialize(int amountNaira, String description);

    /** Server-side verification - the ONLY source of truth that money actually moved. */
    VerifyResult verify(String reference);

    /** Reverse a settled charge where the provider supports it. */
    RefundResult refund(String reference, int amountNaira);

    record InitResult(String reference) {}
    record VerifyResult(boolean success, String reference) {}
    record RefundResult(boolean success, String reference) {}
}
