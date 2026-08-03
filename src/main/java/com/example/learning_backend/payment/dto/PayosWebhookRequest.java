package com.example.learning_backend.payment.dto;

import java.util.Map;

/**
 * Raw PayOS webhook envelope. {@code data} stays an untyped map because the signature is computed
 * over whatever keys the gateway actually sent — binding to a fixed record would silently drop new
 * fields and break verification.
 */
public record PayosWebhookRequest(
    String code,
    String desc,
    Boolean success,
    Map<String, Object> data,
    String signature
) {
}
