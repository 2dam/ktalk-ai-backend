package com.ktalk.domain.billing.controller;

import com.ktalk.domain.billing.dto.BillingDtos.CheckoutRequest;
import com.ktalk.domain.billing.service.BillingService;
import com.ktalk.global.response.ApiResponse;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
@Slf4j
public class BillingController {
    private final BillingService billingService;

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse> plans() {
        return ResponseEntity.ok(ApiResponse.success(billingService.getPlans()));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse> me(Authentication authentication) {
        Long userId = userId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Login is required."));
        }
        return ResponseEntity.ok(ApiResponse.success(billingService.getCurrentSubscription(userId)));
    }

    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse> checkout(@RequestBody CheckoutRequest request, Authentication authentication) {
        Long userId = userId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Login is required."));
        }
        try {
            return ResponseEntity.ok(ApiResponse.success(billingService.createCheckout(userId, request.planId())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create Stripe Checkout session", e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("Could not start checkout."));
        }
    }

    @PostMapping(value = "/webhook", consumes = "application/json")
    public ResponseEntity<String> webhook(@RequestBody byte[] body,
                                          @RequestHeader(value = "Stripe-Signature", required = false)
                                          String signature) {
        try {
            billingService.handleWebhook(new String(body, StandardCharsets.UTF_8), signature);
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            log.warn("Stripe webhook failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("invalid webhook");
        }
    }

    private Long userId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long id) {
            return id;
        }
        try {
            return Long.parseLong(String.valueOf(principal));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
