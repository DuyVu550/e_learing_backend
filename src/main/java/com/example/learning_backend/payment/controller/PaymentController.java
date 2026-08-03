package com.example.learning_backend.payment.controller;

import com.example.learning_backend.payment.dto.PaymentCheckoutRequest;
import com.example.learning_backend.payment.dto.PaymentResponse;
import com.example.learning_backend.payment.dto.PayosWebhookRequest;
import com.example.learning_backend.payment.service.PaymentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/courses/{courseId}/checkout")
    public PaymentResponse checkout(
        Authentication authentication,
        @PathVariable Long courseId,
        @Valid @RequestBody(required = false) PaymentCheckoutRequest request
    ) {
        return paymentService.checkout(authentication.getName(), courseId, request);
    }

    @GetMapping("/payments/me")
    public List<PaymentResponse> myPayments(Authentication authentication) {
        return paymentService.myPayments(authentication.getName());
    }

    @GetMapping("/payments/{paymentId}")
    public PaymentResponse getPayment(Authentication authentication, @PathVariable Long paymentId) {
        return paymentService.getMyPayment(authentication.getName(), paymentId);
    }

    @PostMapping("/payments/{paymentId}/cancel")
    public PaymentResponse cancel(Authentication authentication, @PathVariable Long paymentId) {
        return paymentService.cancel(authentication.getName(), paymentId);
    }

    /**
     * Public gateway callback. Always answers 200 so PayOS stops retrying: a rejected payload is our
     * problem to investigate in the log, and a redelivery would fail identically. The signature check
     * inside the service is the only gate here, since the request carries no authentication.
     */
    @PostMapping("/payments/payos/webhook")
    public Map<String, Object> payosWebhook(@RequestBody PayosWebhookRequest request) {
        try {
            paymentService.handleWebhook(request);
            return Map.of("success", true);
        } catch (RuntimeException ex) {
            log.warn("Rejected PayOS webhook: {}", ex.getMessage());
            return Map.of("success", false);
        }
    }
}
