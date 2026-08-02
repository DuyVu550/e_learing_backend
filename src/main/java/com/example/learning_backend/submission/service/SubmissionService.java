package com.example.learning_backend.submission.service;

import com.example.learning_backend.assessment.entity.Assessment;
import com.example.learning_backend.assessment.entity.AssessmentQuestionSelection;
import com.example.learning_backend.assessment.entity.Question;
import com.example.learning_backend.assessment.entity.QuestionOption;
import com.example.learning_backend.assessment.enums.AssessmentStatus;
import com.example.learning_backend.assessment.enums.QuestionType;
import com.example.learning_backend.assessment.repository.AssessmentQuestionSelectionRepository;
import com.example.learning_backend.assessment.repository.AssessmentRepository;
import com.example.learning_backend.assessment.repository.QuestionOptionRepository;
import com.example.learning_backend.course.entity.Course;
import com.example.learning_backend.course.service.CourseAccessPolicy;
import com.example.learning_backend.enrollment.entity.Enrollment;
import com.example.learning_backend.enrollment.service.EnrollmentAccessPolicy;
import com.example.learning_backend.notification.enums.NotificationType;
import com.example.learning_backend.notification.service.NotificationService;
import com.example.learning_backend.submission.dto.AnswerResultResponse;
import com.example.learning_backend.submission.dto.AnswerSubmitRequest;
import com.example.learning_backend.submission.dto.AssessmentAttemptResponse;
import com.example.learning_backend.submission.dto.AssessmentSubmitRequest;
import com.example.learning_backend.submission.dto.AttemptDetailResponse;
import com.example.learning_backend.submission.dto.AttemptOptionResponse;
import com.example.learning_backend.submission.dto.AttemptQuestionResponse;
import com.example.learning_backend.submission.dto.AttemptResultResponse;
import com.example.learning_backend.submission.dto.ManualGradeRequest;
import com.example.learning_backend.submission.entity.Answer;
import com.example.learning_backend.submission.entity.AssessmentAttempt;
import com.example.learning_backend.submission.enums.AttemptStatus;
import com.example.learning_backend.submission.repository.AnswerRepository;
import com.example.learning_backend.submission.repository.AssessmentAttemptRepository;
import com.example.learning_backend.user.entity.User;
import com.example.learning_backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SubmissionService {

    private final AssessmentAttemptRepository attemptRepository;
    private final AnswerRepository answerRepository;
    private final AssessmentRepository assessmentRepository;
    private final AssessmentQuestionSelectionRepository selectionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final EnrollmentAccessPolicy enrollmentAccessPolicy;
    private final UserRepository userRepository;
    private final CourseAccessPolicy courseAccessPolicy;
    private final NotificationService notificationService;

    public SubmissionService(
        AssessmentAttemptRepository attemptRepository,
        AnswerRepository answerRepository,
        AssessmentRepository assessmentRepository,
        AssessmentQuestionSelectionRepository selectionRepository,
        QuestionOptionRepository questionOptionRepository,
        EnrollmentAccessPolicy enrollmentAccessPolicy,
        UserRepository userRepository,
        CourseAccessPolicy courseAccessPolicy,
        NotificationService notificationService
    ) {
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
        this.assessmentRepository = assessmentRepository;
        this.selectionRepository = selectionRepository;
        this.questionOptionRepository = questionOptionRepository;
        this.enrollmentAccessPolicy = enrollmentAccessPolicy;
        this.userRepository = userRepository;
        this.courseAccessPolicy = courseAccessPolicy;
        this.notificationService = notificationService;
    }

    public AttemptDetailResponse startOrResume(String email, Long assessmentId) {
        User user = requireUser(email);
        Assessment assessment = requireAssessment(assessmentId);
        LocalDateTime now = LocalDateTime.now();

        if (assessment.getStatus() != AssessmentStatus.PUBLISHED) {
            throw new IllegalArgumentException("Assessment is not published: " + assessmentId);
        }
        requireActiveEnrollment(user.getId(), assessment.getCourse().getId());

        AssessmentAttempt existing = attemptRepository
            .findFirstByAssessmentIdAndUserIdAndStatusOrderByStartedAtDesc(assessmentId, user.getId(), AttemptStatus.IN_PROGRESS)
            .orElse(null);
        if (existing != null) {
            enforceDeadline(existing, now);
            return toDetailResponse(existing);
        }

        if (assessment.getAvailableFrom() != null && now.isBefore(assessment.getAvailableFrom())) {
            throw new IllegalArgumentException("Assessment is not open yet: " + assessmentId);
        }
        if (assessment.getAvailableUntil() != null && now.isAfter(assessment.getAvailableUntil())) {
            throw new IllegalArgumentException("Assessment is already closed: " + assessmentId);
        }
        long used = attemptRepository.countByAssessmentIdAndUserId(assessmentId, user.getId());
        if (assessment.getMaxAttempts() != null && used >= assessment.getMaxAttempts()) {
            throw new IllegalArgumentException("No attempts left for assessment: " + assessmentId);
        }

        AssessmentAttempt attempt = new AssessmentAttempt();
        attempt.setAssessment(assessment);
        attempt.setUser(user);
        attempt.setAttemptNo((int) used + 1);
        attempt.setStatus(AttemptStatus.IN_PROGRESS);
        attempt.setStartedAt(now);
        try {
            attempt = attemptRepository.saveAndFlush(attempt);
        } catch (DataIntegrityViolationException ex) {
            // A parallel request won the uk_attempts_assessment_user_no race; reuse the attempt it created.
            attempt = attemptRepository
                .findFirstByAssessmentIdAndUserIdAndStatusOrderByStartedAtDesc(assessmentId, user.getId(), AttemptStatus.IN_PROGRESS)
                .orElseThrow(() -> new IllegalArgumentException("Could not start attempt, please retry"));
        }
        return toDetailResponse(attempt);
    }

    public AttemptDetailResponse getAttempt(String email, Long attemptId) {
        AssessmentAttempt attempt = requireOwnedAttempt(email, attemptId);
        enforceDeadline(attempt, LocalDateTime.now());
        return toDetailResponse(attempt);
    }

    public AttemptDetailResponse saveDraft(String email, Long attemptId, AssessmentSubmitRequest request) {
        AssessmentAttempt attempt = requireOwnedAttempt(email, attemptId);
        if (enforceDeadline(attempt, LocalDateTime.now())) {
            throw new IllegalArgumentException("Time is up, the attempt was submitted automatically");
        }
        requireInProgress(attempt);
        applyAnswers(attempt, request);
        return toDetailResponse(attempt);
    }

    public AttemptResultResponse submit(String email, Long attemptId, AssessmentSubmitRequest request) {
        AssessmentAttempt attempt = requireOwnedAttempt(email, attemptId);
        LocalDateTime now = LocalDateTime.now();
        if (!enforceDeadline(attempt, now)) {
            requireInProgress(attempt);
            applyAnswers(attempt, request);
            grade(attempt, now);
        }
        return toResultResponse(attempt);
    }

    public AttemptResultResponse getResult(String email, Long attemptId) {
        AssessmentAttempt attempt = requireOwnedAttempt(email, attemptId);
        enforceDeadline(attempt, LocalDateTime.now());
        return toResultResponse(attempt);
    }

    @Transactional(readOnly = true)
    public List<AssessmentAttemptResponse> myAttempts(String email) {
        User user = requireUser(email);
        return attemptRepository.findByUserId(user.getId(), Pageable.unpaged())
            .stream()
            .map(this::toAttemptResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<AssessmentAttemptResponse> findByAssessment(Long assessmentId, Authentication authentication) {
        Assessment assessment = requireAssessment(assessmentId);
        ensureCanManage(assessment.getCourse(), authentication);
        return attemptRepository.findByAssessmentId(assessmentId, Pageable.unpaged())
            .stream()
            .map(this::toAttemptResponse)
            .toList();
    }

    public AnswerResultResponse gradeAnswer(Long answerId, ManualGradeRequest request, Authentication authentication) {
        Answer answer = answerRepository.findById(answerId)
            .orElseThrow(() -> new IllegalArgumentException("Answer not found: " + answerId));
        AssessmentAttempt attempt = answer.getAttempt();
        Assessment assessment = attempt.getAssessment();
        ensureCanManage(assessment.getCourse(), authentication);

        if (answer.getQuestion().getType() != QuestionType.SHORT_ANSWER) {
            throw new IllegalArgumentException("Only short answer questions are graded manually: " + answerId);
        }
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("Attempt is not submitted yet: " + attempt.getId());
        }
        AssessmentQuestionSelection selection = selectionsByQuestionId(assessment.getId()).get(answer.getQuestion().getId());
        if (selection == null) {
            throw new IllegalArgumentException("Question is not part of the assessment: " + answer.getQuestion().getId());
        }
        BigDecimal points = selection.getPoints();
        if (request.score().compareTo(points) > 0) {
            throw new IllegalArgumentException("Score must not exceed question points: " + points);
        }

        answer.setScore(request.score());
        answer.setCorrect(request.score().compareTo(points) == 0);
        answer.setFeedback(request.feedback());
        answer.setGradedBy(requireUser(authentication.getName()));
        answer.setGradedAt(LocalDateTime.now());
        recomputeAttemptScore(attempt);
        notificationService.notifyOthers(
            attempt.getUser(),
            answer.getGradedBy(),
            NotificationType.ANSWER_GRADED,
            "Bài tự luận của bạn đã được chấm",
            "Giảng viên đã chấm điểm câu tự luận trong bài: " + assessment.getTitle(),
            attempt.getId()
        );
        return toAnswerResult(answer, selection, revealsAnswers(attempt));
    }

    /**
     * Lazily closes an attempt whose time is up. Returns true when this call auto-submitted it.
     * Must be called from every entry point that touches an attempt.
     */
    private boolean enforceDeadline(AssessmentAttempt attempt, LocalDateTime now) {
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            return false;
        }
        LocalDateTime deadline = deadlineOf(attempt);
        if (deadline == null || !now.isAfter(deadline)) {
            return false;
        }
        grade(attempt, deadline);
        return true;
    }

    // ponytail: derived instead of a stored expires_at; assessments are immutable once published,
    // so timeLimitMinutes cannot change mid-attempt. Store the deadline if that rule ever relaxes.
    private LocalDateTime deadlineOf(AssessmentAttempt attempt) {
        Assessment assessment = attempt.getAssessment();
        LocalDateTime byTimeLimit = assessment.getTimeLimitMinutes() == null
            ? null
            : attempt.getStartedAt().plusMinutes(assessment.getTimeLimitMinutes());
        LocalDateTime byWindow = assessment.getAvailableUntil();
        if (byTimeLimit == null) {
            return byWindow;
        }
        if (byWindow == null) {
            return byTimeLimit;
        }
        return byTimeLimit.isBefore(byWindow) ? byTimeLimit : byWindow;
    }

    private void applyAnswers(AssessmentAttempt attempt, AssessmentSubmitRequest request) {
        if (request == null || request.answers() == null) {
            return;
        }
        Map<Long, AssessmentQuestionSelection> selections = selectionsByQuestionId(attempt.getAssessment().getId());
        for (AnswerSubmitRequest submitted : request.answers()) {
            AssessmentQuestionSelection selection = selections.get(submitted.questionId());
            if (selection == null) {
                throw new IllegalArgumentException("Question is not part of the assessment: " + submitted.questionId());
            }
            Question question = selection.getQuestion();
            Answer answer = answerRepository.findByAttemptIdAndQuestionId(attempt.getId(), submitted.questionId())
                .orElseGet(() -> {
                    Answer created = new Answer();
                    created.setAttempt(attempt);
                    created.setQuestion(question);
                    return created;
                });
            answer.setAnswerText(submitted.answerText());
            answer.setSelectedOptions(resolveOptions(question, submitted.selectedOptionIds()));
            if (submitted.flagged() != null) {
                answer.setFlagged(submitted.flagged());
            }
            answerRepository.save(answer);
        }
    }

    private Set<QuestionOption> resolveOptions(Question question, Set<Long> optionIds) {
        Set<QuestionOption> selected = new LinkedHashSet<>();
        if (optionIds == null || optionIds.isEmpty()) {
            return selected;
        }
        Map<Long, QuestionOption> available = new HashMap<>();
        for (QuestionOption option : questionOptionRepository.findByQuestionId(question.getId())) {
            available.put(option.getId(), option);
        }
        for (Long optionId : optionIds) {
            QuestionOption option = available.get(optionId);
            if (option == null) {
                throw new IllegalArgumentException("Option does not belong to question: " + optionId);
            }
            selected.add(option);
        }
        return selected;
    }

    private void grade(AssessmentAttempt attempt, LocalDateTime submittedAt) {
        attempt.setSubmittedAt(submittedAt);
        Map<Long, AssessmentQuestionSelection> selections = selectionsByQuestionId(attempt.getAssessment().getId());
        for (Answer answer : answerRepository.findByAttemptId(attempt.getId())) {
            AssessmentQuestionSelection selection = selections.get(answer.getQuestion().getId());
            if (selection == null) {
                continue;
            }
            if (answer.getQuestion().getType() == QuestionType.SHORT_ANSWER) {
                continue;
            }
            BigDecimal questionPoints = selection.getPoints();
            BigDecimal score = autoScore(answer, questionPoints);
            answer.setScore(score);
            answer.setCorrect(score.compareTo(questionPoints) == 0);
            answer.setGradedAt(submittedAt);
        }
        recomputeAttemptScore(attempt);
    }

    private BigDecimal autoScore(Answer answer, BigDecimal questionPoints) {
        Question question = answer.getQuestion();
        List<QuestionOption> options = questionOptionRepository.findByQuestionId(question.getId());
        Set<Long> selected = answer.getSelectedOptions().stream().map(QuestionOption::getId).collect(Collectors.toSet());

        return switch (question.getType()) {
            case SINGLE_CHOICE, TRUE_FALSE -> {
                Set<Long> correct = correctOptionIds(options);
                yield selected.equals(correct) ? questionPoints : BigDecimal.ZERO;
            }
            case MULTIPLE_CHOICE -> {
                Set<Long> correct = correctOptionIds(options);
                if (correct.isEmpty()) {
                    yield BigDecimal.ZERO;
                }
                long hits = selected.stream().filter(correct::contains).count();
                long misses = selected.size() - hits;
                BigDecimal ratio = BigDecimal.valueOf(hits - misses)
                    .divide(BigDecimal.valueOf(correct.size()), 4, RoundingMode.HALF_UP);
                if (ratio.compareTo(BigDecimal.ZERO) <= 0) {
                    yield BigDecimal.ZERO;
                }
                yield ratio.multiply(questionPoints).setScale(2, RoundingMode.HALF_UP);
            }
            case FILL_IN_BLANK -> {
                String expected = question.getExpectedAnswer();
                String actual = answer.getAnswerText();
                boolean matches = expected != null && actual != null
                    && expected.trim().equalsIgnoreCase(actual.trim());
                yield matches ? questionPoints : BigDecimal.ZERO;
            }
            case SHORT_ANSWER -> BigDecimal.ZERO;
        };
    }

    private void recomputeAttemptScore(AssessmentAttempt attempt) {
        Assessment assessment = attempt.getAssessment();
        Map<Long, AssessmentQuestionSelection> selections = selectionsByQuestionId(assessment.getId());
        BigDecimal maxScore = selections.values().stream()
            .map(AssessmentQuestionSelection::getPoints)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal score = BigDecimal.ZERO;
        boolean awaitingManual = false;
        for (Answer answer : answerRepository.findByAttemptId(attempt.getId())) {
            if (!selections.containsKey(answer.getQuestion().getId())) {
                continue;
            }
            if (answer.getScore() != null) {
                score = score.add(answer.getScore());
            } else if (answer.getQuestion().getType() == QuestionType.SHORT_ANSWER) {
                awaitingManual = true;
            }
        }

        attempt.setScore(score.setScale(2, RoundingMode.HALF_UP));
        attempt.setMaxScore(maxScore.setScale(2, RoundingMode.HALF_UP));
        attempt.setStatus(awaitingManual ? AttemptStatus.SUBMITTED : AttemptStatus.GRADED);
        attempt.setPassed(evaluatePassed(assessment, attempt.getScore(), maxScore, awaitingManual));
    }

    private Boolean evaluatePassed(Assessment assessment, BigDecimal score, BigDecimal maxScore, boolean awaitingManual) {
        if (assessment.getPassingScore() == null || awaitingManual || maxScore.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        BigDecimal percentage = score.multiply(BigDecimal.valueOf(100))
            .divide(maxScore, 2, RoundingMode.HALF_UP);
        return percentage.compareTo(assessment.getPassingScore()) >= 0;
    }

    private Set<Long> correctOptionIds(List<QuestionOption> options) {
        return options.stream()
            .filter(option -> Boolean.TRUE.equals(option.getCorrect()))
            .map(QuestionOption::getId)
            .collect(Collectors.toSet());
    }

    private Map<Long, AssessmentQuestionSelection> selectionsByQuestionId(Long assessmentId) {
        Map<Long, AssessmentQuestionSelection> selections = new HashMap<>();
        for (AssessmentQuestionSelection selection : selectionRepository.findByAssessmentIdOrderByPosition(assessmentId)) {
            selections.put(selection.getQuestion().getId(), selection);
        }
        return selections;
    }

    private boolean revealsAnswers(AssessmentAttempt attempt) {
        return attempt.getStatus() != AttemptStatus.IN_PROGRESS
            && Boolean.TRUE.equals(attempt.getAssessment().getShowAnswersAfterSubmit());
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
    }

    private Assessment requireAssessment(Long assessmentId) {
        return assessmentRepository.findById(assessmentId)
            .orElseThrow(() -> new IllegalArgumentException("Assessment not found: " + assessmentId));
    }

    private AssessmentAttempt requireOwnedAttempt(String email, Long attemptId) {
        User user = requireUser(email);
        return attemptRepository.findByIdAndUserId(attemptId, user.getId())
            .orElseThrow(() -> new IllegalArgumentException("Assessment attempt not found: " + attemptId));
    }

    private void requireInProgress(AssessmentAttempt attempt) {
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("Attempt is no longer in progress: " + attempt.getId());
        }
    }

    private Enrollment requireActiveEnrollment(Long userId, Long courseId) {
        return enrollmentAccessPolicy.requireActive(userId, courseId);
    }

    private void ensureCanManage(Course course, Authentication authentication) {
        courseAccessPolicy.ensureCanManage(course, authentication);
    }

    private AttemptDetailResponse toDetailResponse(AssessmentAttempt attempt) {
        Assessment assessment = attempt.getAssessment();
        List<AssessmentQuestionSelection> selections =
            new ArrayList<>(selectionRepository.findByAssessmentIdOrderByPosition(assessment.getId()));
        // ponytail: shuffle order is re-derived from the attempt id so resume is stable without an
        // attempt_questions snapshot; safe while published assessments stay immutable.
        if (Boolean.TRUE.equals(assessment.getShuffleQuestions())) {
            Collections.shuffle(selections, new Random(attempt.getId()));
        }

        Map<Long, Answer> answers = new HashMap<>();
        for (Answer answer : answerRepository.findByAttemptId(attempt.getId())) {
            answers.put(answer.getQuestion().getId(), answer);
        }

        List<AttemptQuestionResponse> questions = new ArrayList<>();
        for (AssessmentQuestionSelection selection : selections) {
            Question question = selection.getQuestion();
            Answer answer = answers.get(question.getId());
            questions.add(new AttemptQuestionResponse(
                question.getId(),
                selection.getPosition(),
                selection.getPoints(),
                question.getQuestionText(),
                question.getType(),
                question.getDifficulty(),
                toOptionResponses(attempt, question, assessment.getShuffleOptions()),
                answer == null ? null : answer.getAnswerText(),
                selectedOptionIds(answer),
                answer != null && Boolean.TRUE.equals(answer.getFlagged())
            ));
        }
        return new AttemptDetailResponse(toAttemptResponse(attempt), deadlineOf(attempt), questions);
    }

    private List<AttemptOptionResponse> toOptionResponses(AssessmentAttempt attempt, Question question, Boolean shuffle) {
        List<QuestionOption> options = new ArrayList<>(questionOptionRepository.findByQuestionIdOrderByPosition(question.getId()));
        if (Boolean.TRUE.equals(shuffle)) {
            Collections.shuffle(options, new Random(31L * attempt.getId() + question.getId()));
        }
        return options.stream()
            .map(option -> new AttemptOptionResponse(option.getId(), option.getOptionText(), option.getPosition()))
            .toList();
    }

    private AttemptResultResponse toResultResponse(AssessmentAttempt attempt) {
        Assessment assessment = attempt.getAssessment();
        boolean reveal = revealsAnswers(attempt);
        Map<Long, Answer> answers = new HashMap<>();
        for (Answer answer : answerRepository.findByAttemptId(attempt.getId())) {
            answers.put(answer.getQuestion().getId(), answer);
        }

        List<AnswerResultResponse> results = new ArrayList<>();
        boolean awaitingManual = false;
        for (AssessmentQuestionSelection selection : selectionRepository.findByAssessmentIdOrderByPosition(assessment.getId())) {
            Answer answer = answers.get(selection.getQuestion().getId());
            if (answer == null) {
                continue;
            }
            if (answer.getQuestion().getType() == QuestionType.SHORT_ANSWER && answer.getScore() == null) {
                awaitingManual = true;
            }
            results.add(toAnswerResult(answer, selection, reveal));
        }
        return new AttemptResultResponse(toAttemptResponse(attempt), reveal, awaitingManual, results);
    }

    private AnswerResultResponse toAnswerResult(Answer answer, AssessmentQuestionSelection selection, boolean reveal) {
        Question question = answer.getQuestion();
        List<QuestionOption> options = questionOptionRepository.findByQuestionIdOrderByPosition(question.getId());
        return new AnswerResultResponse(
            answer.getId(),
            question.getId(),
            selection.getPosition(),
            question.getQuestionText(),
            question.getType(),
            selection.getPoints(),
            answer.getScore(),
            answer.getCorrect(),
            answer.getFlagged(),
            answer.getAnswerText(),
            selectedOptionIds(answer),
            answer.getFeedback(),
            options.stream()
                .map(option -> new AttemptOptionResponse(option.getId(), option.getOptionText(), option.getPosition()))
                .toList(),
            reveal ? correctOptionIds(options) : null,
            reveal ? question.getExpectedAnswer() : null,
            reveal ? question.getExplanation() : null
        );
    }

    private Set<Long> selectedOptionIds(Answer answer) {
        if (answer == null) {
            return Set.of();
        }
        return answer.getSelectedOptions().stream()
            .map(QuestionOption::getId)
            .collect(Collectors.toSet());
    }

    private AssessmentAttemptResponse toAttemptResponse(AssessmentAttempt attempt) {
        return new AssessmentAttemptResponse(
            attempt.getId(),
            attempt.getAssessment() != null ? attempt.getAssessment().getId() : null,
            attempt.getAttemptNo(),
            attempt.getStatus(),
            attempt.getStartedAt(),
            attempt.getSubmittedAt(),
            attempt.getScore(),
            attempt.getMaxScore(),
            attempt.getPassed()
        );
    }
}
