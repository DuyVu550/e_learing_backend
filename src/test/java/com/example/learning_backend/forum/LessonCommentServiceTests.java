package com.example.learning_backend.forum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.learning_backend.course.entity.Course;
import com.example.learning_backend.course.entity.CourseSection;
import com.example.learning_backend.course.entity.Lesson;
import com.example.learning_backend.course.repository.CourseRepository;
import com.example.learning_backend.course.repository.CourseSectionRepository;
import com.example.learning_backend.course.repository.LessonRepository;
import com.example.learning_backend.enrollment.entity.Enrollment;
import com.example.learning_backend.enrollment.enums.EnrollmentStatus;
import com.example.learning_backend.enrollment.repository.EnrollmentRepository;
import com.example.learning_backend.forum.dto.LessonCommentRequest;
import com.example.learning_backend.forum.dto.LessonCommentResponse;
import com.example.learning_backend.forum.dto.LessonCommentUpdateRequest;
import com.example.learning_backend.forum.repository.LessonCommentRepository;
import com.example.learning_backend.forum.service.LessonCommentService;
import com.example.learning_backend.notification.dto.NotificationResponse;
import com.example.learning_backend.notification.enums.NotificationType;
import com.example.learning_backend.notification.service.NotificationService;
import com.example.learning_backend.user.entity.User;
import com.example.learning_backend.user.repository.UserRepository;
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
class LessonCommentServiceTests {

    @Autowired
    private LessonCommentService lessonCommentService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private LessonCommentRepository commentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseSectionRepository sectionRepository;

    @Autowired
    private LessonRepository lessonRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    private Course course;
    private Lesson lesson;
    private User instructor;
    private User student;
    private Authentication instructorAuth;
    private Authentication studentAuth;

    @BeforeEach
    void setUp() {
        instructor = saveUser("teacher@example.com", "Teacher");
        student = saveUser("student@example.com", "Student");

        course = new Course();
        course.setSlug("forum-course");
        course.setTitle("Forum Course");
        course.setInstructor(instructor);
        course = courseRepository.save(course);

        CourseSection section = new CourseSection();
        section.setCourse(course);
        section.setTitle("Section 1");
        section.setPosition(1);
        section = sectionRepository.save(section);

        lesson = new Lesson();
        lesson.setSection(section);
        lesson.setTitle("Inheritance");
        lesson.setPosition(1);
        lesson = lessonRepository.save(lesson);

        enroll(student);
        instructorAuth = auth(instructor.getEmail(), "ROLE_INSTRUCTOR");
        studentAuth = auth(student.getEmail(), "ROLE_STUDENT");
    }

    @Test
    void studentCanAskAndInstructorReplyIsNestedUnderTheQuestion() {
        LessonCommentResponse question = lessonCommentService
            .create(lesson.getId(), new LessonCommentRequest("Em chưa hiểu phần này", null), studentAuth);
        lessonCommentService
            .create(lesson.getId(), new LessonCommentRequest("Em xem lại ví dụ nhé", question.id()), instructorAuth);

        List<LessonCommentResponse> thread = lessonCommentService.findByLesson(lesson.getId(), studentAuth);

        assertThat(thread).hasSize(1);
        assertThat(thread.getFirst().content()).isEqualTo("Em chưa hiểu phần này");
        assertThat(thread.getFirst().replies()).hasSize(1);
        assertThat(thread.getFirst().replies().getFirst().authorName()).isEqualTo("Teacher");
    }

    @Test
    void replyingToAReplyIsRejected() {
        LessonCommentResponse question = lessonCommentService
            .create(lesson.getId(), new LessonCommentRequest("Câu hỏi", null), studentAuth);
        LessonCommentResponse reply = lessonCommentService
            .create(lesson.getId(), new LessonCommentRequest("Trả lời", question.id()), instructorAuth);

        assertThatThrownBy(() -> lessonCommentService
            .create(lesson.getId(), new LessonCommentRequest("Trả lời lồng nhau", reply.id()), studentAuth))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("one level");
    }

