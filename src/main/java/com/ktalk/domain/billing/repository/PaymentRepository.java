package com.ktalk.domain.billing.repository;

import com.ktalk.domain.billing.entity.Payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByStripeInvoiceId(String stripeInvoiceId);
}
