package vn.edu.khtn8.ai;

public interface AiProvider {
    AiAnswer answer(AiQuestion question);
    String providerName();

    record ChatMessage(String role, String content) { }
    record AiQuestion(String question, String lessonContext, String mode, java.util.List<ChatMessage> history) {
        public AiQuestion(String question, String lessonContext, String mode) { this(question,lessonContext,mode,java.util.List.of()); }
    }
    record AiAnswer(String text, String[] sourceLabels, boolean needsTeacherReview) { }
}
