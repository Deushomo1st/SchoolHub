package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.service.FeeService;
import com.schoolhub.schoolservice.tenant.TenantContext;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Stripe → SchoolHub. Signature-verified; the invoice's metadata ({schema, feeInvoiceId})
 * routes the event to the right tenant schema. Lazy sync in listInvoices/forYou covers
 * dev runs where no webhook forwarder (stripe listen) is active — this endpoint makes
 * payment recording immediate when one is.
 */
@RestController
@RequestMapping("/api/v1/stripe")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final FeeService fees;
    private final String webhookSecret;

    public StripeWebhookController(FeeService fees,
                                   @Value("${stripe.webhook-secret:}") String webhookSecret) {
        this.fees = fees;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handle(@RequestBody String payload,
                                         @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return ResponseEntity.status(503).body("webhook secret not configured");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("invalid signature");
        }

        if ("invoice.paid".equals(event.getType())) {
            Invoice inv = (Invoice) dataObject(event);
            if (inv == null) {
                log.warn("invoice.paid {} could not be deserialized - payment NOT recorded", event.getId());
            }
            if (inv != null && inv.getMetadata() != null) {
                String schema = inv.getMetadata().get("schema");
                String feeId = inv.getMetadata().get("feeInvoiceId");
                if (schema != null && feeId != null) {
                    try {
                        TenantContext.set(schema);
                        fees.syncStripeById(Long.valueOf(feeId));
                    } finally {
                        TenantContext.clear();
                    }
                }
            }
        }
        return ResponseEntity.ok("ok");
    }

    /**
     * ponytail: the SDK pins an API version that rarely matches the account's, and on a mismatch
     * the typed getObject() just returns empty - which silently turned real payments into no-ops.
     * deserializeUnsafe reads the payload anyway; null now means Stripe genuinely sent something
     * we can't parse, and the caller logs it rather than pretending success.
     */
    static StripeObject dataObject(Event event) {
        EventDataObjectDeserializer d = event.getDataObjectDeserializer();
        return d.getObject().orElseGet(() -> {
            try { return d.deserializeUnsafe(); }
            catch (EventDataObjectDeserializationException e) { return null; }
        });
    }
}
