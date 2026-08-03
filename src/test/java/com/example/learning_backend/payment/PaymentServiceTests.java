package com.example.learning_backend.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.learning_backend.analytics.dto.RevenueReportResponse;
import com.example.learning_backend.analytics.service.AnalyticsService;
import com.example.learning_backend.course.entity.Course;
import com.example.learning_backend.course.repository.CourseRepository;
import com.example.learning_backend.enrollment.enums.EnrollmentStatus;
import com.example.learning_backend.enrollment.repository.EnrollmentRepository;
import com.example.learning_backend.enrollment.service.EnrollmentService;
import com.example.learning_backend.notification.dto.NotificationResponse;
import com.example.learning_backend.notification.enums.NotificationType;
import com.example.learning_backend.notification.service.NotificationService;
import com.example.learning_backend.payment.dto.PayosWebhookRequest;
import com.example.learning_backend.payment.entity.Payment;
import com.example.learning_backend.payment.enums.PaymentProvider;
import com.example.learning_backend.payment.enums.PaymentStatus;
import com.example.learning_backend.payment.repository.PaymentRepository;
import com.example.learning_backend.payment.service.PaymentService;
import com.example.learning_backend.user.entity.User;
import com.example.learning_backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentServiceTests {

    private static final String CHECKSUM_KEY = "test-checksum-key";
    private static final BigDecimal PRICE = BigDecimal.valueOf(500000);

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserRepository userRepository;

    private User student;
    private Course paidCourse;
    private Course freeCourse;

    @BeforeEach
    void setUp() {
        User instructor = saveUser("teacher@example.com", "Teacher");
        student = saveUser("student@example.com", "Student");

        paidCourse = new Course();
        paidCourse.setSlug("paid-course");
        paidCourse.setTitle("Java nâng cao");
        paidCourse.setPrice(PRICE);
        paidCourse.setInstructor(instructor);
        paidCourse = courseRepository.save(paidCourse);

        freeCourse = new Course();
        freeCourse.setSlug("free-course");
        freeCourse.setTitle("Java cơ bản");
        freeCourse.setPrice(BigDecimal.ZERO);
        freeCourse.setInstructor(instructor);
        freeCourse = courseRepository.save(freeCourse);
    }

    @Test
    void aPaidCourseCannotBeSelfEnrolledWithoutPaying() {
        assertThatThrownBy(() -> enrollmentService.enroll(student.getEmail(), paidCourse.getId()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("requires payment");
        assertThat(enrollmentRepository.findByUserIdAndCourseId(student.getId(), paidCourse.getId())).isEmpty();
    }

    @Test
    void aFreeCourseIsStillEnrollableDirectly() {
        assertThat(enrollmentService.enroll(student.getEmail(), freeCourse.getId()).courseId())
            .isEqualTo(freeCourse.getId());
    }

    @Test
    void aSuccessfulWebhookMarksThePaymentPaidAndGrantsTheEnrollment() {
        Payment payment = pendingPayment();

        paymentService.handleWebhook(webhook(payment.getOrderCode(), PRICE, "00"));

        Payment reloaded = paymentRepository.findByOrderCode(payment.getOrderCode()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(reloaded.getPaidAt()).isNotNull();
        assertThat(reloaded.getTransactionReference()).isEqualTo("FT12345");
        assertThat(enrollmentRepository.findByUserIdAndCourseId(student.getId(), paidCourse.getId())).isPresent();
    }

    @Test
    void payingUnlocksTheEnrollEndpointThatWasBlockedBefore() {
        Payment payment = pendingPayment();
        enrollmentRepository.deleteAll();

        paymentService.handleWebhook(webhook(payment.getOrderCode(), PRICE, "00"));
        enrollmentRepository.deleteAll();

        assertThat(enrollmentService.enroll(student.getEmail(), paidCourse.getId()).courseId())
            .isEqualTo(paidCourse.getId());
    }

    @Test
    void aWebhookWithABadSignatureIsRejectedAndChangesNothing() {
        Payment payment = pendingPayment();
        PayosWebhookRequest tampered = new PayosWebhookRequest(
            "00",
            "success",
            true,
            webhookData(payment.getOrderCode(), PRICE, "00"),
            "deadbeef"
        );

        assertThatThrownBy(() -> paymentService.handleWebhook(tampered))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("signature is invalid");
        assertThat(paymentRepository.findByOrderCode(payment.getOrderCode()).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.PENDING);
        assertThat(enrollmentRepository.findByUserIdAndCourseId(student.getId(), paidCourse.getId())).isEmpty();
    }

    @Test
    void aRedeliveredWebhookDoesNotEnrollTwiceOrNotifyAgain() {
        Payment payment = pendingPayment();

        paymentService.handleWebhook(webhook(payment.getOrderCode(), PRICE, "00"));
        paymentService.handleWebhook(webhook(payment.getOrderCode(), PRICE, "00"));

        assertThat(enrollmentRepository.findByCourseIdAndStatusNot(paidCourse.getId(), EnrollmentStatus.CANCELLED))
            .hasSize(1);
        assertThat(notificationService.myNotifications(student.getEmail(), false)).hasSize(1);
    }

    @Test
    void underpaymentFailsThePaymentAndGrantsNothing() {
        Payment payment = pendingPayment();

        paymentService.handleWebhook(webhook(payment.getOrderCode(), BigDecimal.valueOf(1000), "00"));

        Payment reloaded = paymentRepository.findByOrderCode(payment.getOrderCode()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(reloaded.getFailureReason()).contains("below the course price");
        assertThat(enrollmentRepository.findByUserIdAndCourseId(student.getId(), paidCourse.getId())).isEmpty();
    }

    @Test
    void aFailureCodeFromTheGatewayMarksThePaymentFailed() {
        Payment payment = pendingPayment();

        paymentService.handleWebhook(webhook(payment.getOrderCode(), PRICE, "01"));

        assertThat(paymentRepository.findByOrderCode(payment.getOrderCode()).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.FAILED);
        assertThat(enrollmentRepository.findByUserIdAndCourseId(student.getId(), paidCourse.getId())).isEmpty();
    }

    @Test
    void payingNotifiesTheBuyer() {
        Payment payment = pendingPayment();

        paymentService.handleWebhook(webhook(payment.getOrderCode(), PRICE, "00"));

        List<NotificationResponse> unread = notificationService.myNotifications(student.getEmail(), true);
        assertThat(unread).hasSize(1);
        assertThat(unread.getFirst().type()).isEqualTo(NotificationType.PAYMENT_SUCCEEDED);
    }

    @Test
    void aWebhookForAnUnknownOrderIsRejected() {
        assertThatThrownBy(() -> paymentService.handleWebhook(webhook(999999L, PRICE, "00")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Payment not found for order");
    }

    @Test
    void checkoutIsRefusedForAFreeCourse() {
        assertThatThrownBy(() -> paymentService.checkout(student.getEmail(), freeCourse.getId(), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Course is free");
    }

    @Test
    void aPaidPaymentCannotBeCancelled() {
        Payment payment = pendingPayment();
        paymentService.handleWebhook(webhook(payment.getOrderCode(), PRICE, "00"));

        assertThatThrownBy(() -> paymentService.cancel(student.getEmail(), payment.getId()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be cancelled");
    }

    @Test
    void revenueReportOnlyCountsPaidOrders() {
        Payment paid = pendingPayment();
        paymentService.handleWebhook(webhook(paid.getOrderCode(), PRICE, "00"));
        pendingPayment();

        RevenueReportResponse report = analyticsService.revenueReport(null, null);

        assertThat(report.paidOrders()).isEqualTo(1);
        assertThat(report.totalRevenue()).isEqualByComparingTo(PRICE);
        assertThat(report.byCourse()).hasSize(1);
        assertThat(report.byCourse().getFirst().courseId()).isEqualTo(paidCourse.getId());
        assertThat(report.byCourse().getFirst().shareOfTotal()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    /**
     * Builds the PENDING row that checkout would have created, without calling PayOS — the gateway
     * is out of reach in tests, and everything under test here happens after the link exists.
     */
    private Payment pendingPayment() {
        Payment payment = new Payment();
        payment.setOrderCode(System.nanoTime());
        payment.setUser(student);
        payment.setCourse(paidCourse);
        payment.setAmount(PRICE);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setProvider(PaymentProvider.PAYOS);
        payment.setDescription("KH123456");
        return paymentRepository.save(payment);
    }

    private PayosWebhookRequest webhook(long orderCode, BigDecimal amount, String code) {
        Map<String, Object> data = webhookData(orderCode, amount, code);
        return new PayosWebhookRequest(code, "success", "00".equals(code), data, sign(data));
    }

    private Map<String, Object> webhookData(long orderCode, BigDecimal amount, String code) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderCode", orderCode);
        data.put("amount", amount.longValueExact());
        data.put("description", "KH123456");
        data.put("accountNumber", "12345678");
        data.put("reference", "FT12345");
        data.put("transactionDateTime", "2026-08-03 10:00:00");
        data.put("currency", "VND");
        data.put("paymentLinkId", "link-1");
        data.put("code", code);
        data.put("desc", "00".equals(code) ? "success" : "failed");
        return data;
    }

    /** Mirrors PayOS: alphabetically sorted {@code key=value} pairs, HMAC-SHA256 with the checksum key. */
    private String sign(Map<String, Object> data) {
        StringBuilder payload = new StringBuilder();
        for (Map.Entry<String, Object> entry : new TreeMap<>(data).entrySet()) {
            if (!payload.isEmpty()) {
                payload.append('&');
            }
            payload.append(entry.getKey()).append('=').append(entry.getValue());
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(CHECKSUM_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private User saveUser(String email, String fullName) {
        User user = new User();
        user.setEmail(email);
        user.setFullName(fullName);
        user.setPasswordHash("hash");
        return userRepository.save(user);
    }
}
