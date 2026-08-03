package com.example.learning_backend.payment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Talks to PayOS: creates a checkout link and verifies webhook signatures.
 *
 * <p>PayOS uses two different HMAC-SHA256 constructions with the same checksum key. The create
 * request signs exactly five fields in a fixed alphabetical order; the webhook signs every key of
 * its own {@code data} object, sorted alphabetically. Mixing them up is the classic integration
 * bug, so each has its own method here.
 */
@Component
public class PayosClient {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String clientId;
    private final String apiKey;
    private final String checksumKey;

    public PayosClient(
        ObjectMapper objectMapper,
        @Value("${app.payos.base-url}") String baseUrl,
        @Value("${app.payos.client-id}") String clientId,
        @Value("${app.payos.api-key}") String apiKey,
        @Value("${app.payos.checksum-key}") String checksumKey
    ) {
        this.restClient = RestClient.create(baseUrl);
        this.objectMapper = objectMapper;
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.checksumKey = checksumKey;
    }

    /**
     * Creates a payment link and returns the gateway's {@code data} object, which carries
     * {@code checkoutUrl}, {@code paymentLinkId}, {@code qrCode} and the echoed order fields.
     */
    public Map<String, Object> createPaymentLink(
        long orderCode,
        BigDecimal amount,
        String description,
        String returnUrl,
        String cancelUrl
    ) {
        requireConfigured();
        // PayOS takes an integer amount in VND; the currency has no minor unit.
        long amountValue = amount.setScale(0, RoundingMode.HALF_UP).longValueExact();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderCode", orderCode);
        body.put("amount", amountValue);
        body.put("description", description);
        body.put("returnUrl", returnUrl);
        body.put("cancelUrl", cancelUrl);
        body.put("signature", createRequestSignature(amountValue, cancelUrl, description, orderCode, returnUrl));

        PayosEnvelope response = restClient.post()
            .uri("/v2/payment-requests")
            .header("x-client-id", clientId)
            .header("x-api-key", apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(PayosEnvelope.class);

        if (response == null || response.data() == null) {
            throw new IllegalStateException("PayOS returned an empty response for order " + orderCode);
        }
        if (!"00".equals(response.code())) {
            throw new IllegalStateException("PayOS rejected order " + orderCode + ": " + response.desc());
        }
        return response.data();
    }

    /**
     * Signature for {@code POST /v2/payment-requests}: only these five fields, in this order.
     */
    private String createRequestSignature(
        long amount,
        String cancelUrl,
        String description,
        long orderCode,
        String returnUrl
    ) {
        String payload = "amount=" + amount
            + "&cancelUrl=" + cancelUrl
            + "&description=" + description
            + "&orderCode=" + orderCode
            + "&returnUrl=" + returnUrl;
        return hmacSha256(payload);
    }

    /**
     * Verifies a webhook by re-deriving the signature over its {@code data} object: keys sorted
     * alphabetically, joined as {@code key=value} with {@code &}, nulls collapsed to empty strings.
     */
    public boolean verifyWebhookSignature(Map<String, Object> data, String signature) {
        if (data == null || signature == null || signature.isBlank()) {
            return false;
        }
        String expected = hmacSha256(canonicalize(data));
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            signature.toLowerCase().getBytes(StandardCharsets.UTF_8)
        );
    }

    private String canonicalize(Map<String, Object> data) {
        List<String> pairs = new ArrayList<>();
        for (Map.Entry<String, Object> entry : new TreeMap<>(data).entrySet()) {
            pairs.add(entry.getKey() + "=" + stringify(entry.getValue()));
        }
        return String.join("&", pairs);
    }

    /**
     * PayOS treats {@code null} and the literal strings {@code "null"}/{@code "undefined"} as empty,
     * and serializes nested objects and arrays as compact JSON.
     */
    private String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            return objectMapper.writeValueAsString(value);
        }
        String text = String.valueOf(value);
        return "null".equals(text) || "undefined".equals(text) ? "" : text;
    }

    private String hmacSha256(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(checksumKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("HmacSHA256 is not available", ex);
        }
    }

    /** True once credentials are present, so checkout fails loudly instead of calling PayOS anonymously. */
    public boolean isConfigured() {
        return !clientId.isBlank() && !apiKey.isBlank() && !checksumKey.isBlank();
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException(
                "PayOS is not configured; set app.payos.client-id, app.payos.api-key and app.payos.checksum-key"
            );
        }
    }

    private record PayosEnvelope(String code, String desc, Map<String, Object> data, String signature) {
    }
}
