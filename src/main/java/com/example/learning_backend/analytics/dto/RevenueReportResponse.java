package com.example.learning_backend.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record RevenueReportResponse(
    LocalDateTime from,
    LocalDateTime to,
    BigDecimal totalRevenue,
    long paidOrders,
    BigDecimal averageOrderValue,
    List<CourseRevenueResponse> byCourse
) {
}
