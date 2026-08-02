package com.example.learning_backend.analytics.service;

import com.example.learning_backend.analytics.dto.AssessmentReportResponse;
import com.example.learning_backend.analytics.dto.GlobalLeaderboardEntryResponse;
import com.example.learning_backend.analytics.dto.LeaderboardEntryResponse;
import com.example.learning_backend.analytics.dto.QuestionAnalysisResponse;
import com.example.learning_backend.analytics.dto.QuestionStatRow;
import com.example.learning_backend.analytics.dto.ScoreDistributionResponse;
import com.example.learning_backend.assessment.entity.Assessment;
import com.example.learning_backend.assessment.entity.AssessmentQuestionSelection;
import com.example.learning_backend.assessment.entity.Question;
import com.example.learning_backend.assessment.repository.AssessmentQuestionSelectionRepository;
import com.example.learning_backend.assessment.repository.AssessmentRepository;
import com.example.learning_backend.course.service.CourseAccessPolicy;
import com.example.learning_backend.enrollment.service.EnrollmentAccessPolicy;
import com.example.learning_backend.submission.entity.AssessmentAttempt;
import com.example.learning_backend.submission.enums.AttemptStatus;
import com.example.learning_backend.submission.repository.AnswerRepository;
import com.example.learning_backend.submission.repository.AssessmentAttemptRepository;
import com.example.learning_backend.user.entity.User;
import com.example.learning_backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    // ponytail: Vietnamese grading bands hardcoded, ordered high to low so the first match wins;
    // move to assessment config only if a course actually needs its own thresholds.
    private static final List<Band> BANDS = List.of(
        new Band("GIOI", "Giỏi", BigDecimal.valueOf(80), HUNDRED),
        new Band("KHA", "Khá", BigDecimal.valueOf(65), BigDecimal.valueOf(80)),
        new Band("TRUNG_BINH", "Trung bình", BigDecimal.valueOf(50), BigDecimal.valueOf(65)),
        new Band("YEU", "Yếu", BigDecimal.ZERO, BigDecimal.valueOf(50))
    );

    private final AssessmentAttemptRepository attemptRepository;
    private final AnswerRepository answerRepository;
    private final AssessmentRepository assessmentRepository;
    private final AssessmentQuestionSelectionRepository selectionRepository;
    private final UserRepository userRepository;
    private final CourseAccessPolicy courseAccessPolicy;
    private final EnrollmentAccessPolicy enrollmentAccessPolicy;

    public AnalyticsService(
        AssessmentAttemptRepository attemptRepository,
        AnswerRepository answerRepository,
        AssessmentRepository assessmentRepository,
        AssessmentQuestionSelectionRepository selectionRepository,
        UserRepository userRepository,
        CourseAccessPolicy courseAccessPolicy,
        EnrollmentAccessPolicy enrollmentAccessPolicy
    ) {
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
        this.assessmentRepository = assessmentRepository;
        this.selectionRepository = selectionRepository;
        this.userRepository = userRepository;
        this.courseAccessPolicy = courseAccessPolicy;
        this.enrollmentAccessPolicy = enrollmentAccessPolicy;
    }

    public List<LeaderboardEntryResponse> assessmentLeaderboard(Long assessmentId, Authentication authentication) {
        Assessment assessment = requireAssessment(assessmentId);
        if (!courseAccessPolicy.canManage(assessment.getCourse(), authentication)) {
            User viewer = requireUser(authentication.getName());
            enrollmentAccessPolicy.requireActive(viewer.getId(), assessment.getCourse().getId());
        }

        List<Standing> ranked = latestPerUser(gradedAttempts(assessmentId)).stream()
            .map(attempt -> new Standing(
                attempt.getUser(),
                scoreOf(attempt),
                maxScoreOf(attempt),
                durationSeconds(attempt),
                attempt.getSubmittedAt(),
                1L
            ))
            .sorted(standingOrder())
            .toList();

        List<Integer> ranks = competitionRanks(ranked);
        List<LeaderboardEntryResponse> entries = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            Standing standing = ranked.get(index);
            entries.add(new LeaderboardEntryResponse(
                ranks.get(index),
                standing.user.getId(),
                standing.user.getFullName(),
                standing.score.setScale(2, RoundingMode.HALF_UP),
                standing.maxScore.setScale(2, RoundingMode.HALF_UP),
                percentage(standing.score, standing.maxScore),
                standing.durationSeconds,
                standing.submittedAt
            ));
        }
        return entries;
    }

    /**
     * System-wide ranking: each student's latest graded attempt per assessment, summed. Score is
     * the primary key and total time the tie-break, mirroring the per-assessment rule — so taking
     * more assessments genuinely earns a higher standing.
     */
    public List<GlobalLeaderboardEntryResponse> globalLeaderboard() {
        Map<Long, Totals> totalsByUser = new LinkedHashMap<>();
        for (AssessmentAttempt attempt : latestPerUserAndAssessment(attemptRepository.findByStatus(AttemptStatus.GRADED))) {
            totalsByUser
                .computeIfAbsent(attempt.getUser().getId(), key -> new Totals(attempt.getUser()))
                .add(scoreOf(attempt), maxScoreOf(attempt), durationSeconds(attempt));
        }

        List<Standing> ranked = totalsByUser.values().stream()
            .map(totals -> new Standing(
                totals.user,
                totals.score,
                totals.maxScore,
                totals.durationSeconds,
                null,
                totals.assessmentCount
            ))
            .sorted(standingOrder())
            .toList();

        List<Integer> ranks = competitionRanks(ranked);
        List<GlobalLeaderboardEntryResponse> entries = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            Standing standing = ranked.get(index);
            entries.add(new GlobalLeaderboardEntryResponse(
                ranks.get(index),
                standing.user.getId(),
                standing.user.getFullName(),
                standing.assessmentCount,
                standing.score.setScale(2, RoundingMode.HALF_UP),
                standing.maxScore.setScale(2, RoundingMode.HALF_UP),
                percentage(standing.score, standing.maxScore),
                standing.durationSeconds
            ));
        }
        return entries;
    }

    public AssessmentReportResponse assessmentReport(Long assessmentId, Authentication authentication) {
        Assessment assessment = requireAssessment(assessmentId);
        courseAccessPolicy.ensureCanManage(assessment.getCourse(), authentication);

        List<AssessmentAttempt> graded = latestPerUser(gradedAttempts(assessmentId));
        long pendingGrading = attemptRepository
            .findByAssessmentIdAndStatus(assessmentId, AttemptStatus.SUBMITTED).size();
        long passed = graded.stream().filter(attempt -> Boolean.TRUE.equals(attempt.getPassed())).count();

        return new AssessmentReportResponse(
            assessment.getId(),
            assessment.getTitle(),
            graded.size(),
            pendingGrading,
            graded.size(),
            average(graded.stream().map(this::scoreOf).toList()),
            average(graded.stream().map(attempt -> percentage(scoreOf(attempt), maxScoreOf(attempt))).toList()),
            graded.stream().map(this::scoreOf).max(BigDecimal::compareTo).orElse(null),
            graded.stream().map(this::scoreOf).min(BigDecimal::compareTo).orElse(null),
            passed,
            ratio(passed, graded.size()),
            distribution(graded),
            questionAnalysis(assessmentId)
        );
    }

    private List<AssessmentAttempt> gradedAttempts(Long assessmentId) {
        return attemptRepository.findByAssessmentIdAndStatus(assessmentId, AttemptStatus.GRADED);
    }

    /**
     * Keeps each student's most recent attempt. Attempt numbers are unique per (assessment, user)
     * via {@code uk_attempts_assessment_user_no}, so the highest one is unambiguously the latest.
     */
    private List<AssessmentAttempt> latestPerUser(List<AssessmentAttempt> attempts) {
        Map<Long, AssessmentAttempt> latest = new LinkedHashMap<>();
        for (AssessmentAttempt attempt : attempts) {
            latest.merge(attempt.getUser().getId(), attempt, this::higherAttemptNo);
        }
        return new ArrayList<>(latest.values());
    }

    private List<AssessmentAttempt> latestPerUserAndAssessment(List<AssessmentAttempt> attempts) {
        Map<String, AssessmentAttempt> latest = new LinkedHashMap<>();
        for (AssessmentAttempt attempt : attempts) {
            String key = attempt.getUser().getId() + ":" + attempt.getAssessment().getId();
            latest.merge(key, attempt, this::higherAttemptNo);
        }
        return new ArrayList<>(latest.values());
    }

    private AssessmentAttempt higherAttemptNo(AssessmentAttempt current, AssessmentAttempt candidate) {
        return candidate.getAttemptNo() > current.getAttemptNo() ? candidate : current;
    }

    private Comparator<Standing> standingOrder() {
        return Comparator.comparing((Standing standing) -> standing.score).reversed()
            .thenComparingLong(standing -> standing.durationSeconds);
    }

    /** Standard competition ranking: tied rows share a rank and the next rank skips ahead (1,2,2,4). */
    private List<Integer> competitionRanks(List<Standing> ranked) {
        List<Integer> ranks = new ArrayList<>(ranked.size());
        int currentRank = 0;
        for (int index = 0; index < ranked.size(); index++) {
            if (index == 0 || !ranked.get(index - 1).ties(ranked.get(index))) {
                currentRank = index + 1;
            }
            ranks.add(currentRank);
        }
        return ranks;
    }

    private List<ScoreDistributionResponse> distribution(List<AssessmentAttempt> attempts) {
        Map<String, Long> counts = new HashMap<>();
        for (AssessmentAttempt attempt : attempts) {
            counts.merge(bandOf(percentage(scoreOf(attempt), maxScoreOf(attempt))).code(), 1L, Long::sum);
        }
        return BANDS.stream()
            .map(band -> new ScoreDistributionResponse(
                band.code(),
                band.label(),
                band.min(),
                band.max(),
                counts.getOrDefault(band.code(), 0L),
                ratio(counts.getOrDefault(band.code(), 0L), attempts.size())
            ))
            .toList();
    }

    private Band bandOf(BigDecimal percentage) {
        return BANDS.stream()
            .filter(band -> percentage.compareTo(band.min()) >= 0)
            .findFirst()
            .orElse(BANDS.get(BANDS.size() - 1));
    }

    private List<QuestionAnalysisResponse> questionAnalysis(Long assessmentId) {
        Map<Long, QuestionStatRow> stats = new HashMap<>();
        for (QuestionStatRow row : answerRepository.findQuestionStats(assessmentId, AttemptStatus.GRADED)) {
            stats.put(row.questionId(), row);
        }

        List<QuestionAnalysisResponse> analysis = new ArrayList<>();
        for (AssessmentQuestionSelection selection : selectionRepository.findByAssessmentIdOrderByPosition(assessmentId)) {
            Question question = selection.getQuestion();
            QuestionStatRow row = stats.get(question.getId());
            long answered = row == null ? 0L : row.answeredCount();
            long correct = row == null || row.correctCount() == null ? 0L : row.correctCount();
            long wrong = answered - correct;
            analysis.add(new QuestionAnalysisResponse(
                question.getId(),
                selection.getPosition(),
                question.getQuestionText(),
                question.getType(),
                question.getDifficulty(),
                answered,
                correct,
                wrong,
                ratio(wrong, answered)
            ));
        }
        analysis.sort(Comparator.comparing(QuestionAnalysisResponse::wrongRate).reversed()
            .thenComparing(QuestionAnalysisResponse::position));
        return analysis;
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(BigDecimal score, BigDecimal maxScore) {
        if (maxScore == null || maxScore.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return score.multiply(HUNDRED).divide(maxScore, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(long part, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(part).multiply(HUNDRED)
            .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal scoreOf(AssessmentAttempt attempt) {
        return attempt.getScore() == null ? BigDecimal.ZERO : attempt.getScore();
    }

    private BigDecimal maxScoreOf(AssessmentAttempt attempt) {
        return attempt.getMaxScore() == null ? BigDecimal.ZERO : attempt.getMaxScore();
    }

    /**
     * Elapsed exam time. An attempt with no {@code submittedAt} sorts last rather than first, so a
     * missing timestamp can never win a tie-break.
     */
    private long durationSeconds(AssessmentAttempt attempt) {
        if (attempt.getStartedAt() == null || attempt.getSubmittedAt() == null) {
            return Long.MAX_VALUE;
        }
        return Duration.between(attempt.getStartedAt(), attempt.getSubmittedAt()).toSeconds();
    }

    private Assessment requireAssessment(Long assessmentId) {
        return assessmentRepository.findById(assessmentId)
            .orElseThrow(() -> new IllegalArgumentException("Assessment not found: " + assessmentId));
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
    }

    private record Band(String code, String label, BigDecimal min, BigDecimal max) {
    }

    /** A user's ranked position, shared by both leaderboards so ordering and ties behave identically. */
    private record Standing(
        User user,
        BigDecimal score,
        BigDecimal maxScore,
        long durationSeconds,
        LocalDateTime submittedAt,
        long assessmentCount
    ) {
        private boolean ties(Standing other) {
            return score.compareTo(other.score) == 0 && durationSeconds == other.durationSeconds;
        }
    }

    private static final class Totals {

        private final User user;
        private BigDecimal score = BigDecimal.ZERO;
        private BigDecimal maxScore = BigDecimal.ZERO;
        private long durationSeconds;
        private long assessmentCount;

        private Totals(User user) {
            this.user = user;
        }

        private void add(BigDecimal attemptScore, BigDecimal attemptMaxScore, long seconds) {
            score = score.add(attemptScore);
            maxScore = maxScore.add(attemptMaxScore);
            durationSeconds += seconds == Long.MAX_VALUE ? 0L : seconds;
            assessmentCount++;
        }
    }
}
