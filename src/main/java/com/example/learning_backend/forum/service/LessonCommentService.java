package com.example.learning_backend.forum.service;

import com.example.learning_backend.course.entity.Course;
import com.example.learning_backend.course.entity.Lesson;
import com.example.learning_backend.course.repository.LessonRepository;
import com.example.learning_backend.course.service.CourseAccessPolicy;
import com.example.learning_backend.enrollment.service.EnrollmentAccessPolicy;
import com.example.learning_backend.forum.dto.LessonCommentRequest;
import com.example.learning_backend.forum.dto.LessonCommentResponse;
import com.example.learning_backend.forum.dto.LessonCommentUpdateRequest;
import com.example.learning_backend.forum.entity.LessonComment;
import com.example.learning_backend.forum.repository.LessonCommentRepository;
import com.example.learning_backend.notification.enums.NotificationType;
import com.example.learning_backend.notification.service.NotificationService;
import com.example.learning_backend.user.entity.User;
import com.example.learning_backend.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Q&A under a lesson. Threading is deliberately one level deep: a reply cannot itself be replied
 * to, which keeps listing to a single query and avoids recursive SQL.
 */
@Service
@Transactional
public class LessonCommentService {

    private final LessonCommentRepository commentRepository;
    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;
    private final CourseAccessPolicy courseAccessPolicy;
    private final EnrollmentAccessPolicy enrollmentAccessPolicy;
    private final NotificationService notificationService;

    public LessonCommentService(
        LessonCommentRepository commentRepository,
        LessonRepository lessonRepository,
        UserRepository userRepository,
        CourseAccessPolicy courseAccessPolicy,
        EnrollmentAccessPolicy enrollmentAccessPolicy,
        NotificationService notificationService
    ) {
        this.commentRepository = commentRepository;
        this.lessonRepository = lessonRepository;
        this.userRepository = userRepository;
        this.courseAccessPolicy = courseAccessPolicy;
        this.enrollmentAccessPolicy = enrollmentAccessPolicy;
        this.notificationService = notificationService;
    }

    public LessonCommentResponse create(Long lessonId, LessonCommentRequest request, Authentication authentication) {
        Lesson lesson = requireLesson(lessonId);
        Course course = lesson.getSection().getCourse();
        User author = requireUser(authentication.getName());
        requireCourseMember(course, author, authentication);

        LessonComment comment = new LessonComment();
        comment.setLesson(lesson);
        comment.setUser(author);
        comment.setContent(request.content());

        LessonComment parent = null;
        if (request.parentId() != null) {
            parent = requireComment(request.parentId());
            if (!parent.getLesson().getId().equals(lessonId)) {
                throw new IllegalArgumentException("Parent comment belongs to another lesson: " + request.parentId());
            }
            if (parent.getParent() != null) {
                throw new IllegalArgumentException("Replies cannot be nested further than one level");
            }
            comment.setParent(parent);
        }

        LessonComment saved = commentRepository.save(comment);
        notifyAboutNewComment(saved, parent, course, author, lesson);
        return toResponse(saved, List.of());
    }

    @Transactional(readOnly = true)
    public List<LessonCommentResponse> findByLesson(Long lessonId, Authentication authentication) {
        Lesson lesson = requireLesson(lessonId);
        User viewer = requireUser(authentication.getName());
        requireCourseMember(lesson.getSection().getCourse(), viewer, authentication);

        Map<Long, List<LessonCommentResponse>> repliesByParent = new LinkedHashMap<>();
        List<LessonComment> roots = new ArrayList<>();
        for (LessonComment comment : commentRepository.findByLessonIdOrderByCreatedAtAsc(lessonId)) {
            if (comment.getParent() == null) {
                roots.add(comment);
            } else {
                repliesByParent
                    .computeIfAbsent(comment.getParent().getId(), key -> new ArrayList<>())
                    .add(toResponse(comment, List.of()));
            }
        }
        return roots.stream()
            .map(root -> toResponse(root, repliesByParent.getOrDefault(root.getId(), List.of())))
            .toList();
    }

    public LessonCommentResponse update(Long commentId, LessonCommentUpdateRequest request, Authentication authentication) {
        LessonComment comment = requireComment(commentId);
        User actor = requireUser(authentication.getName());
        if (!comment.getUser().getId().equals(actor.getId())) {
            throw new IllegalArgumentException("You can only edit your own comment");
        }
        comment.setContent(request.content());
        return toResponse(comment, List.of());
    }

    public void delete(Long commentId, Authentication authentication) {
        LessonComment comment = requireComment(commentId);
        User actor = requireUser(authentication.getName());
        Course course = comment.getLesson().getSection().getCourse();
        boolean author = comment.getUser().getId().equals(actor.getId());
        if (!author && !courseAccessPolicy.canManage(course, authentication)) {
            throw new IllegalArgumentException("You can only delete your own comment");
        }
        // Replies would otherwise orphan against fk_lesson_comments_parent.
        commentRepository.deleteAll(commentRepository.findByParentIdOrderByCreatedAtAsc(commentId));
        commentRepository.delete(comment);
    }

    private void notifyAboutNewComment(LessonComment comment, LessonComment parent, Course course, User author, Lesson lesson) {
        if (parent != null) {
            notificationService.notifyOthers(
                parent.getUser(),
                author,
                NotificationType.COMMENT_REPLY,
                "Có phản hồi cho bình luận của bạn",
                author.getFullName() + " đã trả lời bình luận của bạn ở bài học: " + lesson.getTitle(),
                comment.getId()
            );
            return;
        }
        notificationService.notifyOthers(
            course.getInstructor(),
            author,
            NotificationType.LESSON_QUESTION,
            "Câu hỏi mới trong khóa học",
            author.getFullName() + " đã đặt câu hỏi ở bài học: " + lesson.getTitle(),
            comment.getId()
        );
    }

    /** Course managers may always take part; everyone else must hold an active enrollment. */
    private void requireCourseMember(Course course, User user, Authentication authentication) {
        if (courseAccessPolicy.canManage(course, authentication)) {
            return;
        }
        enrollmentAccessPolicy.requireActive(user.getId(), course.getId());
    }

    private Lesson requireLesson(Long lessonId) {
        return lessonRepository.findById(lessonId)
            .orElseThrow(() -> new IllegalArgumentException("Lesson not found: " + lessonId));
    }

    private LessonComment requireComment(Long commentId) {
        return commentRepository.findById(commentId)
            .orElseThrow(() -> new IllegalArgumentException("Comment not found: " + commentId));
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
    }

    private LessonCommentResponse toResponse(LessonComment comment, List<LessonCommentResponse> replies) {
        return new LessonCommentResponse(
            comment.getId(),
            comment.getLesson().getId(),
            comment.getParent() == null ? null : comment.getParent().getId(),
            comment.getUser().getId(),
            comment.getUser().getFullName(),
            comment.getContent(),
            comment.getCreatedAt(),
            comment.getUpdatedAt(),
            replies
        );
    }
}