    @Test
    void deletingAQuestionAlsoRemovesItsReplies() {
        LessonCommentResponse question = lessonCommentService
            .create(lesson.getId(), new LessonCommentRequest("Câu hỏi", null), studentAuth);
        lessonCommentService
            .create(lesson.getId(), new LessonCommentRequest("Trả lời", question.id()), instructorAuth);

        lessonCommentService.delete(question.id(), studentAuth);

        assertThat(commentRepository.findByLessonIdOrderByCreatedAtAsc(lesson.getId())).isEmpty();
    }

    @Test
    void onlyTheAuthorCanEditAComment() {
        LessonCommentResponse question = lessonCommentService
            .create(lesson.getId(), new LessonCommentRequest("Câu hỏi", null), studentAuth);

        assertThatThrownBy(() -> lessonCommentService
            .update(question.id(), new LessonCommentUpdateRequest("Sửa trộm"), instructorAuth))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("your own comment");
    }

    @Test
    void courseManagerCanDeleteSomeoneElsesComment() {
        LessonCommentResponse question = lessonCommentService
            .create(lesson.getId(), new LessonCommentRequest("Nội dung không phù hợp", null), studentAuth);

        lessonCommentService.delete(question.id(), instructorAuth);

        assertThat(commentRepository.findById(question.id())).isEmpty();
    }

    @Test
    void studentWhoIsNotEnrolledCannotCommentOrRead() {
        User outsider = saveUser("outsider@example.com", "Outsider");
        Authentication outsiderAuth = auth(outsider.getEmail(), "ROLE_STUDENT");

        assertThatThrownBy(() -> lessonCommentService
            .create(lesson.getId(), new LessonCommentRequest("Cho em hỏi", null), outsiderAuth))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not enrolled");
        assertThatThrownBy(() -> lessonCommentService.findByLesson(lesson.getId(), outsiderAuth))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not enrolled");
    }

    @Test
    void topLevelQuestionNotifiesTheInstructorOnly() {
        lessonCommentService.create(lesson.getId(), new LessonCommentRequest("Câu hỏi", null), studentAuth);

        List<NotificationResponse> forInstructor = notificationService.myNotifications(instructor.getEmail(), false);
        assertThat(forInstructor).hasSize(1);
        assertThat(forInstructor.getFirst().type()).isEqualTo(NotificationType.LESSON_QUESTION);
        assertThat(notificationService.myNotifications(student.getEmail(), false)).isEmpty();
    }

    @Test
    void replyNotifiesTheQuestionAuthorAndNotTheReplier() {
        LessonCommentResponse question = lessonCommentService
            .create(lesson.getId(), new LessonCommentRequest("Câu hỏi", null), studentAuth);
        notificationService.markAllRead(instructor.getEmail());

        lessonCommentService
            .create(lesson.getId(), new LessonCommentRequest("Trả lời", question.id()), instructorAuth);

        List<NotificationResponse> forStudent = notificationService.myNotifications(student.getEmail(), true);
        assertThat(forStudent).hasSize(1);
        assertThat(forStudent.getFirst().type()).isEqualTo(NotificationType.COMMENT_REPLY);
        assertThat(notificationService.myNotifications(instructor.getEmail(), true)).isEmpty();
    }

    @Test
    void instructorAnsweringTheirOwnQuestionNotifiesNobody() {
        LessonCommentResponse own = lessonCommentService
            .create(lesson.getId(), new LessonCommentRequest("Lưu ý cho cả lớp", null), instructorAuth);
        lessonCommentService
            .create(lesson.getId(), new LessonCommentRequest("Bổ sung thêm", own.id()), instructorAuth);

        assertThat(notificationService.myNotifications(instructor.getEmail(), false)).isEmpty();
    }

    private void enroll(User user) {
        Enrollment enrollment = new Enrollment();
        enrollment.setUser(user);
        enrollment.setCourse(course);
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
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
