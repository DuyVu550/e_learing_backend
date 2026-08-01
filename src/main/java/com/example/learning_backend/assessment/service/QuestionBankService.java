package com.example.learning_backend.assessment.service;

import com.example.learning_backend.assessment.dto.QuestionOptionRequest;
import com.example.learning_backend.assessment.dto.QuestionOptionResponse;
import com.example.learning_backend.assessment.dto.QuestionRequest;
import com.example.learning_backend.assessment.dto.QuestionResponse;
import com.example.learning_backend.assessment.dto.QuestionTopicCreateRequest;
import com.example.learning_backend.assessment.dto.QuestionTopicResponse;
import com.example.learning_backend.assessment.entity.Question;
import com.example.learning_backend.assessment.entity.QuestionOption;
import com.example.learning_backend.assessment.entity.QuestionTopic;
import com.example.learning_backend.assessment.enums.AssessmentStatus;
import com.example.learning_backend.assessment.enums.QuestionType;
import com.example.learning_backend.assessment.repository.AssessmentQuestionSelectionRepository;
import com.example.learning_backend.assessment.repository.QuestionOptionRepository;
import com.example.learning_backend.assessment.repository.QuestionRepository;
import com.example.learning_backend.assessment.repository.QuestionTopicRepository;
import com.example.learning_backend.course.entity.Course;
import com.example.learning_backend.course.repository.CourseRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class QuestionBankService {

    private final CourseRepository courseRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final QuestionTopicRepository questionTopicRepository;
    private final AssessmentQuestionSelectionRepository selectionRepository;

    public QuestionBankService(
        CourseRepository courseRepository,
        QuestionRepository questionRepository,
        QuestionOptionRepository questionOptionRepository,
        QuestionTopicRepository questionTopicRepository,
        AssessmentQuestionSelectionRepository selectionRepository
    ) {
        this.courseRepository = courseRepository;
        this.questionRepository = questionRepository;
        this.questionOptionRepository = questionOptionRepository;
        this.questionTopicRepository = questionTopicRepository;
        this.selectionRepository = selectionRepository;
    }

    @Transactional(readOnly = true)
    public List<QuestionTopicResponse> findTopics(Long courseId) {
        return questionTopicRepository.findByCourseIdOrderByName(courseId).stream()
            .map(this::toTopicResponse)
            .toList();
    }

    public QuestionTopicResponse createTopic(Long courseId, QuestionTopicCreateRequest request, Authentication authentication) {
        Course course = requireCourse(courseId);
        ensureCanManage(course, authentication);
        ensureUniqueTopicName(courseId, request.name());

        QuestionTopic topic = new QuestionTopic();
        topic.setCourse(course);
        topic.setName(request.name());
        topic.setDescription(request.description());
        return toTopicResponse(questionTopicRepository.save(topic));
    }

    public QuestionTopicResponse updateTopic(Long topicId, QuestionTopicCreateRequest request, Authentication authentication) {
        QuestionTopic topic = requireTopic(topicId);
        ensureCanManage(topic.getCourse(), authentication);
        if (!topic.getName().equalsIgnoreCase(request.name())) {
            ensureUniqueTopicName(topic.getCourse().getId(), request.name());
        }
        topic.setName(request.name());
        topic.setDescription(request.description());
        return toTopicResponse(topic);
    }

    public void deleteTopic(Long topicId, Authentication authentication) {
        QuestionTopic topic = requireTopic(topicId);
        ensureCanManage(topic.getCourse(), authentication);
        if (questionRepository.existsByTopicId(topicId)) {
            throw new IllegalArgumentException("Question topic is still used by questions: " + topicId);
        }
        questionTopicRepository.delete(topic);
    }

    @Transactional(readOnly = true)
    public List<QuestionResponse> findQuestions(
        Long courseId,
        Long topicId,
        com.example.learning_backend.assessment.enums.QuestionDifficulty difficulty,
        QuestionType type
    ) {
        return questionRepository.findByCourseFilters(courseId, topicId, difficulty, type).stream()
            .map(this::toQuestionResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public QuestionResponse findQuestion(Long questionId) {
        return toQuestionResponse(requireQuestion(questionId));
    }

    public QuestionResponse createQuestion(Long courseId, QuestionRequest request, Authentication authentication) {
        Course course = requireCourse(courseId);
        ensureCanManage(course, authentication);
        QuestionTopic topic = resolveTopic(courseId, request.topicId());
        validateQuestionRequest(request);

        Question question = new Question();
        question.setCourse(course);
        applyQuestion(question, topic, request);
        question = questionRepository.save(question);
        replaceOptions(question, request.options());
        return toQuestionResponse(question);
    }

    public QuestionResponse updateQuestion(Long questionId, QuestionRequest request, Authentication authentication) {
        Question question = requireQuestion(questionId);
        ensureCanManage(question.getCourse(), authentication);
        ensureQuestionCanChange(questionId);
        QuestionTopic topic = resolveTopic(question.getCourse().getId(), request.topicId());
        validateQuestionRequest(request);

        applyQuestion(question, topic, request);
        questionOptionRepository.deleteByQuestionId(questionId);
        replaceOptions(question, request.options());
        return toQuestionResponse(question);
    }

    public void deleteQuestion(Long questionId, Authentication authentication) {
        Question question = requireQuestion(questionId);
        ensureCanManage(question.getCourse(), authentication);
        ensureQuestionCanChange(questionId);
        questionOptionRepository.deleteByQuestionId(questionId);
        questionRepository.delete(question);
    }

    private Course requireCourse(Long courseId) {
        return courseRepository.findById(courseId)
            .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseId));
    }

    private QuestionTopic requireTopic(Long topicId) {
        return questionTopicRepository.findById(topicId)
            .orElseThrow(() -> new IllegalArgumentException("Question topic not found: " + topicId));
    }

    private Question requireQuestion(Long questionId) {
        return questionRepository.findById(questionId)
            .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));
    }

    private QuestionTopic resolveTopic(Long courseId, Long topicId) {
        if (topicId == null) {
            return null;
        }
        QuestionTopic topic = requireTopic(topicId);
        if (!topic.getCourse().getId().equals(courseId)) {
            throw new IllegalArgumentException("Question topic does not belong to course: " + topicId);
        }
        return topic;
    }

    private void ensureUniqueTopicName(Long courseId, String name) {
        if (questionTopicRepository.existsByCourseIdAndNameIgnoreCase(courseId, name)) {
            throw new IllegalArgumentException("Question topic already exists in course: " + name);
        }
    }

    private void ensureQuestionCanChange(Long questionId) {
        if (selectionRepository.existsByQuestionIdAndAssessmentStatus(questionId, AssessmentStatus.PUBLISHED)) {
            throw new IllegalArgumentException("Published assessments already use this question: " + questionId);
        }
    }

    private void applyQuestion(Question question, QuestionTopic topic, QuestionRequest request) {
        question.setTopic(topic);
        question.setQuestionText(request.questionText());
        question.setType(request.type());
        question.setDifficulty(request.difficulty());
        question.setPoints(request.points());
        question.setExpectedAnswer(request.expectedAnswer());
        question.setExplanation(request.explanation());
    }

    private void replaceOptions(Question question, List<QuestionOptionRequest> requests) {
        for (QuestionOptionRequest request : safeOptions(requests)) {
            QuestionOption option = new QuestionOption();
            option.setQuestion(question);
            option.setOptionText(request.optionText());
            option.setCorrect(request.correct());
            option.setPosition(request.position());
            questionOptionRepository.save(option);
        }
    }

    private void validateQuestionRequest(QuestionRequest request) {
        List<QuestionOptionRequest> options = safeOptions(request.options());
        long correctCount = options.stream().filter(option -> Boolean.TRUE.equals(option.correct())).count();
        long positionCount = options.stream().map(QuestionOptionRequest::position).distinct().count();
        if (positionCount != options.size()) {
            throw new IllegalArgumentException("Question option positions must be unique");
        }

        switch (request.type()) {
            case SINGLE_CHOICE -> {
                if (options.size() < 2 || correctCount != 1) {
                    throw new IllegalArgumentException("Single choice questions need at least two options and exactly one correct option");
                }
            }
            case MULTIPLE_CHOICE -> {
                if (options.size() < 2 || correctCount < 1) {
                    throw new IllegalArgumentException("Multiple choice questions need at least two options and one correct option");
                }
            }
            case TRUE_FALSE -> {
                if (options.size() != 2 || correctCount != 1) {
                    throw new IllegalArgumentException("True/false questions need exactly two options and exactly one correct option");
                }
            }
            case FILL_IN_BLANK -> {
                if (!options.isEmpty() || request.expectedAnswer() == null || request.expectedAnswer().isBlank()) {
                    throw new IllegalArgumentException("Fill in blank questions need an expected answer and no options");
                }
            }
            case SHORT_ANSWER -> {
                if (!options.isEmpty()) {
                    throw new IllegalArgumentException("Short answer questions must not have options");
                }
            }
        }
    }

    private List<QuestionOptionRequest> safeOptions(List<QuestionOptionRequest> options) {
        return options == null ? List.of() : options;
    }

    private void ensureCanManage(Course course, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        boolean admin = authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        if (admin) {
            return;
        }
        if (course.getInstructor() == null || !course.getInstructor().getEmail().equals(authentication.getName())) {
            throw new IllegalArgumentException("You cannot manage this course");
        }
    }

    private QuestionTopicResponse toTopicResponse(QuestionTopic topic) {
        return new QuestionTopicResponse(
            topic.getId(),
            topic.getCourse() != null ? topic.getCourse().getId() : null,
            topic.getName(),
            topic.getDescription()
        );
    }

    public QuestionResponse toQuestionResponse(Question question) {
        List<QuestionOptionResponse> options = questionOptionRepository.findByQuestionIdOrderByPosition(question.getId())
            .stream()
            .sorted(Comparator.comparing(QuestionOption::getPosition))
            .map(option -> new QuestionOptionResponse(
                option.getId(),
                option.getOptionText(),
                option.getCorrect(),
                option.getPosition()
            ))
            .toList();
        return new QuestionResponse(
            question.getId(),
            question.getCourse() != null ? question.getCourse().getId() : null,
            question.getTopic() != null ? question.getTopic().getId() : null,
            question.getQuestionText(),
            question.getType(),
            question.getDifficulty(),
            question.getPoints(),
            question.getExpectedAnswer(),
            question.getExplanation(),
            options
        );
    }
}
