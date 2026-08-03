package com.example.learning_backend.payment.repository;

import com.example.learning_backend.payment.entity.Payment;
import com.example.learning_backend.payment.enums.PaymentStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderCode(Long orderCode);

    Optional<Payment> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndCourseIdAndStatus(Long userId, Long courseId, PaymentStatus status);

    Optional<Payment> findFirstByUserIdAndCourseIdAndStatusOrderByCreatedAtDesc(
        Long userId,
        Long courseId,
        PaymentStatus status
    );

    List<Payment> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Payment> findByStatusAndPaidAtBetween(PaymentStatus status, LocalDateTime from, LocalDateTime to);
}
