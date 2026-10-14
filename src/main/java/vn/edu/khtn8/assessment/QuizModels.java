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
    public record Quiz(long id, String title, int durationMinutes, int maxAttempts, int questionCount,String mode,Instant revealAfter) {
        public Quiz(long id,String title,int durationMinutes,int maxAttempts,int questionCount){this(id,title,durationMinutes,maxAttempts,questionCount,"PRACTICE",null);}
    }
    public record Attempt(long id, long studentId, String title, String status, Instant deadline,
                          List<Question> questions, Map<Long, Long> answers, BigDecimal score, BigDecimal maxScore, boolean expired,String mode,Instant revealAfter) {
        public Attempt(long id,long studentId,String title,String status,Instant deadline,List<Question> questions,Map<Long,Long> answers,BigDecimal score,BigDecimal maxScore,boolean expired){this(id,studentId,title,status,deadline,questions,answers,score,maxScore,expired,"PRACTICE",null);}
    }
    public record OptionView(long id, String text, Boolean correct) { }
    public record QuestionView(long id, String text, String explanation, BigDecimal points,
                               List<OptionView> options, Long selectedOption) {
        public String outcome() {
            if(options.stream().allMatch(o -> o.correct()==null)) return "HIDDEN";
            if(selectedOption==null) return "UNANSWERED";
            return options.stream().anyMatch(o -> o.id()==selectedOption && Boolean.TRUE.equals(o.correct())) ? "CORRECT" : "WRONG";
        }
    }
    public record AttemptView(long id, String title, String status, Instant deadline,
                              List<QuestionView> questions, BigDecimal score, BigDecimal maxScore, boolean expired,
                              String mode, Instant revealAfter, boolean answersVisible) {
        public long correctCount() {return questions.stream().filter(q -> q.outcome().equals("CORRECT")).count();}
        public long wrongCount() {return questions.stream().filter(q -> q.outcome().equals("WRONG")).count();}
        public long unansweredCount() {return questions.stream().filter(q -> q.selectedOption()==null).count();}
    }
    public record Submission(@NotNull @Size(max = 200) Map<Long, @NotNull Long> answers) { }
}
