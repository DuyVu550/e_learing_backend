package com.example.learning_backend.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.learning_backend.assessment.dto.AssessmentCreateRequest;
import com.example.learning_backend.assessment.dto.AssessmentQuestionSelectionRequest;
import com.example.learning_backend.assessment.dto.AssessmentUpdateRequest;
import com.example.learning_backend.assessment.dto.QuestionOptionRequest;
import com.example.learning_backend.assessment.dto.QuestionRequest;
import com.example.learning_backend.assessment.dto.QuestionResponse;
import com.example.learning_backend.assessment.enums.AssessmentCompositionMode;
import com.example.learning_backend.assessment.enums.AssessmentStatus;
import com.example.learning_backend.assessment.enums.AssessmentType;
import com.example.learning_backend.assessment.enums.QuestionDifficulty;
import com.example.learning_backend.assessment.enums.QuestionType;
import com.example.learning_backend.assessment.service.AssessmentService;
import com.example.learning_backend.assessment.service.QuestionBankService;
import com.example.learning_backend.course.entity.Course;
import com.example.learning_backend.course.repository.CourseRepository;
import com.example.learning_backend.enrollment.entity.Enrollment;
import com.example.learning_backend.enrollment.enums.EnrollmentStatus;
import com.example.learning_backend.enrollment.repository.EnrollmentRepository;
import com.example.learning_backend.enrollment.service.EnrollmentService;
import com.example.learning_backend.submission.dto.AnswerSubmitRequest;
import com.example.learning_backend.submission.dto.AssessmentSubmitRequest;
import com.example.learning_backend.submission.dto.AttemptDetailResponse;
import com.example.learning_backend.submission.dto.AttemptQuestionResponse;
import com.example.learning_backend.submission.dto.AttemptResultResponse;
import com.example.learning_backend.submission.dto.ManualGradeRequest;
import com.example.learning_backend.submission.entity.AssessmentAttempt;
import com.example.learning_backend.submission.enums.AttemptStatus;
import com.example.learning_backend.submission.repository.AssessmentAttemptRepository;
import com.example.learning_backend.submission.service.SubmissionService;
import com.example.learning_backend.user.entity.User;
import com.example.learning_backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
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
class SubmissionServiceTests {

    private static final String STUDENT_EMAIL = "student@example.com";

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private AssessmentService assessmentService;

    @Autowired
    private QuestionBankService questionBankService;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private AssessmentAttemptRepository attemptRepository;

    private Course course;
    private Authentication instructorAuth;

    @BeforeEach
    void setUp() {
        User instructor = new User();
        instructor.setEmail("teacher@example.com");
        instructor.setFullName("Teacher");
        instructor.setPasswordHash("hash");
        instructor = userRepository.save(instructor);

        User student = new User();
        student.setEmail(STUDENT_EMAIL);
        student.setFullName("Student");
        student.setPasswordHash("hash");
        userRepository.save(student);

        course = new Course();
        course.setSlug("exam-course");
        course.setTitle("Exam Course");
        course.setInstructor(instructor);
        course = courseRepository.save(course);

        instructorAuth = new UsernamePasswordAuthenticationToken(
            instructor.getEmail(),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_INSTRUCTOR"))
        );
    }

    @Test
    void attemptDetailHidesCorrectAnswersAndExplanations() {
        Long assessmentId = publishedAssessmentWithSingleChoice();
        enrollmentService.enroll(STUDENT_EMAIL, course.getId());

        AttemptDetailResponse detail = submissionService.startOrResume(STUDENT_EMAIL, assessmentId);

        assertThat(detail.questions()).hasSize(1);
        AttemptQuestionResponse question = detail.questions().getFirst();
        assertThat(question.options()).hasSize(2);
        // The student-facing DTO has no field that could carry correctness or the explanation.
        assertThat(AttemptQuestionResponse.class.getRecordComponents())
            .extracting(java.lang.reflect.RecordComponent::getName)
            .doesNotContain("correct", "expectedAnswer", "explanation");
        assertThat(detail.attempt().status()).isEqualTo(AttemptStatus.IN_PROGRESS);
    }

