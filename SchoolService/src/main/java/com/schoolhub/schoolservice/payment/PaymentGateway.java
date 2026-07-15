package com.schoolhub.schoolservice.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Selects the active PaymentProvider adapter by config. schoolhub.payment.provider (default
 * 'paystack') chooses which adapter FeeService uses - swapping gateway/country is a config
 * change. Adding a new provider = drop in another @Component PaymentProvider; this finds it.
 */
@Component
public class PaymentGateway {

    private final List<PaymentProvider> providers;
    private final String activeKey;

    public PaymentGateway(List<PaymentProvider> providers,
                          @Value("${schoolhub.payment.provider:paystack}") String activeKey) {
        this.providers = providers;
        this.activeKey = activeKey;
    }

    public PaymentProvider active() {
        return providers.stream()
                .filter(p -> p.key().equalsIgnoreCase(activeKey))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No payment adapter for '" + activeKey + "'. Available: "
                                + providers.stream().map(PaymentProvider::key).toList()));
    }
}
