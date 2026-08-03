package com.example.learning_backend.payment.service;

import com.example.learning_backend.course.entity.Course;
import com.example.learning_backend.course.repository.CourseRepository;
import com.example.learning_backend.notification.enums.NotificationType;
import com.example.learning_backend.notification.service.NotificationService;
import com.example.learning_backend.payment.dto.PaymentCheckoutRequest;
import com.example.learning_backend.payment.dto.PaymentResponse;
import com.example.learning_backend.payment.dto.PayosWebhookRequest;
import com.example.learning_backend.payment.entity.Payment;
import com.example.learning_backend.payment.enums.PaymentProvider;
import com.example.learning_backend.payment.enums.PaymentStatus;
import com.example.learning_backend.payment.repository.PaymentRepository;
import com.example.learning_backend.enrollment.entity.Enrollment;
import com.example.learning_backend.enrollment.enums.EnrollmentStatus;
import com.example.learning_backend.enrollment.repository.EnrollmentRepository;
import com.example.learning_backend.user.entity.User;
import com.example.learning_backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Buying a course. Checkout creates a PENDING payment and a gateway link; the webhook is the only
 * thing that promotes it to PAID and grants the enrollment, so a buyer who never actually pays
 * cannot talk their way into the course by returning to {@code returnUrl}.
 */
