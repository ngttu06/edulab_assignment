package vn.edu.khtn8.ai;

public interface AiProvider {
    AiAnswer answer(AiQuestion question);
    String providerName();

    record AiQuestion(String question, String lessonContext, String mode) { }
    record AiAnswer(String text, String[] sourceLabels, boolean needsTeacherReview) { }
}
