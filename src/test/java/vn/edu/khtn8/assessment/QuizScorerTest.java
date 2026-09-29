package vn.edu.khtn8.assessment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.assessment.QuizModels.*;
import static org.junit.jupiter.api.Assertions.*;

class QuizScorerTest {
    private final List<Question> questions = List.of(
            new Question(1, "First", "Why", new BigDecimal("2"), List.of(new Option(11, "A", true), new Option(12, "B", false))),
            new Question(2, "Second", "Why", new BigDecimal("3"), List.of(new Option(21, "A", false), new Option(22, "B", true))));

    @Test void weightsPointsAndNormalizesToTen() { assertEquals(new BigDecimal("4.00"), QuizScorer.grade(questions, Map.of(1L, 11L), false)); }
    @Test void allCorrectGetsTen() { assertEquals(new BigDecimal("10.00"), QuizScorer.grade(questions, Map.of(1L, 11L, 2L, 22L), false)); }
    @Test void unansweredGetsZero() { assertEquals(new BigDecimal("0.00"), QuizScorer.grade(questions, Map.of(), false)); }
    @Test void expiredGetsZero() { assertEquals(new BigDecimal("0.00"), QuizScorer.grade(questions, Map.of(1L, 11L, 2L, 22L), true)); }
    @Test void rejectsForeignQuestion() { assertThrows(ResponseStatusException.class, () -> QuizScorer.grade(questions, Map.of(3L, 11L), false)); }
    @Test void rejectsOptionFromAnotherQuestion() { assertThrows(ResponseStatusException.class, () -> QuizScorer.grade(questions, Map.of(1L, 22L), false)); }
    @Test void hidesAnswersAndExplanationBeforeSubmit() {
        var attempt = new Attempt(1, 2, "Quiz", "IN_PROGRESS", Instant.now().plusSeconds(60), questions, Map.of(), null, BigDecimal.TEN, false);
        var view = QuizService.view(attempt);
        assertNull(view.score());
        assertTrue(view.questions().stream().allMatch(q -> q.explanation() == null && q.options().stream().allMatch(o -> o.correct() == null)));
    }
}
