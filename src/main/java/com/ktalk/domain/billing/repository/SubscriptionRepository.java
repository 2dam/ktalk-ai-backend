package com.ktalk.domain.billing.repository;

import com.ktalk.domain.billing.entity.Subscription;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);

    Optional<Subscription> findTopByStripeCustomerIdOrderByCreatedAtDesc(String stripeCustomerId);
}
