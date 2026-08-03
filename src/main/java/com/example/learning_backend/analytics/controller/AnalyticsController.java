package com.example.learning_backend.analytics.controller;

import com.example.learning_backend.analytics.dto.AssessmentReportResponse;
import com.example.learning_backend.analytics.dto.GlobalLeaderboardEntryResponse;
import com.example.learning_backend.analytics.dto.LeaderboardEntryResponse;
import com.example.learning_backend.analytics.dto.RevenueReportResponse;
import com.example.learning_backend.analytics.service.AnalyticsService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/assessments/{assessmentId}/leaderboard")
    public List<LeaderboardEntryResponse> assessmentLeaderboard(
        @PathVariable Long assessmentId,
        Authentication authentication
    ) {
        return analyticsService.assessmentLeaderboard(assessmentId, authentication);
    }

    @GetMapping("/leaderboard")
    public List<GlobalLeaderboardEntryResponse> globalLeaderboard() {
        return analyticsService.globalLeaderboard();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @GetMapping("/assessments/{assessmentId}/report")
    public AssessmentReportResponse assessmentReport(
        @PathVariable Long assessmentId,
        Authentication authentication
    ) {
        return analyticsService.assessmentReport(assessmentId, authentication);
    }

    /** Revenue is system-wide, so it stays admin-only rather than following the instructor gate above. */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/reports/revenue")
    public RevenueReportResponse revenueReport(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return analyticsService.revenueReport(from, to);
    }
}
