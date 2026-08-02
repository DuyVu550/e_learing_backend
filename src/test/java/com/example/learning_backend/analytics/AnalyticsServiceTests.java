package com.example.learning_backend.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.learning_backend.analytics.dto.AssessmentReportResponse;
import com.example.learning_backend.analytics.dto.GlobalLeaderboardEntryResponse;
import com.example.learning_backend.analytics.dto.LeaderboardEntryResponse;
import com.example.learning_backend.analytics.dto.QuestionAnalysisResponse;
import com.example.learning_backend.analytics.dto.ScoreDistributionResponse;
import com.example.learning_backend.analytics.service.AnalyticsService;
import com.example.learning_backend.assessment.entity.Assessment;
import com.example.learning_backend.assessment.entity.AssessmentQuestionSelection;
import com.example.learning_backend.assessment.entity.Question;
import com.example.learning_backend.assessment.entity.QuestionOption;
import com.example.learning_backend.assessment.enums.AssessmentCompositionMode;
import com.example.learning_backend.assessment.enums.AssessmentStatus;
import com.example.learning_backend.assessment.enums.AssessmentType;
import com.example.learning_backend.assessment.enums.QuestionDifficulty;
import com.example.learning_backend.assessment.enums.QuestionType;
import com.example.learning_backend.assessment.repository.AssessmentQuestionSelectionRepository;
import com.example.learning_backend.assessment.repository.AssessmentRepository;
import com.example.learning_backend.assessment.repository.QuestionOptionRepository;
import com.example.learning_backend.assessment.repository.QuestionRepository;
import com.example.learning_backend.course.entity.Course;
import com.example.learning_backend.course.repository.CourseRepository;
import com.example.learning_backend.enrollment.entity.Enrollment;
import com.example.learning_backend.enrollment.enums.EnrollmentStatus;
import com.example.learning_backend.enrollment.repository.EnrollmentRepository;
import com.example.learning_backend.submission.entity.Answer;
import com.example.learning_backend.submission.entity.AssessmentAttempt;
import com.example.learning_backend.submission.enums.AttemptStatus;
import com.example.learning_backend.submission.repository.AnswerRepository;
import com.example.learning_backend.submission.repository.AssessmentAttemptRepository;
import com.example.learning_backend.user.entity.User;
import com.example.learning_backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AnalyticsServiceTests {

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private AssessmentRepository assessmentRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private QuestionOptionRepository questionOptionRepository;

    @Autowired
    private AssessmentQuestionSelectionRepository selectionRepository;

    @Autowired
    private AssessmentAttemptRepository attemptRepository;

    @Autowired
    private AnswerRepository answerRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    private User instructor;
    private Course course;
    private Assessment assessment;
    private Authentication instructorAuth;
    private LocalDateTime start;

    @BeforeEach
    void setUp() {
        instructor = saveUser("teacher@example.com", "Teacher");
        course = new Course();
        course.setSlug("analytics-course");
        course.setTitle("Analytics Course");
        course.setInstructor(instructor);
        course = courseRepository.save(course);

        assessment = new Assessment();
        assessment.setCourse(course);
        assessment.setTitle("Java Final");
        assessment.setType(AssessmentType.EXAM);
        assessment.setCompositionMode(AssessmentCompositionMode.FIXED);
        assessment.setStatus(AssessmentStatus.PUBLISHED);
        assessment.setPassingScore(BigDecimal.valueOf(50));
        assessment = assessmentRepository.save(assessment);

        instructorAuth = auth("teacher@example.com", "ROLE_INSTRUCTOR");
        start = LocalDateTime.now().minusHours(2);
    }

    @Test
    void leaderboardUsesLatestAttemptEvenWhenAnEarlierOneScoredHigher() {
        User student = enrolledStudent("s1@example.com", "Student One");
        gradedAttempt(student, 1, "10.00", "10.00", 600);
        gradedAttempt(student, 2, "4.00", "10.00", 600);

        List<LeaderboardEntryResponse> board = analyticsService.assessmentLeaderboard(assessment.getId(), instructorAuth);

        assertThat(board).hasSize(1);
        assertThat(board.getFirst().score()).isEqualByComparingTo("4.00");
    }

    @Test
    void equalScoresAreRankedByShorterDuration() {
        User fast = enrolledStudent("fast@example.com", "Fast");
        User slow = enrolledStudent("slow@example.com", "Slow");
        gradedAttempt(slow, 1, "8.00", "10.00", 900);
        gradedAttempt(fast, 1, "8.00", "10.00", 300);

        List<LeaderboardEntryResponse> board = analyticsService.assessmentLeaderboard(assessment.getId(), instructorAuth);

        assertThat(board).extracting(LeaderboardEntryResponse::fullName).containsExactly("Fast", "Slow");
        assertThat(board).extracting(LeaderboardEntryResponse::rank).containsExactly(1, 2);
        assertThat(board.getFirst().durationSeconds()).isEqualTo(300L);
    }

    @Test
    void identicalScoreAndDurationShareRankAndTheNextRankSkips() {
        User a = enrolledStudent("a@example.com", "Alpha");
        User b = enrolledStudent("b@example.com", "Beta");
        User c = enrolledStudent("c@example.com", "Gamma");
        gradedAttempt(a, 1, "9.00", "10.00", 300);
        gradedAttempt(b, 1, "9.00", "10.00", 300);
        gradedAttempt(c, 1, "5.00", "10.00", 300);

        List<LeaderboardEntryResponse> board = analyticsService.assessmentLeaderboard(assessment.getId(), instructorAuth);

        assertThat(board).extracting(LeaderboardEntryResponse::rank).containsExactly(1, 1, 3);
    }

    @Test
    void leaderboardExcludesAttemptsStillAwaitingManualGrading() {
        User graded = enrolledStudent("graded@example.com", "Graded");
        User pending = enrolledStudent("pending@example.com", "Pending");
        gradedAttempt(graded, 1, "7.00", "10.00", 300);
        attempt(pending, 1, AttemptStatus.SUBMITTED, "9.00", "10.00", 300);

        List<LeaderboardEntryResponse> board = analyticsService.assessmentLeaderboard(assessment.getId(), instructorAuth);

        assertThat(board).extracting(LeaderboardEntryResponse::fullName).containsExactly("Graded");
    }

    @Test
    void leaderboardNeverExposesEmailAddresses() {
        assertThat(LeaderboardEntryResponse.class.getRecordComponents())
            .extracting(java.lang.reflect.RecordComponent::getName)
            .doesNotContain("email");
        assertThat(GlobalLeaderboardEntryResponse.class.getRecordComponents())
            .extracting(java.lang.reflect.RecordComponent::getName)
            .doesNotContain("email");
    }

    @Test
    void studentWhoIsNotEnrolledCannotSeeTheLeaderboard() {
        User outsider = saveUser("outsider@example.com", "Outsider");
        Authentication outsiderAuth = auth(outsider.getEmail(), "ROLE_STUDENT");

        assertThatThrownBy(() -> analyticsService.assessmentLeaderboard(assessment.getId(), outsiderAuth))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not enrolled");
    }

    @Test
    void enrolledStudentCanSeeTheLeaderboard() {
        User student = enrolledStudent("s@example.com", "Student");
        gradedAttempt(student, 1, "6.00", "10.00", 300);

        List<LeaderboardEntryResponse> board = analyticsService
            .assessmentLeaderboard(assessment.getId(), auth(student.getEmail(), "ROLE_STUDENT"));

        assertThat(board).hasSize(1);
    }

    @Test
    void reportIsRejectedForAnInstructorWhoDoesNotOwnTheCourse() {
        saveUser("other@example.com", "Other Teacher");
        Authentication otherAuth = auth("other@example.com", "ROLE_INSTRUCTOR");

        assertThatThrownBy(() -> analyticsService.assessmentReport(assessment.getId(), otherAuth))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot manage");
    }

    @Test
    void distributionPlacesAttemptsInTheCorrectBandAtTheBoundaries() {
        gradedAttempt(enrolledStudent("g@example.com", "Gioi"), 1, "8.00", "10.00", 300);
        gradedAttempt(enrolledStudent("k@example.com", "Kha"), 1, "6.50", "10.00", 300);
        gradedAttempt(enrolledStudent("t@example.com", "TrungBinh"), 1, "5.00", "10.00", 300);
        gradedAttempt(enrolledStudent("y@example.com", "Yeu"), 1, "4.90", "10.00", 300);

        AssessmentReportResponse report = analyticsService.assessmentReport(assessment.getId(), instructorAuth);

        assertThat(report.distribution())
            .extracting(ScoreDistributionResponse::band, ScoreDistributionResponse::attemptCount)
            .containsExactly(
                org.assertj.core.api.Assertions.tuple("GIOI", 1L),
                org.assertj.core.api.Assertions.tuple("KHA", 1L),
                org.assertj.core.api.Assertions.tuple("TRUNG_BINH", 1L),
                org.assertj.core.api.Assertions.tuple("YEU", 1L)
            );
        assertThat(report.gradedAttemptCount()).isEqualTo(4);
    }

    @Test
    void reportSurvivesAttemptsWithNullOrZeroMaxScore() {
        gradedAttempt(enrolledStudent("z@example.com", "Zero"), 1, "0.00", "0.00", 300);
        AssessmentAttempt nulls = attempt(enrolledStudent("n@example.com", "Null"), 1, AttemptStatus.GRADED, null, null, 300);
        assertThat(nulls.getScore()).isNull();

        AssessmentReportResponse report = analyticsService.assessmentReport(assessment.getId(), instructorAuth);

        assertThat(report.averagePercentage()).isEqualByComparingTo("0.00");
        assertThat(report.gradedAttemptCount()).isEqualTo(2);
    }

    @Test
    void reportCountsAttemptsAwaitingManualGradingSeparately() {
        gradedAttempt(enrolledStudent("done@example.com", "Done"), 1, "7.00", "10.00", 300);
        attempt(enrolledStudent("wait@example.com", "Wait"), 1, AttemptStatus.SUBMITTED, "5.00", "10.00", 300);

        AssessmentReportResponse report = analyticsService.assessmentReport(assessment.getId(), instructorAuth);

        assertThat(report.gradedAttemptCount()).isEqualTo(1);
        assertThat(report.pendingGradingCount()).isEqualTo(1);
    }

    @Test
    void questionAnalysisComputesWrongRateAndFlagsUnansweredQuestions() {
        Question answered = question("Which keyword creates a subclass?");
        Question untouched = question("What does JVM stand for?");
        select(answered, 1);
        select(untouched, 2);

        User right = enrolledStudent("r@example.com", "Right");
        User wrong1 = enrolledStudent("w1@example.com", "Wrong One");
        User wrong2 = enrolledStudent("w2@example.com", "Wrong Two");
        answer(gradedAttempt(right, 1, "4.00", "8.00", 300), answered, true);
        answer(gradedAttempt(wrong1, 1, "0.00", "8.00", 300), answered, false);
        answer(gradedAttempt(wrong2, 1, "0.00", "8.00", 300), answered, false);

        AssessmentReportResponse report = analyticsService.assessmentReport(assessment.getId(), instructorAuth);

        QuestionAnalysisResponse hardest = report.questions().getFirst();
        assertThat(hardest.questionId()).isEqualTo(answered.getId());
        assertThat(hardest.answeredCount()).isEqualTo(3);
        assertThat(hardest.correctCount()).isEqualTo(1);
        assertThat(hardest.wrongRate()).isEqualByComparingTo("66.67");

        QuestionAnalysisResponse skipped = report.questions().get(1);
        assertThat(skipped.questionId()).isEqualTo(untouched.getId());
        assertThat(skipped.answeredCount()).isZero();
        assertThat(skipped.wrongRate()).isEqualByComparingTo("0.00");
    }

    @Test
    void globalLeaderboardSumsTheLatestAttemptOfEachAssessment() {
        Assessment second = new Assessment();
        second.setCourse(course);
        second.setTitle("Second Exam");
        second.setType(AssessmentType.QUIZ);
        second.setCompositionMode(AssessmentCompositionMode.FIXED);
        second.setStatus(AssessmentStatus.PUBLISHED);
        second = assessmentRepository.save(second);

        User student = enrolledStudent("multi@example.com", "Multi");
        gradedAttempt(student, 1, "6.00", "10.00", 300);
        AssessmentAttempt other = new AssessmentAttempt();
        other.setAssessment(second);
        other.setUser(student);
        other.setAttemptNo(1);
        other.setStatus(AttemptStatus.GRADED);
        other.setStartedAt(start);
        other.setSubmittedAt(start.plusSeconds(120));
        other.setScore(new BigDecimal("3.00"));
        other.setMaxScore(new BigDecimal("5.00"));
        attemptRepository.saveAndFlush(other);

        List<GlobalLeaderboardEntryResponse> board = analyticsService.globalLeaderboard();

        GlobalLeaderboardEntryResponse entry = board.stream()
            .filter(row -> row.userId().equals(student.getId()))
            .findFirst()
            .orElseThrow();
        assertThat(entry.assessmentCount()).isEqualTo(2);
        assertThat(entry.totalScore()).isEqualByComparingTo("9.00");
        assertThat(entry.totalMaxScore()).isEqualByComparingTo("15.00");
        assertThat(entry.percentage()).isEqualByComparingTo("60.00");
        assertThat(entry.totalDurationSeconds()).isEqualTo(420L);
    }

    private User saveUser(String email, String fullName) {
        User user = new User();
        user.setEmail(email);
        user.setFullName(fullName);
        user.setPasswordHash("hash");
        return userRepository.save(user);
    }

    private User enrolledStudent(String email, String fullName) {
        User student = saveUser(email, fullName);
        Enrollment enrollment = new Enrollment();
        enrollment.setUser(student);
        enrollment.setCourse(course);
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollmentRepository.save(enrollment);
        return student;
    }

    private AssessmentAttempt gradedAttempt(User user, int attemptNo, String score, String maxScore, int seconds) {
        return attempt(user, attemptNo, AttemptStatus.GRADED, score, maxScore, seconds);
    }

    private AssessmentAttempt attempt(
        User user,
        int attemptNo,
        AttemptStatus status,
        String score,
        String maxScore,
        int seconds
    ) {
        AssessmentAttempt attempt = new AssessmentAttempt();
        attempt.setAssessment(assessment);
        attempt.setUser(user);
        attempt.setAttemptNo(attemptNo);
        attempt.setStatus(status);
        attempt.setStartedAt(start);
        attempt.setSubmittedAt(start.plusSeconds(seconds));
        attempt.setScore(score == null ? null : new BigDecimal(score));
        attempt.setMaxScore(maxScore == null ? null : new BigDecimal(maxScore));
        if (score != null && maxScore != null && new BigDecimal(maxScore).signum() > 0) {
            attempt.setPassed(new BigDecimal(score).multiply(BigDecimal.valueOf(100))
                .divide(new BigDecimal(maxScore), 2, java.math.RoundingMode.HALF_UP)
                .compareTo(BigDecimal.valueOf(50)) >= 0);
        }
        return attemptRepository.saveAndFlush(attempt);
    }

    private Question question(String text) {
        Question question = new Question();
        question.setCourse(course);
        question.setQuestionText(text);
        question.setType(QuestionType.SINGLE_CHOICE);
        question.setDifficulty(QuestionDifficulty.EASY);
        question.setPoints(BigDecimal.valueOf(4));
        question = questionRepository.save(question);

        QuestionOption right = new QuestionOption();
        right.setQuestion(question);
        right.setOptionText("correct");
        right.setCorrect(Boolean.TRUE);
        right.setPosition(1);
        questionOptionRepository.save(right);
        return question;
    }

    private void select(Question question, int position) {
        AssessmentQuestionSelection selection = new AssessmentQuestionSelection();
        selection.setAssessment(assessment);
        selection.setQuestion(question);
        selection.setPosition(position);
        selection.setPoints(BigDecimal.valueOf(4));
        selectionRepository.save(selection);
    }

    private void answer(AssessmentAttempt attempt, Question question, boolean correct) {
        Answer answer = new Answer();
        answer.setAttempt(attempt);
        answer.setQuestion(question);
        answer.setCorrect(correct);
        answer.setScore(correct ? BigDecimal.valueOf(4) : BigDecimal.ZERO);
        answerRepository.saveAndFlush(answer);
    }

    private Authentication auth(String email, String role) {
        return new UsernamePasswordAuthenticationToken(email, null, List.of(new SimpleGrantedAuthority(role)));
    }
}
