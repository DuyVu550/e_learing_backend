package com.example.learning_backend.notification;

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
import com.example.learning_backend.notification.dto.NotificationResponse;
import com.example.learning_backend.notification.enums.NotificationType;
import com.example.learning_backend.notification.service.NotificationService;
import com.example.learning_backend.submission.dto.AnswerSubmitRequest;
import com.example.learning_backend.submission.dto.AssessmentSubmitRequest;
import com.example.learning_backend.submission.dto.AttemptDetailResponse;
import com.example.learning_backend.submission.dto.ManualGradeRequest;
import com.example.learning_backend.submission.service.SubmissionService;
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
class NotificationServiceTests {

    private static final String STUDENT_EMAIL = "student@example.com";
    private static final String TEACHER_EMAIL = "teacher@example.com";

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AssessmentService assessmentService;

    @Autowired
    private QuestionBankService questionBankService;

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    private Course course;
    private User student;
    private Authentication instructorAuth;

    @BeforeEach
    void setUp() {
        User instructor = saveUser(TEACHER_EMAIL, "Teacher");
        student = saveUser(STUDENT_EMAIL, "Student");

        course = new Course();
        course.setSlug("notify-course");
        course.setTitle("Notify Course");
        course.setInstructor(instructor);
        course = courseRepository.save(course);

        enroll(student);
        instructorAuth = auth(TEACHER_EMAIL, "ROLE_INSTRUCTOR");
    }

    @Test
    void publishingAnAssessmentNotifiesEveryEnrolledStudent() {
        User second = saveUser("second@example.com", "Second");
        enroll(second);
        User cancelled = saveUser("cancelled@example.com", "Cancelled");
        enrollAs(cancelled, EnrollmentStatus.CANCELLED);

        publishAssessment();

        assertThat(notificationService.myNotifications(STUDENT_EMAIL, false))
            .singleElement()
            .satisfies(notification -> assertThat(notification.type()).isEqualTo(NotificationType.ASSESSMENT_PUBLISHED));
        assertThat(notificationService.myNotifications(second.getEmail(), false)).hasSize(1);
        assertThat(notificationService.myNotifications(cancelled.getEmail(), false)).isEmpty();
        assertThat(notificationService.myNotifications(TEACHER_EMAIL, false)).isEmpty();
    }

    @Test
    void gradingAnEssayNotifiesTheStudentAndNotTheGrader() {
        Long assessmentId = publishEssayAssessment();
        AttemptDetailResponse detail = submissionService.startOrResume(STUDENT_EMAIL, assessmentId);
        Long questionId = detail.questions().getFirst().questionId();
        submissionService.submit(
            STUDENT_EMAIL,
            detail.attempt().id(),
            new AssessmentSubmitRequest(List.of(new AnswerSubmitRequest(questionId, "Bài làm của em", null, null)))
        );
        notificationService.markAllRead(STUDENT_EMAIL);

        Long answerId = submissionService.getResult(STUDENT_EMAIL, detail.attempt().id())
            .answers().getFirst().answerId();
        submissionService.gradeAnswer(answerId, new ManualGradeRequest(BigDecimal.valueOf(4), "Tốt"), instructorAuth);

        List<NotificationResponse> unread = notificationService.myNotifications(STUDENT_EMAIL, true);
        assertThat(unread).hasSize(1);
        assertThat(unread.getFirst().type()).isEqualTo(NotificationType.ANSWER_GRADED);
        assertThat(unread.getFirst().referenceId()).isEqualTo(detail.attempt().id());
        assertThat(notificationService.myNotifications(TEACHER_EMAIL, true)).isEmpty();
    }

    @Test
    void unreadCountDropsAsNotificationsAreRead() {
        publishAssessment();
        assertThat(notificationService.unreadCount(STUDENT_EMAIL)).isEqualTo(1);

        Long id = notificationService.myNotifications(STUDENT_EMAIL, false).getFirst().id();
        NotificationResponse read = notificationService.markRead(STUDENT_EMAIL, id);

        assertThat(read.read()).isTrue();
        assertThat(read.readAt()).isNotNull();
        assertThat(notificationService.unreadCount(STUDENT_EMAIL)).isZero();
    }

