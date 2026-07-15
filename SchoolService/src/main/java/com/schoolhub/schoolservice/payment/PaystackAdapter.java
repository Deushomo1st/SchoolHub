package com.schoolhub.schoolservice.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Paystack ADAPTER - Nigeria's default gateway.
 *
 * Ships simulated (no live secret key required for a demo): initialize() mints a PSK- reference
 * and verify() confirms any well-formed one. To go live, replace the method bodies with real
 * Paystack REST calls (POST /transaction/initialize, GET /transaction/verify/{ref}, POST /refund)
 * using schoolhub.paystack.secret - the PORT contract and every call site stay identical.
 */
@Component
public class PaystackAdapter implements PaymentProvider {

    private final boolean live;

    public PaystackAdapter(@Value("${schoolhub.paystack.secret:}") String secretKey) {
        this.live = secretKey != null && !secretKey.isBlank();
    }

    @Override
    public String key() { return "paystack"; }

    @Override
    public InitResult initialize(int amountNaira, String description) {
        // Live: POST https://api.paystack.co/transaction/initialize -> authorization_url + reference.
        return new InitResult("PSK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    @Override
    public VerifyResult verify(String reference) {
        // Live: GET https://api.paystack.co/transaction/verify/{reference}; success iff data.status == "success".
        // Simulated: a well-formed PSK- reference is treated as settled.
        return new VerifyResult(reference != null && reference.startsWith("PSK-"), reference);
    }

    @Override
    public RefundResult refund(String reference, int amountNaira) {
        // Live: POST https://api.paystack.co/refund { transaction: reference, amount: amountNaira*100 }.
        return new RefundResult(true, reference);
    }

    /** Whether a live secret key is configured (false = simulated demo mode). */
    public boolean isLive() { return live; }
}
