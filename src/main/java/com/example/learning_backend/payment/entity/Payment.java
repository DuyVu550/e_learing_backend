package com.example.learning_backend.payment.entity;

import com.example.learning_backend.common.entity.BaseEntity;
import com.example.learning_backend.course.entity.Course;
import com.example.learning_backend.payment.enums.PaymentProvider;
import com.example.learning_backend.payment.enums.PaymentStatus;
import com.example.learning_backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    /**
     * Our own order number, sent to the gateway as {@code orderCode} and echoed back by the webhook.
     * Unique so a replayed webhook resolves to exactly one payment.
     */
    @Column(name = "order_code", nullable = false, unique = true)
    private Long orderCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    /** Copied from the course at checkout time so a later price change cannot rewrite history. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentProvider provider = PaymentProvider.PAYOS;

    @Column(length = 255)
    private String description;

    @Column(name = "checkout_url", length = 500)
    private String checkoutUrl;

    @Column(name = "payment_link_id", length = 100)
    private String paymentLinkId;

    @Column(name = "transaction_reference", length = 100)
    private String transactionReference;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;
}
