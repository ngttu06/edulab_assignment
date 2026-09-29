package vn.edu.khtn8.assessment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class QuizModels {
    private QuizModels() { }
    public record Option(long id, String text, boolean correct) { }
    public record Question(long id, String text, String explanation, BigDecimal points, List<Option> options) { }
    public record Quiz(long id, String title, int durationMinutes, int maxAttempts, int questionCount) { }
    public record Attempt(long id, long studentId, String title, String status, Instant deadline,
                          List<Question> questions, Map<Long, Long> answers, BigDecimal score, BigDecimal maxScore, boolean expired) { }
    public record OptionView(long id, String text, Boolean correct) { }
    public record QuestionView(long id, String text, String explanation, BigDecimal points,
                               List<OptionView> options, Long selectedOption) { }
    public record AttemptView(long id, String title, String status, Instant deadline,
                              List<QuestionView> questions, BigDecimal score, BigDecimal maxScore, boolean expired) { }
    public record Submission(@NotNull @Size(max = 200) Map<Long, @NotNull Long> answers) { }
}
