package com.ktalk.domain.billing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktalk.domain.billing.dto.BillingDtos.CheckoutResponse;
import com.ktalk.domain.billing.dto.BillingDtos.PlanResponse;
import com.ktalk.domain.billing.dto.BillingDtos.SubscriptionResponse;
import com.ktalk.domain.billing.entity.Payment;
import com.ktalk.domain.billing.entity.PaymentStatus;
import com.ktalk.domain.billing.entity.Subscription;
import com.ktalk.domain.billing.entity.SubscriptionStatus;
import com.ktalk.domain.billing.repository.PaymentRepository;
import com.ktalk.domain.billing.repository.SubscriptionRepository;
import com.ktalk.domain.user.entity.User;
import com.ktalk.domain.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${stripe.secret-key:}")
    private String stripeSecretKey;

    @Value("${stripe.webhook-secret:}")
    private String stripeWebhookSecret;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${billing.plan.pro.price-id:}")
    private String proPriceId;

    @Value("${billing.plan.business.price-id:}")
    private String businessPriceId;

    @Value("${billing.plan.pro.monthly-price-krw:9900}")
    private long proMonthlyPriceKrw;

    @Value("${billing.plan.business.monthly-price-krw:29000}")
    private long businessMonthlyPriceKrw;

    @Value("${billing.plan.free.monthly-ai-requests:30}")
    private int freeMonthlyAiRequests;

    @Value("${billing.plan.pro.monthly-ai-requests:1000}")
    private int proMonthlyAiRequests;

    @Value("${billing.plan.business.monthly-ai-requests:5000}")
    private int businessMonthlyAiRequests;

    public List<PlanResponse> getPlans() {
        return List.of(
                new PlanResponse("free", "Free", 0, freeMonthlyAiRequests, false),
                new PlanResponse("pro", "Pro", proMonthlyPriceKrw, proMonthlyAiRequests, hasText(proPriceId)),
                new PlanResponse("business", "Business", businessMonthlyPriceKrw, businessMonthlyAiRequests,
                        hasText(businessPriceId))
        );
    }

    public SubscriptionResponse getCurrentSubscription(Long userId) {
        return subscriptionRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .map(this::toResponse)
                .orElse(new SubscriptionResponse("free", SubscriptionStatus.INACTIVE, null));
    }

    public CheckoutResponse createCheckout(Long userId, String requestedPlanId) {
        String planId = normalizePlanId(requestedPlanId);
        String priceId = priceIdFor(planId);
        if (!hasText(priceId)) {
            throw new IllegalArgumentException("This plan is not available for checkout.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User was not found."));
        Subscription existing = subscriptionRepository.findTopByUserIdOrderByCreatedAtDesc(userId).orElse(null);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "subscription");
        form.add("line_items[0][price]", priceId);
        form.add("line_items[0][quantity]", "1");
        form.add("success_url", trimSlash(frontendUrl) + "/?billing=success&session_id={CHECKOUT_SESSION_ID}");
        form.add("cancel_url", trimSlash(frontendUrl) + "/?billing=cancel");
        form.add("client_reference_id", String.valueOf(userId));
        form.add("customer_email", user.getEmail());
        form.add("metadata[userId]", String.valueOf(userId));
        form.add("metadata[planId]", planId);
        form.add("subscription_data[metadata][userId]", String.valueOf(userId));
        form.add("subscription_data[metadata][planId]", planId);
        if (existing != null && hasText(existing.getStripeCustomerId())) {
            form.remove("customer_email");
            form.add("customer", existing.getStripeCustomerId());
        }

        Map<?, ?> response = stripeClient().post()
                .uri("/v1/checkout/sessions")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        Object checkoutUrl = response == null ? null : response.get("url");
        if (!(checkoutUrl instanceof String url) || url.isBlank()) {
            throw new IllegalStateException("Stripe Checkout URL was not returned.");
        }
        return new CheckoutResponse(url);
    }

    public void handleWebhook(String payload, String signatureHeader) {
        verifyStripeSignature(payload, signatureHeader);
        JsonNode event;
        try {
            event = objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("Stripe webhook payload is invalid.", e);
        }

        String type = event.path("type").asText();
        JsonNode object = event.path("data").path("object");
        switch (type) {
            case "checkout.session.completed" -> handleCheckoutCompleted(object);
            case "customer.subscription.created", "customer.subscription.updated", "customer.subscription.deleted" ->
                    handleSubscriptionChanged(object);
            case "invoice.paid" -> handleInvoicePaid(object);
            case "invoice.payment_failed" -> handleInvoiceFailed(object);
            default -> log.debug("Ignored Stripe event type={}", type);
        }
    }

    private void handleCheckoutCompleted(JsonNode session) {
        Long userId = parseLong(session.path("metadata").path("userId").asText(null));
        String planId = normalizePlanId(session.path("metadata").path("planId").asText("pro"));
        String customerId = session.path("customer").asText(null);
        String subscriptionId = session.path("subscription").asText(null);
        if (userId == null || !hasText(subscriptionId)) {
            log.warn("Stripe checkout event is missing userId or subscriptionId");
            return;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("Stripe checkout user not found: userId={}", userId);
            return;
        }
        Subscription subscription = subscriptionRepository.findByStripeSubscriptionId(subscriptionId)
                .orElseGet(Subscription::new);
        subscription.setUser(user);
        subscription.setPlanId(planId);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStripeCustomerId(customerId);
        subscription.setStripeSubscriptionId(subscriptionId);
        subscription.touch();
        subscriptionRepository.save(subscription);
    }

    private void handleSubscriptionChanged(JsonNode object) {
        String subscriptionId = object.path("id").asText(null);
        if (!hasText(subscriptionId)) {
            return;
        }
        Subscription subscription = subscriptionRepository.findByStripeSubscriptionId(subscriptionId)
                .orElseGet(Subscription::new);
        Long userId = parseLong(object.path("metadata").path("userId").asText(null));
        if (subscription.getUser() == null && userId != null) {
            userRepository.findById(userId).ifPresent(subscription::setUser);
        }
        subscription.setPlanId(normalizePlanId(object.path("metadata").path("planId").asText(subscription.getPlanId())));
        subscription.setStatus(toSubscriptionStatus(object.path("status").asText()));
        subscription.setStripeCustomerId(object.path("customer").asText(null));
        subscription.setStripeSubscriptionId(subscriptionId);
        long periodEnd = object.path("current_period_end").asLong(0);
        subscription.setCurrentPeriodEnd(periodEnd > 0 ? Instant.ofEpochSecond(periodEnd) : null);
        subscription.touch();
        if (subscription.getUser() != null) {
            subscriptionRepository.save(subscription);
        }
    }

    private void handleInvoicePaid(JsonNode invoice) {
        String invoiceId = invoice.path("id").asText(null);
        if (!hasText(invoiceId)) {
            return;
        }
        Payment payment = paymentRepository.findByStripeInvoiceId(invoiceId).orElseGet(Payment::new);
        payment.setStripeInvoiceId(invoiceId);
        payment.setStripeCustomerId(invoice.path("customer").asText(null));
        payment.setStripeSubscriptionId(invoice.path("subscription").asText(null));
        payment.setAmountPaid(invoice.path("amount_paid").asLong(0));
        payment.setCurrency(invoice.path("currency").asText("krw"));
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(Instant.now());
        subscriptionRepository.findByStripeSubscriptionId(payment.getStripeSubscriptionId())
                .map(Subscription::getUser)
                .ifPresent(payment::setUser);
        paymentRepository.save(payment);
    }

    private void handleInvoiceFailed(JsonNode invoice) {
        String subscriptionId = invoice.path("subscription").asText(null);
        if (hasText(subscriptionId)) {
            subscriptionRepository.findByStripeSubscriptionId(subscriptionId).ifPresent(subscription -> {
                subscription.setStatus(SubscriptionStatus.PAST_DUE);
                subscription.touch();
                subscriptionRepository.save(subscription);
            });
        }
    }

    private void verifyStripeSignature(String payload, String signatureHeader) {
        if (!hasText(stripeWebhookSecret)) {
            throw new IllegalStateException("STRIPE_WEBHOOK_SECRET is not configured.");
        }
        if (!hasText(signatureHeader)) {
            throw new IllegalArgumentException("Stripe-Signature header is missing.");
        }
        String timestamp = null;
        List<String> signatures = new ArrayList<>();
        for (String part : signatureHeader.split(",")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && pair[0].equals("t")) timestamp = pair[1];
            if (pair.length == 2 && pair[0].equals("v1")) signatures.add(pair[1]);
        }
        if (!hasText(timestamp) || signatures.isEmpty()) {
            throw new IllegalArgumentException("Stripe-Signature header is invalid.");
        }
        String expected = hmacSha256(stripeWebhookSecret, timestamp + "." + payload);
        if (signatures.stream().noneMatch(signature -> constantTimeEquals(signature, expected))) {
            throw new IllegalArgumentException("Stripe webhook signature verification failed.");
        }
    }

    private WebClient stripeClient() {
        if (!hasText(stripeSecretKey)) {
            throw new IllegalStateException("STRIPE_SECRET_KEY is not configured.");
        }
        return webClientBuilder.baseUrl("https://api.stripe.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + stripeSecretKey)
                .build();
    }

    private SubscriptionResponse toResponse(Subscription subscription) {
        return new SubscriptionResponse(subscription.getPlanId(), subscription.getStatus(), subscription.getCurrentPeriodEnd());
    }

    private String priceIdFor(String planId) {
        return switch (planId) {
            case "pro" -> proPriceId;
            case "business" -> businessPriceId;
            default -> "";
        };
    }

    private String normalizePlanId(String value) {
        String planId = value == null ? "free" : value.toLowerCase(Locale.ROOT).trim();
        if (!List.of("free", "pro", "business").contains(planId)) {
            throw new IllegalArgumentException("Unsupported billing plan.");
        }
        return planId;
    }

    private SubscriptionStatus toSubscriptionStatus(String status) {
        return switch (status == null ? "" : status) {
            case "trialing" -> SubscriptionStatus.TRIALING;
            case "active" -> SubscriptionStatus.ACTIVE;
            case "past_due" -> SubscriptionStatus.PAST_DUE;
            case "canceled" -> SubscriptionStatus.CANCELED;
            case "unpaid" -> SubscriptionStatus.UNPAID;
            case "incomplete" -> SubscriptionStatus.INCOMPLETE;
            case "incomplete_expired" -> SubscriptionStatus.INCOMPLETE_EXPIRED;
            default -> SubscriptionStatus.INACTIVE;
        };
    }

    private Long parseLong(String value) {
        try {
            return value == null ? null : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String hmacSha256(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not calculate Stripe webhook signature.", e);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null || left.length() != right.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < left.length(); i++) {
            result |= left.charAt(i) ^ right.charAt(i);
        }
        return result == 0;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimSlash(String value) {
        if (!hasText(value)) {
            return "http://localhost:5173";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
