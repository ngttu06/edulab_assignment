package vn.edu.khtn8.assessment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.assessment.QuizModels.Question;

public final class QuizScorer {
    private QuizScorer() { }

    public static BigDecimal grade(List<Question> questions, Map<Long, Long> answers, boolean expired) {
        if (answers == null) throw invalid();
        Set<Long> ids = questions.stream().map(Question::id).collect(Collectors.toSet());
        if (!ids.containsAll(answers.keySet())) throw invalid();
        BigDecimal earned = BigDecimal.ZERO;
        BigDecimal possible = BigDecimal.ZERO;
        for (Question question : questions) {
            possible = possible.add(question.points());
            Long selected = answers.get(question.id());
            if (answers.containsKey(question.id()) && selected == null) throw invalid();
            if (selected != null) {
                var option = question.options().stream().filter(o -> o.id() == selected).findFirst().orElseThrow(QuizScorer::invalid);
                if (!expired && option.correct()) earned = earned.add(question.points());
            }
        }
        if (possible.signum() <= 0) throw new IllegalStateException("Quiz must have positive total points");
        return earned.multiply(BigDecimal.TEN).divide(possible, 2, RoundingMode.HALF_UP);
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Câu hỏi hoặc phương án không thuộc lượt làm bài.");
    }
}