    @Test
    void startRejectsStudentWhoIsNotEnrolled() {
        Long assessmentId = publishedAssessmentWithSingleChoice();

        assertThatThrownBy(() -> submissionService.startOrResume(STUDENT_EMAIL, assessmentId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not enrolled");
    }

    @Test
    void startRejectsCancelledEnrollment() {
        Long assessmentId = publishedAssessmentWithSingleChoice();
        enrollmentService.enroll(STUDENT_EMAIL, course.getId());
        Enrollment enrollment = enrollmentRepository
            .findByUserIdAndCourseId(userRepository.findByEmail(STUDENT_EMAIL).orElseThrow().getId(), course.getId())
            .orElseThrow();
        enrollment.setStatus(EnrollmentStatus.CANCELLED);
        enrollmentRepository.save(enrollment);

        assertThatThrownBy(() -> submissionService.startOrResume(STUDENT_EMAIL, assessmentId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cancelled");
    }

    @Test
    void startRejectsDraftAssessment() {
        QuestionResponse question = createSingleChoiceQuestion();
        Long assessmentId = createAssessment(30, 1, BigDecimal.valueOf(50), false);
        assessmentService.addSelection(
            assessmentId,
            new AssessmentQuestionSelectionRequest(question.id(), 1, null),
            instructorAuth
        );
        enrollmentService.enroll(STUDENT_EMAIL, course.getId());

        assertThatThrownBy(() -> submissionService.startOrResume(STUDENT_EMAIL, assessmentId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not published");
    }

    @Test
    void startRejectsWhenMaxAttemptsReached() {
        Long assessmentId = publishedAssessmentWithSingleChoice();
        enrollmentService.enroll(STUDENT_EMAIL, course.getId());
        AttemptDetailResponse first = submissionService.startOrResume(STUDENT_EMAIL, assessmentId);
        submissionService.submit(STUDENT_EMAIL, first.attempt().id(), new AssessmentSubmitRequest(List.of()));

        assertThatThrownBy(() -> submissionService.startOrResume(STUDENT_EMAIL, assessmentId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No attempts left");
    }

    @Test
    void resumeReturnsTheSameInProgressAttempt() {
        Long assessmentId = publishedAssessmentWithSingleChoice();
        enrollmentService.enroll(STUDENT_EMAIL, course.getId());

        AttemptDetailResponse first = submissionService.startOrResume(STUDENT_EMAIL, assessmentId);
        AttemptDetailResponse resumed = submissionService.startOrResume(STUDENT_EMAIL, assessmentId);

        assertThat(resumed.attempt().id()).isEqualTo(first.attempt().id());
        assertThat(resumed.attempt().attemptNo()).isEqualTo(1);
    }

    @Test
    void draftSavesAnswerTextAndFlag() {
        Long assessmentId = publishedAssessmentWithSingleChoice();
        enrollmentService.enroll(STUDENT_EMAIL, course.getId());
        AttemptDetailResponse detail = submissionService.startOrResume(STUDENT_EMAIL, assessmentId);
        Long questionId = detail.questions().getFirst().questionId();
        Long optionId = detail.questions().getFirst().options().getFirst().id();

        AttemptDetailResponse saved = submissionService.saveDraft(
            STUDENT_EMAIL,
            detail.attempt().id(),
            new AssessmentSubmitRequest(List.of(new AnswerSubmitRequest(questionId, null, Set.of(optionId), true)))
        );

        AttemptQuestionResponse question = saved.questions().getFirst();
        assertThat(question.selectedOptionIds()).containsExactly(optionId);
        assertThat(question.flagged()).isTrue();
        assertThat(saved.attempt().status()).isEqualTo(AttemptStatus.IN_PROGRESS);
    }

    @Test
    void draftPreservesFlagWhenOmitted() {
        Long assessmentId = publishedAssessmentWithSingleChoice();
        enrollmentService.enroll(STUDENT_EMAIL, course.getId());
        AttemptDetailResponse detail = submissionService.startOrResume(STUDENT_EMAIL, assessmentId);
        Long attemptId = detail.attempt().id();
        Long questionId = detail.questions().getFirst().questionId();
        submissionService.saveDraft(
            STUDENT_EMAIL,
            attemptId,
            new AssessmentSubmitRequest(List.of(new AnswerSubmitRequest(questionId, null, Set.of(), true)))
        );

        AttemptDetailResponse saved = submissionService.saveDraft(
            STUDENT_EMAIL,
            attemptId,
            new AssessmentSubmitRequest(List.of(new AnswerSubmitRequest(questionId, "note", Set.of(), null)))
        );

        assertThat(saved.questions().getFirst().flagged()).isTrue();
    }

    @Test
    void expiredAttemptIsAutoSubmittedOnNextAccess() {
        Long assessmentId = publishedAssessmentWithSingleChoice();
        enrollmentService.enroll(STUDENT_EMAIL, course.getId());
        AttemptDetailResponse detail = submissionService.startOrResume(STUDENT_EMAIL, assessmentId);
        Long attemptId = detail.attempt().id();
        backdateAttempt(attemptId, 120);

        AttemptDetailResponse reloaded = submissionService.getAttempt(STUDENT_EMAIL, attemptId);

        assertThat(reloaded.attempt().status()).isEqualTo(AttemptStatus.GRADED);
        assertThat(reloaded.attempt().submittedAt()).isNotNull();
        assertThatThrownBy(() -> submissionService.saveDraft(
            STUDENT_EMAIL,
            attemptId,
            new AssessmentSubmitRequest(List.of())
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void singleChoiceIsGradedAutomatically() {
        Long assessmentId = publishedAssessmentWithSingleChoice();
        enrollmentService.enroll(STUDENT_EMAIL, course.getId());
        AttemptDetailResponse detail = submissionService.startOrResume(STUDENT_EMAIL, assessmentId);
        Long questionId = detail.questions().getFirst().questionId();
        Long correctOptionId = detail.questions().getFirst().options().stream()
            .filter(option -> option.optionText().equals("extends"))
            .findFirst()
            .orElseThrow()
            .id();

        AttemptResultResponse result = submissionService.submit(
            STUDENT_EMAIL,
            detail.attempt().id(),
            new AssessmentSubmitRequest(List.of(new AnswerSubmitRequest(questionId, null, Set.of(correctOptionId), null)))
        );

        assertThat(result.attempt().status()).isEqualTo(AttemptStatus.GRADED);
        // Selection points (4) override the question's own points (2).
        assertThat(result.attempt().score()).isEqualByComparingTo("4.00");
        assertThat(result.attempt().maxScore()).isEqualByComparingTo("4.00");
        assertThat(result.attempt().passed()).isTrue();
        assertThat(result.answers().getFirst().correct()).isTrue();
    }

    @Test
    void multipleChoiceUsesPartialCredit() {
        QuestionResponse question = questionBankService.createQuestion(
            course.getId(),
            new QuestionRequest(
                null,
                "Which are JVM languages?",
                QuestionType.MULTIPLE_CHOICE,
                QuestionDifficulty.MEDIUM,
                BigDecimal.valueOf(10),
                null,
                null,
                List.of(
                    new QuestionOptionRequest("Java", true, 1),
                    new QuestionOptionRequest("Kotlin", true, 2),
                    new QuestionOptionRequest("Scala", true, 3),
                    new QuestionOptionRequest("Python", false, 4)
                )
            ),
            instructorAuth
        );
        Long assessmentId = publishAssessmentWith(question, BigDecimal.valueOf(9), null);
        enrollmentService.enroll(STUDENT_EMAIL, course.getId());
        AttemptDetailResponse detail = submissionService.startOrResume(STUDENT_EMAIL, assessmentId);
        Long javaId = optionId(detail, "Java");
        Long kotlinId = optionId(detail, "Kotlin");
        Long pythonId = optionId(detail, "Python");

        AttemptResultResponse result = submissionService.submit(
            STUDENT_EMAIL,
            detail.attempt().id(),
            new AssessmentSubmitRequest(List.of(
                new AnswerSubmitRequest(question.id(), null, Set.of(javaId, kotlinId, pythonId), null)
            ))
        );

        // 2 correct - 1 wrong = 1, over 3 correct options, times 9 points.
        assertThat(result.answers().getFirst().score()).isEqualByComparingTo("3.00");
        assertThat(result.answers().getFirst().correct()).isFalse();
    }

    @Test
    void multipleChoiceNeverScoresBelowZero() {
        QuestionResponse question = questionBankService.createQuestion(
            course.getId(),
            new QuestionRequest(
                null,
                "Pick the JVM language",
                QuestionType.MULTIPLE_CHOICE,
                QuestionDifficulty.MEDIUM,
                BigDecimal.valueOf(6),
                null,
                null,
                List.of(
                    new QuestionOptionRequest("Java", true, 1),
                    new QuestionOptionRequest("Python", false, 2),
                    new QuestionOptionRequest("Ruby", false, 3)
                )
            ),
            instructorAuth
        );
        Long assessmentId = publishAssessmentWith(question, BigDecimal.valueOf(6), null);
        enrollmentService.enroll(STUDENT_EMAIL, course.getId());
        AttemptDetailResponse detail = submissionService.startOrResume(STUDENT_EMAIL, assessmentId);

        AttemptResultResponse result = submissionService.submit(
            STUDENT_EMAIL,
            detail.attempt().id(),
            new AssessmentSubmitRequest(List.of(new AnswerSubmitRequest(
                question.id(),
                null,
                Set.of(optionId(detail, "Python"), optionId(detail, "Ruby")),
                null
            )))
        );

        assertThat(result.answers().getFirst().score()).isEqualByComparingTo("0.00");
    }

    @Test
    void fillInBlankMatchesIgnoringCaseAndWhitespace() {
        QuestionResponse question = questionBankService.createQuestion(
            course.getId(),
            new QuestionRequest(
                null,
                "Keyword for class inheritance?",
                QuestionType.FILL_IN_BLANK,
                QuestionDifficulty.EASY,
                BigDecimal.valueOf(5),
                "extends",
                null,
                List.of()
            ),
            instructorAuth
        );
        Long assessmentId = publishAssessmentWith(question, BigDecimal.valueOf(5), null);
        enrollmentService.enroll(STUDENT_EMAIL, course.getId());
        AttemptDetailResponse detail = submissionService.startOrResume(STUDENT_EMAIL, assessmentId);

        AttemptResultResponse result = submissionService.submit(
            STUDENT_EMAIL,
            detail.attempt().id(),
            new AssessmentSubmitRequest(List.of(new AnswerSubmitRequest(question.id(), "  Extends ", Set.of(), null)))
        );

        assertThat(result.attempt().status()).isEqualTo(AttemptStatus.GRADED);
        assertThat(result.answers().getFirst().score()).isEqualByComparingTo("5.00");
    }

    @Test
    void shortAnswerWaitsForManualGradingThenCompletesAttempt() {
        QuestionResponse question = questionBankService.createQuestion(
            course.getId(),
            new QuestionRequest(
                null,
                "Explain the JVM memory model.",
                QuestionType.SHORT_ANSWER,
                QuestionDifficulty.HARD,
                BigDecimal.valueOf(10),
                null,
                null,
                List.of()
            ),
            instructorAuth
        );
        Long assessmentId = publishAssessmentWith(question, BigDecimal.valueOf(10), BigDecimal.valueOf(50));
        enrollmentService.enroll(STUDENT_EMAIL, course.getId());
        AttemptDetailResponse detail = submissionService.startOrResume(STUDENT_EMAIL, assessmentId);

        AttemptResultResponse submitted = submissionService.submit(
            STUDENT_EMAIL,
            detail.attempt().id(),
            new AssessmentSubmitRequest(List.of(new AnswerSubmitRequest(question.id(), "Heap and stack.", Set.of(), null)))
        );

        assertThat(submitted.attempt().status()).isEqualTo(AttemptStatus.SUBMITTED);
        assertThat(submitted.awaitingManualGrading()).isTrue();
        assertThat(submitted.attempt().passed()).isNull();

        Long answerId = submitted.answers().getFirst().answerId();
        submissionService.gradeAnswer(
            answerId,
            new ManualGradeRequest(BigDecimal.valueOf(8), "Good, mention the metaspace next time."),
            instructorAuth
        );

        AttemptResultResponse graded = submissionService.getResult(STUDENT_EMAIL, detail.attempt().id());
        assertThat(graded.attempt().status()).isEqualTo(AttemptStatus.GRADED);
        assertThat(graded.attempt().score()).isEqualByComparingTo("8.00");
        assertThat(graded.attempt().passed()).isTrue();
        assertThat(graded.answers().getFirst().feedback()).contains("metaspace");
    }

    @Test
    void manualGradeRejectsScoreAboveQuestionPoints() {
        QuestionResponse question = questionBankService.createQuestion(
            course.getId(),
            new QuestionRequest(
                null,
                "Explain garbage collection.",
                QuestionType.SHORT_ANSWER,
                QuestionDifficulty.HARD,
                BigDecimal.valueOf(10),
                null,
                null,
                List.of()
            ),
            instructorAuth
        );
        Long assessmentId = publishAssessmentWith(question, BigDecimal.valueOf(10), null);
        enrollmentService.enroll(STUDENT_EMAIL, course.getId());
        AttemptDetailResponse detail = submissionService.startOrResume(STUDENT_EMAIL, assessmentId);
        AttemptResultResponse submitted = submissionService.submit(
            STUDENT_EMAIL,
            detail.attempt().id(),
            new AssessmentSubmitRequest(List.of(new AnswerSubmitRequest(question.id(), "Mark and sweep.", Set.of(), null)))
        );
        Long answerId = submitted.answers().getFirst().answerId();

        assertThatThrownBy(() -> submissionService.gradeAnswer(
            answerId,
            new ManualGradeRequest(BigDecimal.valueOf(50), null),
            instructorAuth
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not exceed");
    }

    @Test
    void resultHidesExplanationsUnlessAssessmentAllowsIt() {
        Long hiddenAssessment = publishedAssessmentWithSingleChoice();
        enrollmentService.enroll(STUDENT_EMAIL, course.getId());
        AttemptDetailResponse detail = submissionService.startOrResume(STUDENT_EMAIL, hiddenAssessment);
        AttemptResultResponse hidden = submissionService.submit(
            STUDENT_EMAIL,
            detail.attempt().id(),
            new AssessmentSubmitRequest(List.of(new AnswerSubmitRequest(
                detail.questions().getFirst().questionId(),
                null,
                Set.of(detail.questions().getFirst().options().getFirst().id()),
                null
            )))
        );

        assertThat(hidden.answersRevealed()).isFalse();
        assertThat(hidden.answers().getFirst().explanation()).isNull();
        assertThat(hidden.answers().getFirst().correctOptionIds()).isNull();
    }

    @Test
    void resultRevealsExplanationsWhenAssessmentAllowsIt() {
        QuestionResponse question = createSingleChoiceQuestion();
        Long assessmentId = createAssessment(30, 1, BigDecimal.valueOf(50), true);
        assessmentService.addSelection(
            assessmentId,
            new AssessmentQuestionSelectionRequest(question.id(), 1, BigDecimal.valueOf(4)),
            instructorAuth
        );
        publish(assessmentId);
        enrollmentService.enroll(STUDENT_EMAIL, course.getId());
        AttemptDetailResponse detail = submissionService.startOrResume(STUDENT_EMAIL, assessmentId);

        AttemptResultResponse revealed = submissionService.submit(
            STUDENT_EMAIL,
            detail.attempt().id(),
            new AssessmentSubmitRequest(List.of(new AnswerSubmitRequest(
                question.id(),
                null,
                Set.of(optionId(detail, "extends")),
                null
            )))
        );

        assertThat(revealed.answersRevealed()).isTrue();
        assertThat(revealed.answers().getFirst().explanation()).contains("extends");
        assertThat(revealed.answers().getFirst().correctOptionIds()).isNotEmpty();
    }

    @Test
    void draftRejectsQuestionOutsideTheAssessment() {
        Long assessmentId = publishedAssessmentWithSingleChoice();
        enrollmentService.enroll(STUDENT_EMAIL, course.getId());
        AttemptDetailResponse detail = submissionService.startOrResume(STUDENT_EMAIL, assessmentId);

        assertThatThrownBy(() -> submissionService.saveDraft(
            STUDENT_EMAIL,
            detail.attempt().id(),
            new AssessmentSubmitRequest(List.of(new AnswerSubmitRequest(999_999L, "x", Set.of(), null)))
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not part of the assessment");
    }

    @Test
    void studentCannotOpenSomeoneElsesAttempt() {
        Long assessmentId = publishedAssessmentWithSingleChoice();
        enrollmentService.enroll(STUDENT_EMAIL, course.getId());
        AttemptDetailResponse detail = submissionService.startOrResume(STUDENT_EMAIL, assessmentId);

        User other = new User();
        other.setEmail("other@example.com");
        other.setFullName("Other");
        other.setPasswordHash("hash");
        userRepository.save(other);

        assertThatThrownBy(() -> submissionService.getAttempt("other@example.com", detail.attempt().id()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not found");
    }

    private Long publishedAssessmentWithSingleChoice() {
        QuestionResponse question = createSingleChoiceQuestion();
        return publishAssessmentWith(question, BigDecimal.valueOf(4), BigDecimal.valueOf(50));
    }

    private QuestionResponse createSingleChoiceQuestion() {
        return questionBankService.createQuestion(
            course.getId(),
            new QuestionRequest(
                null,
                "Which keyword creates a subclass?",
                QuestionType.SINGLE_CHOICE,
                QuestionDifficulty.EASY,
                BigDecimal.valueOf(2),
                null,
                "Use extends for class inheritance.",
                List.of(
                    new QuestionOptionRequest("extends", true, 1),
                    new QuestionOptionRequest("implements", false, 2)
                )
            ),
            instructorAuth
        );
    }

    private Long publishAssessmentWith(QuestionResponse question, BigDecimal points, BigDecimal passingScore) {
        Long assessmentId = createAssessment(30, 1, passingScore, false);
        assessmentService.addSelection(
            assessmentId,
            new AssessmentQuestionSelectionRequest(question.id(), 1, points),
            instructorAuth
        );
        publish(assessmentId);
        return assessmentId;
    }

    private Long createAssessment(Integer timeLimit, Integer maxAttempts, BigDecimal passingScore, boolean showAnswers) {
        return assessmentService.create(
            course.getId(),
            new AssessmentCreateRequest(
                "Java Quiz",
                null,
                AssessmentType.QUIZ,
                null,
                AssessmentCompositionMode.FIXED,
                null,
                null,
                timeLimit,
                maxAttempts,
                passingScore,
                false,
                false,
                showAnswers
            ),
            instructorAuth
        ).id();
    }

    private void publish(Long assessmentId) {
        assessmentService.update(
            assessmentId,
            new AssessmentUpdateRequest(
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                AssessmentStatus.PUBLISHED
            ),
            instructorAuth
        );
    }

    private Long optionId(AttemptDetailResponse detail, String optionText) {
        return detail.questions().stream()
            .flatMap(question -> question.options().stream())
            .filter(option -> option.optionText().equals(optionText))
            .findFirst()
            .orElseThrow()
            .id();
    }

    private void backdateAttempt(Long attemptId, int minutesAgo) {
        AssessmentAttempt attempt = attemptRepository.findById(attemptId).orElseThrow();
        attempt.setStartedAt(LocalDateTime.now().minusMinutes(minutesAgo));
        attemptRepository.saveAndFlush(attempt);
    }
}
