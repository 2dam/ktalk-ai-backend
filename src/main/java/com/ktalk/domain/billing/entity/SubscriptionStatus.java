package com.ktalk.domain.billing.entity;

public enum SubscriptionStatus {
    INACTIVE,
    TRIALING,
    ACTIVE,
    PAST_DUE,
    CANCELED,
    UNPAID,
    INCOMPLETE,
    INCOMPLETE_EXPIRED
}
