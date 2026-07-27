package com.schoolhub.tenantservice.controller;

import com.schoolhub.tenantservice.service.BillingService;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** School-admin billing (Stripe subscriptions) + the Stripe webhook for subscription events. */
@RestController
@RequestMapping("/api/v1/tenants")
public class BillingController {

    private static final Logger log = LoggerFactory.getLogger(BillingController.class);

    private final BillingService billing;
    private final String webhookSecret;

    public BillingController(BillingService billing,
                             @Value("${stripe.webhook-secret:}") String webhookSecret) {
        this.billing = billing;
        this.webhookSecret = webhookSecret;
    }

    /** Body for POST /billing/plan - switch the school plan. */
    public record ChangePlanReq(Long planId) {}

    @GetMapping("/billing")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> status(Authentication auth) {
        return ResponseEntity.ok(billing.status((Long) auth.getPrincipal()));
    }

    @PostMapping("/billing/checkout")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> checkout(Authentication auth) {
        return ResponseEntity.ok(billing.checkout((Long) auth.getPrincipal()));
    }

    @PostMapping("/billing/portal")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> portal(Authentication auth) {
        return ResponseEntity.ok(billing.portal((Long) auth.getPrincipal()));
    }

    @PostMapping("/billing/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> sync(Authentication auth) {
        return ResponseEntity.ok(billing.sync((Long) auth.getPrincipal()));
    }

    @PostMapping("/billing/plan")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> changePlan(Authentication auth, @RequestBody ChangePlanReq req) {
        return ResponseEntity.ok(billing.changePlan((Long) auth.getPrincipal(), req.planId()));
    }

    /** Stripe → subscription lifecycle. No JWT; authenticity is the signature. */
    @PostMapping("/stripe/webhook")
    public ResponseEntity<String> webhook(@RequestBody String payload,
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
        switch (event.getType()) {
            case "checkout.session.completed" -> {
                Session s = (Session) dataObject(event);
                if (s == null) log.warn("{} {} could not be deserialized - subscription NOT applied", event.getType(), event.getId());
                if (s != null && "subscription".equals(s.getMode())) {
                    billing.applySubscriptionEvent(s.getSubscription(), s.getCustomer(),
                            s.getMetadata() == null ? null : s.getMetadata().get("tenantId"), "active");
                }
            }
            case "customer.subscription.updated", "customer.subscription.deleted" -> {
                Subscription sub = (Subscription) dataObject(event);
                if (sub == null) log.warn("{} {} could not be deserialized - subscription NOT applied", event.getType(), event.getId());
                if (sub != null) {
                    billing.applySubscriptionEvent(sub.getId(), sub.getCustomer(),
                            sub.getMetadata() == null ? null : sub.getMetadata().get("tenantId"), sub.getStatus());
                }
            }
            default -> { /* uninteresting event */ }
        }
        return ResponseEntity.ok("ok");
    }

    /** ponytail: see StripeWebhookController.dataObject - SDK/account API-version mismatch makes
     *  the typed getObject() empty, which silently dropped live subscription events. */
    static StripeObject dataObject(Event event) {
        EventDataObjectDeserializer d = event.getDataObjectDeserializer();
        return d.getObject().orElseGet(() -> {
            try { return d.deserializeUnsafe(); }
            catch (EventDataObjectDeserializationException e) { return null; }
        });
    }
}