    @Test
    void markAllReadClearsEverythingAndIsIdempotent() {
        publishAssessment();

        assertThat(notificationService.markAllRead(STUDENT_EMAIL)).isEqualTo(1);
        assertThat(notificationService.markAllRead(STUDENT_EMAIL)).isZero();
        assertThat(notificationService.unreadCount(STUDENT_EMAIL)).isZero();
    }

    @Test
    void aUserCannotReadSomeoneElsesNotification() {
        publishAssessment();
        Long id = notificationService.myNotifications(STUDENT_EMAIL, false).getFirst().id();

        assertThatThrownBy(() -> notificationService.markRead(TEACHER_EMAIL, id))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Notification not found");
    }

    @Test
    void anAlreadyPublishedAssessmentCannotBeRepublishedSoStudentsAreNotNotifiedTwice() {
        Long assessmentId = publishAssessment();
        assertThat(notificationService.unreadCount(STUDENT_EMAIL)).isEqualTo(1);

        // Published assessments are immutable, which is what stops a duplicate announcement.
        assertThatThrownBy(() -> assessmentService
            .update(assessmentId, statusUpdate(AssessmentStatus.PUBLISHED), instructorAuth))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Only draft assessments can be edited");
        assertThat(notificationService.unreadCount(STUDENT_EMAIL)).isEqualTo(1);
    }

    private Long publishAssessment() {
        QuestionResponse question = questionBankService.createQuestion(
            course.getId(),
            new QuestionRequest(
                null,
                "Which keyword creates a subclass?",
                QuestionType.SINGLE_CHOICE,
                QuestionDifficulty.EASY,
                BigDecimal.valueOf(2),
                null,
                null,
                List.of(
                    new QuestionOptionRequest("extends", true, 1),
                    new QuestionOptionRequest("implements", false, 2)
                )
            ),
            instructorAuth
        );
        return publishWith(question);
    }

    private Long publishEssayAssessment() {
        QuestionResponse question = questionBankService.createQuestion(
            course.getId(),
            new QuestionRequest(
                null,
                "Giải thích tính kế thừa.",
                QuestionType.SHORT_ANSWER,
                QuestionDifficulty.MEDIUM,
                BigDecimal.valueOf(4),
                null,
                null,
                List.of()
            ),
            instructorAuth
        );
        return publishWith(question);
    }

    private Long publishWith(QuestionResponse question) {
        Long assessmentId = assessmentService.create(
            course.getId(),
            new AssessmentCreateRequest(
                "Java Quiz",
                null,
                AssessmentType.QUIZ,
                null,
                AssessmentCompositionMode.FIXED,
                null,
                null,
                30,
                1,
                BigDecimal.valueOf(50),
                false,
                false,
                false
            ),
            instructorAuth
        ).id();
        assessmentService.addSelection(
            assessmentId,
            new AssessmentQuestionSelectionRequest(question.id(), 1, BigDecimal.valueOf(4)),
            instructorAuth
        );
        assessmentService.update(assessmentId, statusUpdate(AssessmentStatus.PUBLISHED), instructorAuth);
        return assessmentId;
    }

    private AssessmentUpdateRequest statusUpdate(AssessmentStatus status) {
        return new AssessmentUpdateRequest(
            null, null, null, null, null, null, null, null, null, null, null, null, null, status
        );
    }

    private void enroll(User user) {
        enrollAs(user, EnrollmentStatus.ACTIVE);
    }

    private void enrollAs(User user, EnrollmentStatus status) {
        Enrollment enrollment = new Enrollment();
        enrollment.setUser(user);
        enrollment.setCourse(course);
        enrollment.setStatus(status);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollmentRepository.save(enrollment);
    }

    private User saveUser(String email, String fullName) {
        User user = new User();
        user.setEmail(email);
        user.setFullName(fullName);
        user.setPasswordHash("hash");
        return userRepository.save(user);
    }

    private Authentication auth(String email, String role) {
        return new UsernamePasswordAuthenticationToken(email, null, List.of(new SimpleGrantedAuthority(role)));
    }
}
