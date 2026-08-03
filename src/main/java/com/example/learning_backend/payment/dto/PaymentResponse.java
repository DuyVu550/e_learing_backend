package com.example.learning_backend.payment.dto;

import com.example.learning_backend.payment.enums.PaymentProvider;
import com.example.learning_backend.payment.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
    Long id,
    Long orderCode,
    Long courseId,
    String courseTitle,
    BigDecimal amount,
    PaymentStatus status,
    PaymentProvider provider,
    String checkoutUrl,
    String paymentLinkId,
    LocalDateTime paidAt,
    LocalDateTime cancelledAt,
    String failureReason,
    LocalDateTime createdAt
) {
}
