package com.example.learning_backend.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.learning_backend.assessment.dto.AssessmentCreateRequest;
import com.example.learning_backend.assessment.dto.AssessmentQuestionRuleRequest;
import com.example.learning_backend.assessment.dto.AssessmentQuestionSelectionRequest;
import com.example.learning_backend.assessment.dto.QuestionOptionRequest;
import com.example.learning_backend.assessment.dto.QuestionRequest;
import com.example.learning_backend.assessment.dto.QuestionTopicCreateRequest;
import com.example.learning_backend.assessment.enums.AssessmentCompositionMode;
import com.example.learning_backend.assessment.enums.AssessmentType;
import com.example.learning_backend.assessment.enums.QuestionDifficulty;
import com.example.learning_backend.assessment.enums.QuestionType;
import com.example.learning_backend.assessment.service.AssessmentService;
import com.example.learning_backend.assessment.service.QuestionBankService;
import com.example.learning_backend.course.entity.Course;
import com.example.learning_backend.course.repository.CourseRepository;
import com.example.learning_backend.user.entity.User;
import com.example.learning_backend.user.repository.UserRepository;
import java.math.BigDecimal;
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
class AssessmentAuthoringServiceTests {

    @Autowired
    private AssessmentService assessmentService;

    @Autowired
    private QuestionBankService questionBankService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserRepository userRepository;

    private Course course;
    private Authentication instructorAuth;

    @BeforeEach
    void setUp() {
        User instructor = new User();
        instructor.setEmail("instructor@example.com");
        instructor.setFullName("Instructor");
        instructor.setPasswordHash("hash");
        instructor = userRepository.save(instructor);

        course = new Course();
        course.setSlug("java-core-test");
        course.setTitle("Java Core Test");
        course.setInstructor(instructor);
        course = courseRepository.save(course);

        instructorAuth = new UsernamePasswordAuthenticationToken(
            instructor.getEmail(),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_INSTRUCTOR"))
        );
    }

    @Test
    void createQuestionRejectsInvalidSingleChoice() {
        QuestionRequest request = new QuestionRequest(
            null,
            "Which keyword creates a subclass?",
            QuestionType.SINGLE_CHOICE,
            QuestionDifficulty.EASY,
            BigDecimal.ONE,
            null,
            null,
            List.of(
                new QuestionOptionRequest("extends", true, 1),
                new QuestionOptionRequest("implements", true, 2)
            )
        );

        assertThatThrownBy(() -> questionBankService.createQuestion(course.getId(), request, instructorAuth))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exactly one correct option");
    }

    @Test
    void fixedAssessmentSelectsQuestionFromSameCourse() {
        var topic = questionBankService.createTopic(
            course.getId(),
            new QuestionTopicCreateRequest("Java Basics", null),
            instructorAuth
        );
        var question = questionBankService.createQuestion(
            course.getId(),
            new QuestionRequest(
                topic.id(),
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
        var assessment = assessmentService.create(
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
                BigDecimal.valueOf(70),
                false,
                true,
                false
            ),
            instructorAuth
        );

        var selection = assessmentService.addSelection(
            assessment.id(),
            new AssessmentQuestionSelectionRequest(question.id(), 1, null),
            instructorAuth
        );

        assertThat(selection.question().id()).isEqualTo(question.id());
        assertThat(selection.points()).isEqualByComparingTo("2");
    }

    @Test
    void randomGenerationRejectsInsufficientInventory() {
        var assessment = assessmentService.create(
            course.getId(),
            new AssessmentCreateRequest(
                "Random Java Quiz",
                null,
                AssessmentType.QUIZ,
                null,
                AssessmentCompositionMode.RANDOM,
                null,
                null,
                30,
                1,
                BigDecimal.valueOf(70),
                true,
                true,
                false
            ),
            instructorAuth
        );
        assessmentService.addRule(
            assessment.id(),
            new AssessmentQuestionRuleRequest(
                null,
                QuestionDifficulty.HARD,
                QuestionType.SINGLE_CHOICE,
                2,
                BigDecimal.ONE,
                1
            ),
            instructorAuth
        );

        assertThatThrownBy(() -> assessmentService.generateSelections(assessment.id(), instructorAuth))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Not enough questions");
    }
}
