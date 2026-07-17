package com.schoolhub.schoolservice.stripe;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Invoice;
import com.stripe.model.InvoiceItem;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.InvoiceCreateParams;
import com.stripe.param.InvoiceItemCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Thin Stripe wrapper for fee invoicing (NGN, hosted invoice page). All amounts
 * are naira and converted to kobo (x100) at this boundary — the app never deals
 * in Stripe minor units anywhere else.
 */
@Component
public class StripeGateway {

    private final boolean enabled;

    public StripeGateway(@Value("${stripe.secret-key:}") String secretKey) {
        this.enabled = secretKey != null && !secretKey.isBlank();
        if (enabled) Stripe.apiKey = secretKey;
    }

    public boolean enabled() { return enabled; }

    /** Reuse the stored Customer or create one for this payer. */
    public String ensureCustomer(String existingId, String email, String name) throws StripeException {
        if (existingId != null && !existingId.isBlank()) return existingId;
        Customer c = Customer.create(CustomerCreateParams.builder()
                .setEmail(email)
                .setName(name)
                .build());
        return c.getId();
    }

    /**
     * Create + finalize an NGN invoice for the outstanding amount and return its
     * hosted payment page. Metadata carries {schema, feeInvoiceId} so webhooks can
     * route back to the right tenant.
     */
    public Invoice createHostedInvoice(String customerId, int amountNaira, String description,
                                       Map<String, String> metadata) throws StripeException {
        InvoiceItem.create(InvoiceItemCreateParams.builder()
                .setCustomer(customerId)
                .setAmount(amountNaira * 100L)          // naira → kobo
                .setCurrency("ngn")
                .setDescription(description)
                .build());
        Invoice inv = Invoice.create(InvoiceCreateParams.builder()
                .setCustomer(customerId)
                .setCollectionMethod(InvoiceCreateParams.CollectionMethod.SEND_INVOICE)
                .setDaysUntilDue(7L)
                .setPendingInvoiceItemsBehavior(InvoiceCreateParams.PendingInvoiceItemsBehavior.INCLUDE)
                .putAllMetadata(metadata)
                .build());
        return inv.finalizeInvoice();
    }

    public Invoice retrieveInvoice(String id) throws StripeException {
        return Invoice.retrieve(id);
    }

    /** Void an open invoice (amount changed, e.g. a cash part-payment landed). Best-effort. */
    public void voidInvoice(Invoice inv) {
        try { inv.voidInvoice(); } catch (StripeException ignored) { /* stale/raced — a new one is created anyway */ }
    }
}