@Service
@Transactional
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final NotificationService notificationService;
    private final PayosClient payosClient;
    private final String defaultReturnUrl;
    private final String defaultCancelUrl;

    public PaymentService(
        PaymentRepository paymentRepository,
        CourseRepository courseRepository,
        UserRepository userRepository,
        EnrollmentRepository enrollmentRepository,
        NotificationService notificationService,
        PayosClient payosClient,
        @Value("${app.payos.return-url}") String defaultReturnUrl,
        @Value("${app.payos.cancel-url}") String defaultCancelUrl
    ) {
        this.paymentRepository = paymentRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.notificationService = notificationService;
        this.payosClient = payosClient;
        this.defaultReturnUrl = defaultReturnUrl;
        this.defaultCancelUrl = defaultCancelUrl;
    }

    public PaymentResponse checkout(String email, Long courseId, PaymentCheckoutRequest request) {
        User user = requireUser(email);
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseId));

        if (course.getPrice() == null || course.getPrice().signum() <= 0) {
            throw new IllegalArgumentException("Course is free, enroll directly: " + courseId);
        }
        if (enrollmentRepository.findByUserIdAndCourseId(user.getId(), courseId).isPresent()) {
            throw new IllegalArgumentException("You already have access to course: " + courseId);
        }
        if (paymentRepository.existsByUserIdAndCourseIdAndStatus(user.getId(), courseId, PaymentStatus.PAID)) {
            throw new IllegalArgumentException("Course is already paid for: " + courseId);
        }

        // Reuse a still-open link so refreshing the checkout page does not litter the gateway with orders.
        Payment existing = paymentRepository
            .findFirstByUserIdAndCourseIdAndStatusOrderByCreatedAtDesc(user.getId(), courseId, PaymentStatus.PENDING)
            .orElse(null);
        if (existing != null && existing.getCheckoutUrl() != null) {
            return toResponse(existing);
        }

        Payment payment = new Payment();
        payment.setOrderCode(nextOrderCode());
        payment.setUser(user);
        payment.setCourse(course);
        payment.setAmount(course.getPrice());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setProvider(PaymentProvider.PAYOS);
        payment.setDescription(describe(payment.getOrderCode()));
        Payment saved = paymentRepository.save(payment);

        Map<String, Object> data = payosClient.createPaymentLink(
            saved.getOrderCode(),
            saved.getAmount(),
            saved.getDescription(),
            request != null && hasText(request.returnUrl()) ? request.returnUrl() : defaultReturnUrl,
            request != null && hasText(request.cancelUrl()) ? request.cancelUrl() : defaultCancelUrl
        );
        saved.setCheckoutUrl(asText(data.get("checkoutUrl")));
        saved.setPaymentLinkId(asText(data.get("paymentLinkId")));
        return toResponse(saved);
    }

    /**
     * Handles a gateway callback. Verifies the signature first, then applies the outcome exactly
     * once — PayOS retries webhooks, so a second delivery for an already-PAID order is a no-op.
     */
    public void handleWebhook(PayosWebhookRequest request) {
        if (request == null || request.data() == null) {
            throw new IllegalArgumentException("Webhook payload is empty");
        }
        if (!payosClient.verifyWebhookSignature(request.data(), request.signature())) {
            throw new IllegalArgumentException("Webhook signature is invalid");
        }

        Object rawOrderCode = request.data().get("orderCode");
        if (rawOrderCode == null) {
            throw new IllegalArgumentException("Webhook is missing orderCode");
        }
        long orderCode = Long.parseLong(String.valueOf(rawOrderCode));
        Payment payment = paymentRepository.findByOrderCode(orderCode)
            .orElseThrow(() -> new IllegalArgumentException("Payment not found for order: " + orderCode));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }

        boolean succeeded = "00".equals(asText(request.data().get("code")));
        if (!succeeded) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(trim(asText(request.data().get("desc")), 255));
            return;
        }

        // Trust the gateway's amount over our own record: underpayment must not unlock the course.
        BigDecimal paidAmount = new BigDecimal(String.valueOf(request.data().get("amount")));
        if (paidAmount.compareTo(payment.getAmount()) < 0) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Paid amount " + paidAmount + " is below the course price " + payment.getAmount());
            return;
        }

        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        payment.setTransactionReference(trim(asText(request.data().get("reference")), 100));
        grantEnrollment(payment);
        announcePayment(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> myPayments(String email) {
        User user = requireUser(email);
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
            .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PaymentResponse getMyPayment(String email, Long paymentId) {
        User user = requireUser(email);
        return paymentRepository.findByIdAndUserId(paymentId, user.getId())
            .map(this::toResponse)
            .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
    }

    public PaymentResponse cancel(String email, Long paymentId) {
        User user = requireUser(email);
        Payment payment = paymentRepository.findByIdAndUserId(paymentId, user.getId())
            .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        if (payment.getStatus() == PaymentStatus.PAID) {
            throw new IllegalArgumentException("A paid payment cannot be cancelled: " + paymentId);
        }
        if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.CANCELLED);
            payment.setCancelledAt(LocalDateTime.now());
        }
        return toResponse(payment);
    }

    /** Mirrors {@code EnrollmentService.enroll}: an existing enrollment is left as it is. */
    private void grantEnrollment(Payment payment) {
        Long userId = payment.getUser().getId();
        Long courseId = payment.getCourse().getId();
        if (enrollmentRepository.findByUserIdAndCourseId(userId, courseId).isPresent()) {
            return;
        }
        Enrollment enrollment = new Enrollment();
        enrollment.setUser(payment.getUser());
        enrollment.setCourse(payment.getCourse());
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollmentRepository.save(enrollment);
    }

    private void announcePayment(Payment payment) {
        notificationService.notify(
            payment.getUser(),
            NotificationType.PAYMENT_SUCCEEDED,
            "Thanh toán thành công",
            "Bạn đã thanh toán thành công khóa học: " + payment.getCourse().getTitle(),
            payment.getId()
        );
    }

    /**
     * PayOS requires a numeric order code that is unique per channel, and caps the description at
     * 9 characters for unlinked bank accounts. A millisecond timestamp satisfies both: it fits the
     * gateway's Int53 range and is short enough to reference in the transfer note.
     */
    private long nextOrderCode() {
        long candidate = System.currentTimeMillis();
        while (paymentRepository.findByOrderCode(candidate).isPresent()) {
            candidate++;
        }
        return candidate;
    }

    /** Kept to 9 characters so the bank transfer note is never truncated by the gateway. */
    private String describe(long orderCode) {
        String suffix = String.valueOf(orderCode);
        return "KH" + suffix.substring(Math.max(0, suffix.length() - 7));
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
            payment.getId(),
            payment.getOrderCode(),
            payment.getCourse() != null ? payment.getCourse().getId() : null,
            payment.getCourse() != null ? payment.getCourse().getTitle() : null,
            payment.getAmount(),
            payment.getStatus(),
            payment.getProvider(),
            payment.getCheckoutUrl(),
            payment.getPaymentLinkId(),
            payment.getPaidAt(),
            payment.getCancelledAt(),
            payment.getFailureReason(),
            payment.getCreatedAt()
        );
    }
}
