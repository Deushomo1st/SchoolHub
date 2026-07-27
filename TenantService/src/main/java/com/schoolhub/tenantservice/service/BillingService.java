package com.schoolhub.tenantservice.service;

import com.schoolhub.tenantservice.model.AppUser;
import com.schoolhub.tenantservice.model.SubscriptionPlan;
import com.schoolhub.tenantservice.model.Tenant;
import com.schoolhub.tenantservice.repository.AppUserRepository;
import com.schoolhub.tenantservice.repository.SubscriptionPlanRepository;
import com.schoolhub.tenantservice.repository.TenantRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Price;
import com.stripe.model.Product;
import com.stripe.model.Subscription;
import com.stripe.model.billingportal.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PriceCreateParams;
import com.stripe.param.ProductCreateParams;
import com.stripe.param.billingportal.SessionCreateParams;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stripe Billing: schools subscribe to SchoolHub plans. Plans map lazily to a
 * Product + monthly NGN Price; each school is a Stripe Customer with one
 * subscription. Status is stored on the tenant and refreshed by webhook or the
 * explicit sync endpoint (dev runs may have no webhook forwarder).
 */
@Service
public class BillingService {

    private final TenantRepository tenantRepo;
    private final SubscriptionPlanRepository planRepo;
    private final AppUserRepository userRepo;
    private final AuditService audit;
    private final boolean enabled;
    private final String appBaseUrl;

    public BillingService(TenantRepository tenantRepo, SubscriptionPlanRepository planRepo,
                          AppUserRepository userRepo, AuditService audit,
                          @Value("${stripe.secret-key:}") String secretKey,
                          @Value("${stripe.app-base-url}") String appBaseUrl) {
        this.tenantRepo = tenantRepo;
        this.planRepo = planRepo;
        this.userRepo = userRepo;
        this.audit = audit;
        this.enabled = secretKey != null && !secretKey.isBlank();
        if (enabled) Stripe.apiKey = secretKey;
        this.appBaseUrl = appBaseUrl;
    }

    private void requireEnabled() {
        if (!enabled) throw new IllegalStateException("Stripe is not configured on the server");
    }

