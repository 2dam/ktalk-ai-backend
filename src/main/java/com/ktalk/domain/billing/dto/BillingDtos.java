package com.ktalk.domain.billing.dto;

import com.ktalk.domain.billing.entity.SubscriptionStatus;
import java.time.Instant;

public final class BillingDtos {
    private BillingDtos() {
    }

    public record CheckoutRequest(String planId) {
    }

    public record CheckoutResponse(String checkoutUrl) {
    }

    public record PlanResponse(String id, String name, long monthlyPriceKrw, int monthlyAiRequests,
                               boolean checkoutEnabled) {
    }

    public record SubscriptionResponse(String planId, SubscriptionStatus status, Instant currentPeriodEnd) {
    }
}
