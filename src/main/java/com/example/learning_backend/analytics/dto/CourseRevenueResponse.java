package com.example.learning_backend.analytics.dto;

import java.math.BigDecimal;

public record CourseRevenueResponse(
    Long courseId,
    String courseTitle,
    long paidOrders,
    BigDecimal revenue,
    BigDecimal shareOfTotal
) {
}