    private Tenant callerTenant(Long callerUserId) {
        AppUser caller = userRepo.findById(callerUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        if (caller.getTenantId() == null) throw new AccessDeniedException("Only a school administrator manages billing");
        return tenantRepo.findById(caller.getTenantId())
                .orElseThrow(() -> new IllegalStateException("Caller references missing school"));
    }

    /** What the admin's Billing card shows. */
    public Map<String, Object> status(Long callerUserId) {
        Tenant t = callerTenant(callerUserId);
        SubscriptionPlan plan = t.getPlanId() == null ? null : planRepo.findById(t.getPlanId()).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plan", plan == null ? null : plan.getName());
        m.put("priceNaira", plan == null ? null : plan.getPriceNaira());
        m.put("subscribed", t.getStripeSubscriptionId() != null);
        m.put("subStatus", t.getStripeSubStatus());
        m.put("stripeEnabled", enabled);
        return m;
    }

    /** Hosted Checkout for the school's plan (subscription mode, NGN monthly). */
    @Transactional
    public Map<String, Object> checkout(Long callerUserId) {
        requireEnabled();
        Tenant t = callerTenant(callerUserId);
        if (t.getPlanId() == null) throw new IllegalArgumentException("The school has no plan to subscribe to");
        SubscriptionPlan plan = planRepo.findById(t.getPlanId())
                .orElseThrow(() -> new EntityNotFoundException("Plan not found"));
        try {
            String priceId = ensurePrice(plan);
            String customerId = ensureCustomer(t);
            var params = com.stripe.param.checkout.SessionCreateParams.builder()
                    .setMode(com.stripe.param.checkout.SessionCreateParams.Mode.SUBSCRIPTION)
                    .setCustomer(customerId)
                    .addLineItem(com.stripe.param.checkout.SessionCreateParams.LineItem.builder()
                            .setPrice(priceId).setQuantity(1L).build())
                    .setSuccessUrl(appBaseUrl + "/app.html?billing=success")
                    .setCancelUrl(appBaseUrl + "/app.html?billing=cancelled")
                    .putMetadata("tenantId", String.valueOf(t.getId()))
                    .setSubscriptionData(com.stripe.param.checkout.SessionCreateParams.SubscriptionData.builder()
                            .putMetadata("tenantId", String.valueOf(t.getId())).build())
                    .build();
            var session = com.stripe.model.checkout.Session.create(params);
            audit.record(t.getId(), callerUserId, "BILLING_CHECKOUT_STARTED", plan.getName());
            return Map.of("url", session.getUrl());
        } catch (StripeException e) {
            throw new IllegalStateException("Stripe error: " + e.getMessage());
        }
    }


    /**
     * Checkout for a freshly-registered school (no authenticated user yet).
     * Returns the Stripe Checkout URL, or null if the plan is free, Stripe is
     * not configured, or any Stripe error occurs (signup must never break).
     */
    public String checkoutForNewTenant(Tenant t, SubscriptionPlan plan) {
        if (!enabled) return null;
        if (plan.getPriceNaira() == null || plan.getPriceNaira() == 0) return null;
        try {
            String priceId = ensurePrice(plan);
            String customerId = ensureCustomer(t);
            var params = com.stripe.param.checkout.SessionCreateParams.builder()
                    .setMode(com.stripe.param.checkout.SessionCreateParams.Mode.SUBSCRIPTION)
                    .setCustomer(customerId)
                    .addLineItem(com.stripe.param.checkout.SessionCreateParams.LineItem.builder()
                            .setPrice(priceId).setQuantity(1L).build())
                    .setSuccessUrl(appBaseUrl + "/login.html?billing=success")
                    .setCancelUrl(appBaseUrl + "/login.html?billing=cancelled")
                    .putMetadata("tenantId", String.valueOf(t.getId()))
                    .setSubscriptionData(com.stripe.param.checkout.SessionCreateParams.SubscriptionData.builder()
                            .putMetadata("tenantId", String.valueOf(t.getId())).build())
                    .build();
            var session = com.stripe.model.checkout.Session.create(params);
            audit.record(t.getId(), null, "BILLING_CHECKOUT_STARTED", plan.getName() + " (signup)");
            return session.getUrl();
        } catch (Exception e) {
            // Stripe hiccup: school is still registered; they can subscribe from the dashboard later.
            return null;
        }
    }

    /**
     * Switch the caller's school to a different plan (right-size up or down).
     * Honours the "no destructive Stripe calls on switch" rule: we only move the
     * local plan pointer; the next checkout mints/uses the new plan's Stripe price.
     * Only surfaced via the payment gate today (unpaid context) - see class note.
     */
    @Transactional
    public Map<String, Object> changePlan(Long callerUserId, Long planId) {
        Tenant t = callerTenant(callerUserId);
        if (planId == null) throw new IllegalArgumentException("planId is required");
        SubscriptionPlan plan = planRepo.findById(planId)
                .orElseThrow(() -> new EntityNotFoundException("Plan not found"));
        if (!plan.getId().equals(t.getPlanId())) {
            t.setPlanId(plan.getId());
            tenantRepo.save(t);
            audit.record(t.getId(), callerUserId, "PLAN_CHANGED",
                    "switched to " + plan.getName() + " - N" + plan.getPriceNaira());
        }
        return status(callerUserId);
    }

    /** Stripe Customer Portal (manage/cancel/update card). */
    public Map<String, Object> portal(Long callerUserId) {
        requireEnabled();
        Tenant t = callerTenant(callerUserId);
        if (t.getStripeCustomerId() == null) throw new IllegalArgumentException("Subscribe first — there is no billing profile yet");
        try {
            Session s = Session.create(SessionCreateParams.builder()
                    .setCustomer(t.getStripeCustomerId())
                    .setReturnUrl(appBaseUrl + "/app.html")
                    .build());
            return Map.of("url", s.getUrl());
        } catch (StripeException e) {
            throw new IllegalStateException("Stripe error: " + e.getMessage());
        }
    }

    /** Pull the live subscription state (used after checkout returns, or on demand). */
    @Transactional
    public Map<String, Object> sync(Long callerUserId) {
        requireEnabled();
        Tenant t = callerTenant(callerUserId);
        try {
            if (t.getStripeSubscriptionId() == null && t.getStripeCustomerId() != null) {
                // Checkout may have completed without the webhook landing: find the newest sub.
                var subs = Subscription.list(com.stripe.param.SubscriptionListParams.builder()
                        .setCustomer(t.getStripeCustomerId()).setLimit(1L).build());
                if (!subs.getData().isEmpty()) t.setStripeSubscriptionId(subs.getData().get(0).getId());
            }
            if (t.getStripeSubscriptionId() != null) {
                Subscription sub = Subscription.retrieve(t.getStripeSubscriptionId());
                t.setStripeSubStatus(sub.getStatus());
            }
            tenantRepo.save(t);
        } catch (StripeException e) {
            throw new IllegalStateException("Stripe error: " + e.getMessage());
        }
        return status(callerUserId);
    }

    /** Webhook: subscription lifecycle → stored status (found by subscription or metadata). */
    @Transactional
    public void applySubscriptionEvent(String subscriptionId, String customerId, String tenantIdMeta, String status) {
        Tenant t = null;
        if (tenantIdMeta != null) t = tenantRepo.findById(Long.valueOf(tenantIdMeta)).orElse(null);
        if (t == null && customerId != null) t = tenantRepo.findByStripeCustomerId(customerId).orElse(null);
        if (t == null) return;
        if (subscriptionId != null) t.setStripeSubscriptionId(subscriptionId);
        if (customerId != null) t.setStripeCustomerId(customerId);
        t.setStripeSubStatus(status);
        tenantRepo.save(t);
        audit.record(t.getId(), null, "BILLING_STATUS", status);
    }

    private String ensureCustomer(Tenant t) throws StripeException {
        if (t.getStripeCustomerId() != null) return t.getStripeCustomerId();
        Customer c = Customer.create(CustomerCreateParams.builder()
                .setName(t.getName())
                .setEmail(t.getContactEmail())
                .putMetadata("tenantId", String.valueOf(t.getId()))
                .build());
        t.setStripeCustomerId(c.getId());
        tenantRepo.save(t);
        return c.getId();
    }

    /** Product/Price minted lazily; a price edit gets a fresh Price (Stripe prices are immutable). */
    private String ensurePrice(SubscriptionPlan plan) throws StripeException {
        long kobo = plan.getPriceNaira() * 100L;
        if (plan.getStripePriceId() != null) {
            Price p = Price.retrieve(plan.getStripePriceId());
            if (p.getUnitAmount() != null && p.getUnitAmount() == kobo) return p.getId();
        }
        if (plan.getStripeProductId() == null) {
            Product prod = Product.create(ProductCreateParams.builder()
                    .setName("SchoolHub " + plan.getName())
                    .setDescription(plan.getDescription())
                    .build());
            plan.setStripeProductId(prod.getId());
        }
        Price price = Price.create(PriceCreateParams.builder()
                .setProduct(plan.getStripeProductId())
                .setUnitAmount(kobo)
                .setCurrency("ngn")
                .setRecurring(PriceCreateParams.Recurring.builder()
                        .setInterval(PriceCreateParams.Recurring.Interval.MONTH).build())
                .build());
        plan.setStripePriceId(price.getId());
        planRepo.save(plan);
        return price.getId();
    }
}
