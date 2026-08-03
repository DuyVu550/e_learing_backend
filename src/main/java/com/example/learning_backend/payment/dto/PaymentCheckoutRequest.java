package com.example.learning_backend.payment.dto;

import jakarta.validation.constraints.Size;

/**
 * Checkout request. The amount is deliberately absent — it is read from the course so a caller
 * cannot name their own price.
 */
public record PaymentCheckoutRequest(
    @Size(max = 500) String returnUrl,
    @Size(max = 500) String cancelUrl
) {
}
