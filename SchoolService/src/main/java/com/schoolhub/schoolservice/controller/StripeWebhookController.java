package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.service.FeeService;
import com.schoolhub.schoolservice.tenant.TenantContext;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.net.Webhook;
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
            Invoice inv = (Invoice) event.getDataObjectDeserializer().getObject().orElse(null);
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
}
