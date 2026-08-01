package com.example.learning_backend.course.service;

import com.example.learning_backend.course.dto.CourseCreateRequest;
import com.example.learning_backend.course.dto.CourseDetailResponse;
import com.example.learning_backend.course.dto.CourseResponse;
import com.example.learning_backend.course.dto.CourseSectionCreateRequest;
import com.example.learning_backend.course.dto.CourseSectionResponse;
import com.example.learning_backend.course.dto.LessonCreateRequest;
import com.example.learning_backend.course.dto.LessonResponse;
import com.example.learning_backend.course.entity.Course;
import com.example.learning_backend.course.entity.CourseSection;
import com.example.learning_backend.course.entity.Lesson;
import com.example.learning_backend.course.enums.LessonContentType;
import com.example.learning_backend.course.repository.CourseRepository;
import com.example.learning_backend.course.repository.CourseSectionRepository;
import com.example.learning_backend.course.repository.LessonRepository;
import com.example.learning_backend.user.entity.User;
import com.example.learning_backend.user.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseSectionRepository courseSectionRepository;
    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;

    public CourseService(
        CourseRepository courseRepository,
        CourseSectionRepository courseSectionRepository,
        LessonRepository lessonRepository,
        UserRepository userRepository
    ) {
        this.courseRepository = courseRepository;
        this.courseSectionRepository = courseSectionRepository;
        this.lessonRepository = lessonRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> findAll() {
        return courseRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CourseResponse findById(Long id) {
        return courseRepository.findById(id).map(this::toResponse)
            .orElseThrow(() -> new IllegalArgumentException("Course not found: " + id));
    }

    @Transactional(readOnly = true)
    public CourseDetailResponse findDetail(Long id) {
        Course course = courseRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Course not found: " + id));
        return toDetailResponse(course);
    }

    @Transactional(readOnly = true)
    public List<CourseSectionResponse> findSections(Long courseId) {
        requireCourse(courseId);
        return courseSectionRepository.findByCourseIdOrderByPositionAsc(courseId)
            .stream()
            .map(this::toSectionResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<LessonResponse> findLessons(Long sectionId) {
        requireSection(sectionId);
        return lessonRepository.findBySectionIdOrderByPositionAsc(sectionId)
            .stream()
            .map(this::toLessonResponse)
            .toList();
    }

    public CourseResponse create(CourseCreateRequest request) {
        User instructor = userRepository.findById(request.instructorId())
            .orElseThrow(() -> new IllegalArgumentException("Instructor not found: " + request.instructorId()));

        Course course = new Course();
        course.setSlug(request.slug());
        course.setTitle(request.title());
        course.setDescription(request.description());
        course.setLevel(request.level());
        course.setInstructor(instructor);
        return toResponse(courseRepository.save(course));
    }

    public CourseSectionResponse createSection(Long courseId, CourseSectionCreateRequest request) {
        Course course = requireCourse(courseId);

        CourseSection section = new CourseSection();
        section.setCourse(course);
        section.setTitle(request.title());
        section.setPosition(request.position());
        return toSectionResponse(courseSectionRepository.save(section));
    }

    public LessonResponse createLesson(Long sectionId, LessonCreateRequest request) {
        CourseSection section = requireSection(sectionId);
        validateLessonContent(request);

        Lesson lesson = new Lesson();
        lesson.setSection(section);
        lesson.setTitle(request.title());
        lesson.setContentType(request.contentType());
        lesson.setContent(request.content());
        lesson.setVideoUrl(request.videoUrl());
        lesson.setDocumentUrl(request.documentUrl());
        lesson.setDurationSeconds(request.durationSeconds());
        lesson.setPosition(request.position());
        lesson.setPreview(Boolean.TRUE.equals(request.preview()));
        return toLessonResponse(lessonRepository.save(lesson));
    }

    private Course requireCourse(Long id) {
        return courseRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Course not found: " + id));
    }

    private CourseSection requireSection(Long id) {
        return courseSectionRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Course section not found: " + id));
    }

    private void validateLessonContent(LessonCreateRequest request) {
        LessonContentType contentType = request.contentType();
        if (contentType == LessonContentType.VIDEO && !hasText(request.videoUrl())) {
            throw new IllegalArgumentException("Video lesson requires videoUrl");
        }
        if (contentType == LessonContentType.PDF && !hasText(request.documentUrl())) {
            throw new IllegalArgumentException("PDF lesson requires documentUrl");
        }
        if ((contentType == LessonContentType.TEXT || contentType == LessonContentType.MARKDOWN) && !hasText(request.content())) {
            throw new IllegalArgumentException(contentType + " lesson requires content");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private CourseResponse toResponse(Course course) {
        return new CourseResponse(
            course.getId(),
            course.getSlug(),
            course.getTitle(),
            course.getDescription(),
            course.getLevel(),
            course.getStatus(),
            course.getInstructor() != null ? course.getInstructor().getId() : null
        );
    }

    private CourseDetailResponse toDetailResponse(Course course) {
        List<CourseSectionResponse> sections = courseSectionRepository.findByCourseIdOrderByPositionAsc(course.getId())
            .stream()
            .map(this::toSectionResponse)
            .toList();
        return new CourseDetailResponse(
            course.getId(),
            course.getSlug(),
            course.getTitle(),
            course.getDescription(),
            course.getLevel(),
            course.getStatus(),
            course.getInstructor() != null ? course.getInstructor().getId() : null,
            sections
        );
    }

    private CourseSectionResponse toSectionResponse(CourseSection section) {
        List<LessonResponse> lessons = lessonRepository.findBySectionIdOrderByPositionAsc(section.getId())
            .stream()
            .map(this::toLessonResponse)
            .toList();
        return new CourseSectionResponse(
            section.getId(),
            section.getCourse() != null ? section.getCourse().getId() : null,
            section.getTitle(),
            section.getPosition(),
            lessons
        );
    }

    private LessonResponse toLessonResponse(Lesson lesson) {
        return new LessonResponse(
            lesson.getId(),
            lesson.getSection() != null ? lesson.getSection().getId() : null,
            lesson.getTitle(),
            lesson.getContentType(),
            lesson.getContent(),
            lesson.getVideoUrl(),
            lesson.getDocumentUrl(),
            lesson.getDurationSeconds(),
            lesson.getPosition(),
            lesson.getPreview()
        );
    }
}
