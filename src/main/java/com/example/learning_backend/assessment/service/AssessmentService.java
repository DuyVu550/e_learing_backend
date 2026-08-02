package com.example.learning_backend.assessment.service;

import com.example.learning_backend.assessment.dto.AssessmentCreateRequest;
import com.example.learning_backend.assessment.dto.AssessmentQuestionRuleRequest;
import com.example.learning_backend.assessment.dto.AssessmentQuestionRuleResponse;
import com.example.learning_backend.assessment.dto.AssessmentQuestionRuleUpdateRequest;
import com.example.learning_backend.assessment.dto.AssessmentQuestionSelectionRequest;
import com.example.learning_backend.assessment.dto.AssessmentQuestionSelectionResponse;
import com.example.learning_backend.assessment.dto.AssessmentQuestionSelectionUpdateRequest;
import com.example.learning_backend.assessment.dto.AssessmentResponse;
import com.example.learning_backend.assessment.dto.AssessmentUpdateRequest;
import com.example.learning_backend.assessment.entity.Assessment;
import com.example.learning_backend.assessment.entity.AssessmentQuestionRule;
import com.example.learning_backend.assessment.entity.AssessmentQuestionSelection;
import com.example.learning_backend.assessment.entity.Question;
import com.example.learning_backend.assessment.entity.QuestionTopic;
import com.example.learning_backend.assessment.enums.AssessmentCompositionMode;
import com.example.learning_backend.assessment.enums.AssessmentStatus;
import com.example.learning_backend.assessment.repository.AssessmentQuestionRuleRepository;
import com.example.learning_backend.assessment.repository.AssessmentQuestionSelectionRepository;
import com.example.learning_backend.assessment.repository.AssessmentRepository;
import com.example.learning_backend.assessment.repository.QuestionRepository;
import com.example.learning_backend.assessment.repository.QuestionTopicRepository;
import com.example.learning_backend.course.entity.Course;
import com.example.learning_backend.course.entity.Lesson;
import com.example.learning_backend.course.repository.CourseRepository;
import com.example.learning_backend.course.repository.LessonRepository;
import com.example.learning_backend.course.service.CourseAccessPolicy;
import com.example.learning_backend.enrollment.entity.Enrollment;
import com.example.learning_backend.enrollment.enums.EnrollmentStatus;
import com.example.learning_backend.enrollment.repository.EnrollmentRepository;
import com.example.learning_backend.notification.enums.NotificationType;
import com.example.learning_backend.notification.service.NotificationService;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final QuestionRepository questionRepository;
    private final QuestionTopicRepository questionTopicRepository;
    private final AssessmentQuestionSelectionRepository selectionRepository;
    private final AssessmentQuestionRuleRepository ruleRepository;
    private final QuestionBankService questionBankService;
    private final CourseAccessPolicy courseAccessPolicy;
    private final EnrollmentRepository enrollmentRepository;
    private final NotificationService notificationService;

    public AssessmentService(
        AssessmentRepository assessmentRepository,
        CourseRepository courseRepository,
        LessonRepository lessonRepository,
        QuestionRepository questionRepository,
        QuestionTopicRepository questionTopicRepository,
        AssessmentQuestionSelectionRepository selectionRepository,
        AssessmentQuestionRuleRepository ruleRepository,
        QuestionBankService questionBankService,
        CourseAccessPolicy courseAccessPolicy,
        EnrollmentRepository enrollmentRepository,
        NotificationService notificationService
    ) {
        this.assessmentRepository = assessmentRepository;
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
        this.questionRepository = questionRepository;
        this.questionTopicRepository = questionTopicRepository;
        this.selectionRepository = selectionRepository;
        this.ruleRepository = ruleRepository;
        this.questionBankService = questionBankService;
        this.courseAccessPolicy = courseAccessPolicy;
        this.enrollmentRepository = enrollmentRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<AssessmentResponse> findByCourse(Long courseId) {
        return assessmentRepository.findByCourseId(courseId, Pageable.unpaged())
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public AssessmentResponse findById(Long id) {
        return assessmentRepository.findById(id).map(this::toResponse)
            .orElseThrow(() -> new IllegalArgumentException("Assessment not found: " + id));
    }

    public AssessmentResponse create(Long courseId, AssessmentCreateRequest request, Authentication authentication) {
        Course course = requireCourse(courseId);
        ensureCanManage(course, authentication);
        Lesson lesson = resolveLesson(courseId, request.lessonId());
        validateSettings(request.availableFrom(), request.availableUntil(), request.timeLimitMinutes(), request.maxAttempts(), request.passingScore());

        Assessment assessment = new Assessment();
        assessment.setCourse(course);
        assessment.setLesson(lesson);
        assessment.setTitle(request.title());
        assessment.setDescription(request.description());
        assessment.setType(request.type());
        assessment.setCompositionMode(request.compositionMode() == null ? AssessmentCompositionMode.FIXED : request.compositionMode());
        assessment.setAvailableFrom(request.availableFrom());
        assessment.setAvailableUntil(request.availableUntil());
        assessment.setTimeLimitMinutes(request.timeLimitMinutes());
        assessment.setMaxAttempts(request.maxAttempts());
        assessment.setPassingScore(request.passingScore());
        assessment.setShuffleQuestions(Boolean.TRUE.equals(request.shuffleQuestions()));
        assessment.setShuffleOptions(Boolean.TRUE.equals(request.shuffleOptions()));
        assessment.setShowAnswersAfterSubmit(Boolean.TRUE.equals(request.showAnswersAfterSubmit()));
        return toResponse(assessmentRepository.save(assessment));
    }

    public AssessmentResponse update(Long assessmentId, AssessmentUpdateRequest request, Authentication authentication) {
        Assessment assessment = requireAssessment(assessmentId);
        ensureCanManage(assessment.getCourse(), authentication);
        if (assessment.getStatus() == AssessmentStatus.ARCHIVED) {
            throw new IllegalArgumentException("Archived assessments are read only");
        }
        AssessmentStatus nextStatus = request.status();
        if (assessment.getStatus() != AssessmentStatus.DRAFT) {
            throw new IllegalArgumentException("Only draft assessments can be edited");
        }

        if (request.title() != null) {
            assessment.setTitle(request.title());
        }
        if (request.description() != null) {
            assessment.setDescription(request.description());
        }
        if (request.type() != null) {
            assessment.setType(request.type());
        }
        if (request.lessonId() != null) {
            assessment.setLesson(resolveLesson(assessment.getCourse().getId(), request.lessonId()));
        }
        if (request.compositionMode() != null) {
            assessment.setCompositionMode(request.compositionMode());
        }
        if (request.availableFrom() != null) {
            assessment.setAvailableFrom(request.availableFrom());
        }
        if (request.availableUntil() != null) {
            assessment.setAvailableUntil(request.availableUntil());
        }
        if (request.timeLimitMinutes() != null) {
            assessment.setTimeLimitMinutes(request.timeLimitMinutes());
        }
        if (request.maxAttempts() != null) {
            assessment.setMaxAttempts(request.maxAttempts());
        }
        if (request.passingScore() != null) {
            assessment.setPassingScore(request.passingScore());
        }
        if (request.shuffleQuestions() != null) {
            assessment.setShuffleQuestions(request.shuffleQuestions());
        }
        if (request.shuffleOptions() != null) {
            assessment.setShuffleOptions(request.shuffleOptions());
        }
        if (request.showAnswersAfterSubmit() != null) {
            assessment.setShowAnswersAfterSubmit(request.showAnswersAfterSubmit());
        }
        validateAssessmentSettings(assessment);
        if (nextStatus != null && nextStatus != assessment.getStatus()) {
            applyStatusTransition(assessment, nextStatus);
        }
        return toResponse(assessment);
    }

    @Transactional(readOnly = true)
    public List<AssessmentQuestionSelectionResponse> findSelections(Long assessmentId) {
        return selectionRepository.findByAssessmentIdOrderByPosition(assessmentId).stream()
            .map(this::toSelectionResponse)
            .toList();
    }

    public AssessmentQuestionSelectionResponse addSelection(
        Long assessmentId,
        AssessmentQuestionSelectionRequest request,
        Authentication authentication
    ) {
        Assessment assessment = requireAssessment(assessmentId);
        ensureCanManage(assessment.getCourse(), authentication);
        ensureDraftComposition(assessment, AssessmentCompositionMode.FIXED);
        Question question = requireQuestion(request.questionId());
        ensureQuestionInAssessmentCourse(assessment, question);
        ensureUniqueSelection(assessmentId, request.questionId(), request.position(), null);

        AssessmentQuestionSelection selection = new AssessmentQuestionSelection();
        selection.setAssessment(assessment);
        selection.setQuestion(question);
        selection.setPosition(request.position());
        selection.setPoints(request.points() == null ? question.getPoints() : request.points());
        return toSelectionResponse(selectionRepository.save(selection));
    }

    public AssessmentQuestionSelectionResponse updateSelection(
        Long selectionId,
        AssessmentQuestionSelectionUpdateRequest request,
        Authentication authentication
    ) {
        AssessmentQuestionSelection selection = requireSelection(selectionId);
        Assessment assessment = selection.getAssessment();
        ensureCanManage(assessment.getCourse(), authentication);
        ensureDraftComposition(assessment, AssessmentCompositionMode.FIXED);
        if (request.position() != null) {
            ensureUniqueSelection(assessment.getId(), selection.getQuestion().getId(), request.position(), selection.getId());
            selection.setPosition(request.position());
        }
        if (request.points() != null) {
            selection.setPoints(request.points());
        }
        return toSelectionResponse(selection);
    }

    public void deleteSelection(Long selectionId, Authentication authentication) {
        AssessmentQuestionSelection selection = requireSelection(selectionId);
        ensureCanManage(selection.getAssessment().getCourse(), authentication);
        ensureDraftComposition(selection.getAssessment(), AssessmentCompositionMode.FIXED);
        selectionRepository.delete(selection);
    }

    @Transactional(readOnly = true)
    public List<AssessmentQuestionRuleResponse> findRules(Long assessmentId) {
        return ruleRepository.findByAssessmentIdOrderByPosition(assessmentId).stream()
            .map(this::toRuleResponse)
            .toList();
    }

    public AssessmentQuestionRuleResponse addRule(Long assessmentId, AssessmentQuestionRuleRequest request, Authentication authentication) {
        Assessment assessment = requireAssessment(assessmentId);
        ensureCanManage(assessment.getCourse(), authentication);
        ensureDraftComposition(assessment, AssessmentCompositionMode.RANDOM);
        ensureRulePositionAvailable(assessmentId, request.position(), null);

        AssessmentQuestionRule rule = new AssessmentQuestionRule();
        rule.setAssessment(assessment);
        rule.setTopic(resolveTopic(assessment.getCourse().getId(), request.topicId()));
        rule.setDifficulty(request.difficulty());
        rule.setQuestionType(request.questionType());
        rule.setQuestionCount(request.questionCount());
        rule.setPoints(request.points());
        rule.setPosition(request.position());
        return toRuleResponse(ruleRepository.save(rule));
    }

    public AssessmentQuestionRuleResponse updateRule(
        Long ruleId,
        AssessmentQuestionRuleUpdateRequest request,
        Authentication authentication
    ) {
        AssessmentQuestionRule rule = requireRule(ruleId);
        Assessment assessment = rule.getAssessment();
        ensureCanManage(assessment.getCourse(), authentication);
        ensureDraftComposition(assessment, AssessmentCompositionMode.RANDOM);
        if (request.topicId() != null) {
            rule.setTopic(resolveTopic(assessment.getCourse().getId(), request.topicId()));
        }
        if (request.difficulty() != null) {
            rule.setDifficulty(request.difficulty());
        }
        if (request.questionType() != null) {
            rule.setQuestionType(request.questionType());
        }
        if (request.questionCount() != null) {
            rule.setQuestionCount(request.questionCount());
        }
        if (request.points() != null) {
            rule.setPoints(request.points());
        }
        if (request.position() != null) {
            ensureRulePositionAvailable(assessment.getId(), request.position(), rule.getId());
            rule.setPosition(request.position());
        }
        return toRuleResponse(rule);
    }

    public void deleteRule(Long ruleId, Authentication authentication) {
        AssessmentQuestionRule rule = requireRule(ruleId);
        ensureCanManage(rule.getAssessment().getCourse(), authentication);
        ensureDraftComposition(rule.getAssessment(), AssessmentCompositionMode.RANDOM);
        ruleRepository.delete(rule);
    }

    public List<AssessmentQuestionSelectionResponse> generateSelections(Long assessmentId, Authentication authentication) {
        Assessment assessment = requireAssessment(assessmentId);
        ensureCanManage(assessment.getCourse(), authentication);
        ensureDraftComposition(assessment, AssessmentCompositionMode.RANDOM);
        List<AssessmentQuestionRule> rules = ruleRepository.findByAssessmentIdOrderByPosition(assessmentId);
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("Random assessments need at least one question rule");
        }

        selectionRepository.deleteByAssessmentId(assessmentId);
        Set<Long> selectedQuestionIds = new HashSet<>();
        int position = 1;
        for (AssessmentQuestionRule rule : rules) {
            List<Question> candidates = questionRepository.findByCourseFilters(
                    assessment.getCourse().getId(),
                    rule.getTopic() == null ? null : rule.getTopic().getId(),
                    rule.getDifficulty(),
                    rule.getQuestionType()
                ).stream()
                .filter(question -> selectedQuestionIds.add(question.getId()))
                .limit(rule.getQuestionCount())
                .toList();
            if (candidates.size() < rule.getQuestionCount()) {
                throw new IllegalArgumentException("Not enough questions for random rule: " + rule.getId());
            }
            for (Question question : candidates) {
                AssessmentQuestionSelection selection = new AssessmentQuestionSelection();
                selection.setAssessment(assessment);
                selection.setQuestion(question);
                selection.setPosition(position++);
                selection.setPoints(rule.getPoints());
                selectionRepository.save(selection);
            }
        }

        return findSelections(assessmentId);
    }

    private void applyStatusTransition(Assessment assessment, AssessmentStatus nextStatus) {
        if (assessment.getStatus() == AssessmentStatus.ARCHIVED) {
            throw new IllegalArgumentException("Archived assessments are read only");
        }
        if (nextStatus == AssessmentStatus.PUBLISHED) {
            validateAssessmentSettings(assessment);
            if (selectionRepository.countByAssessmentId(assessment.getId()) == 0) {
                throw new IllegalArgumentException("Published assessments need at least one selected question");
            }
            if (assessment.getCompositionMode() == AssessmentCompositionMode.RANDOM
                && ruleRepository.countByAssessmentId(assessment.getId()) == 0) {
                throw new IllegalArgumentException("Published random assessments need question rules");
            }
        }
        assessment.setStatus(nextStatus);
        if (nextStatus == AssessmentStatus.PUBLISHED) {
            // Callers only reach this on a real status change, so students are announced to once.
            announcePublication(assessment);
        }
    }

    /**
     * ponytail: fans out one row per enrolled student in this transaction. Fine at course scale;
     * batch or move off-request if a course ever grows past a few thousand enrollments.
     */
    private void announcePublication(Assessment assessment) {
        List<Enrollment> enrollments = enrollmentRepository
            .findByCourseIdAndStatusNot(assessment.getCourse().getId(), EnrollmentStatus.CANCELLED);
        for (Enrollment enrollment : enrollments) {
            notificationService.notify(
                enrollment.getUser(),
                NotificationType.ASSESSMENT_PUBLISHED,
                "Có bài kiểm tra mới",
                "Bài kiểm tra mới đã được mở: " + assessment.getTitle(),
                assessment.getId()
            );
        }
    }

    private void validateAssessmentSettings(Assessment assessment) {
        validateSettings(
            assessment.getAvailableFrom(),
            assessment.getAvailableUntil(),
            assessment.getTimeLimitMinutes(),
            assessment.getMaxAttempts(),
            assessment.getPassingScore()
        );
    }

    private void validateSettings(
        java.time.LocalDateTime availableFrom,
        java.time.LocalDateTime availableUntil,
        Integer timeLimitMinutes,
        Integer maxAttempts,
        BigDecimal passingScore
    ) {
        if (availableFrom != null && availableUntil != null && availableFrom.isAfter(availableUntil)) {
            throw new IllegalArgumentException("availableFrom must be before availableUntil");
        }
        if (timeLimitMinutes != null && timeLimitMinutes < 1) {
            throw new IllegalArgumentException("timeLimitMinutes must be positive");
        }
        if (maxAttempts != null && maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (passingScore != null && (passingScore.compareTo(BigDecimal.ZERO) < 0
            || passingScore.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new IllegalArgumentException("passingScore must be between 0 and 100");
        }
    }

    private Course requireCourse(Long courseId) {
        return courseRepository.findById(courseId)
            .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseId));
    }

    private Assessment requireAssessment(Long id) {
        return assessmentRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Assessment not found: " + id));
    }

    private Lesson resolveLesson(Long courseId, Long lessonId) {
        if (lessonId == null) {
            return null;
        }
        Lesson lesson = lessonRepository.findById(lessonId)
            .orElseThrow(() -> new IllegalArgumentException("Lesson not found: " + lessonId));
        if (!lesson.getSection().getCourse().getId().equals(courseId)) {
            throw new IllegalArgumentException("Lesson does not belong to course: " + lessonId);
        }
        return lesson;
    }

    private Question requireQuestion(Long questionId) {
        return questionRepository.findById(questionId)
            .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));
    }

    private QuestionTopic resolveTopic(Long courseId, Long topicId) {
        if (topicId == null) {
            return null;
        }
        QuestionTopic topic = questionTopicRepository.findById(topicId)
            .orElseThrow(() -> new IllegalArgumentException("Question topic not found: " + topicId));
        if (!topic.getCourse().getId().equals(courseId)) {
            throw new IllegalArgumentException("Question topic does not belong to course: " + topicId);
        }
        return topic;
    }

    private AssessmentQuestionSelection requireSelection(Long selectionId) {
        return selectionRepository.findById(selectionId)
            .orElseThrow(() -> new IllegalArgumentException("Assessment question selection not found: " + selectionId));
    }

    private AssessmentQuestionRule requireRule(Long ruleId) {
        return ruleRepository.findById(ruleId)
            .orElseThrow(() -> new IllegalArgumentException("Assessment question rule not found: " + ruleId));
    }

    private void ensureCanManage(Course course, Authentication authentication) {
        courseAccessPolicy.ensureCanManage(course, authentication);
    }

    private void ensureDraftComposition(Assessment assessment, AssessmentCompositionMode mode) {
        if (assessment.getStatus() != AssessmentStatus.DRAFT) {
            throw new IllegalArgumentException("Only draft assessments can be changed");
        }
        if (assessment.getCompositionMode() != mode) {
            throw new IllegalArgumentException("Assessment composition mode must be " + mode);
        }
    }

    private void ensureQuestionInAssessmentCourse(Assessment assessment, Question question) {
        if (!question.getCourse().getId().equals(assessment.getCourse().getId())) {
            throw new IllegalArgumentException("Question does not belong to assessment course: " + question.getId());
        }
    }

    private void ensureUniqueSelection(Long assessmentId, Long questionId, Integer position, Long currentSelectionId) {
        List<AssessmentQuestionSelection> selections = selectionRepository.findByAssessmentIdOrderByPosition(assessmentId);
        for (AssessmentQuestionSelection selection : selections) {
            if (selection.getId().equals(currentSelectionId)) {
                continue;
            }
            if (selection.getQuestion().getId().equals(questionId)) {
                throw new IllegalArgumentException("Question is already selected for assessment: " + questionId);
            }
            if (selection.getPosition().equals(position)) {
                throw new IllegalArgumentException("Selection position is already used: " + position);
            }
        }
    }

    private void ensureRulePositionAvailable(Long assessmentId, Integer position, Long currentRuleId) {
        List<AssessmentQuestionRule> rules = ruleRepository.findByAssessmentIdOrderByPosition(assessmentId);
        for (AssessmentQuestionRule rule : rules) {
            if (!rule.getId().equals(currentRuleId) && rule.getPosition().equals(position)) {
                throw new IllegalArgumentException("Rule position is already used: " + position);
            }
        }
    }

    private AssessmentResponse toResponse(Assessment assessment) {
        return new AssessmentResponse(
            assessment.getId(),
            assessment.getCourse() != null ? assessment.getCourse().getId() : null,
            assessment.getLesson() != null ? assessment.getLesson().getId() : null,
            assessment.getTitle(),
            assessment.getDescription(),
            assessment.getType(),
            assessment.getStatus(),
            assessment.getCompositionMode(),
            assessment.getAvailableFrom(),
            assessment.getAvailableUntil(),
            assessment.getTimeLimitMinutes(),
            assessment.getMaxAttempts(),
            assessment.getPassingScore(),
            assessment.getShuffleQuestions(),
            assessment.getShuffleOptions(),
            assessment.getShowAnswersAfterSubmit()
        );
    }

    private AssessmentQuestionSelectionResponse toSelectionResponse(AssessmentQuestionSelection selection) {
        return new AssessmentQuestionSelectionResponse(
            selection.getId(),
            selection.getAssessment().getId(),
            selection.getPosition(),
            selection.getPoints(),
            questionBankService.toQuestionResponse(selection.getQuestion())
        );
    }

    private AssessmentQuestionRuleResponse toRuleResponse(AssessmentQuestionRule rule) {
        return new AssessmentQuestionRuleResponse(
            rule.getId(),
            rule.getAssessment().getId(),
            rule.getTopic() != null ? rule.getTopic().getId() : null,
            rule.getDifficulty(),
            rule.getQuestionType(),
            rule.getQuestionCount(),
            rule.getPoints(),
            rule.getPosition()
        );
    }
}
