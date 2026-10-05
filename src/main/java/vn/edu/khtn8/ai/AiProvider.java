package vn.edu.khtn8.ai;

public interface AiProvider {
    AiAnswer answer(AiQuestion question);
    String providerName();

    record ChatMessage(String role, String content) { }
    record Attachment(String filename,String contentType,String dataUrl) {
        @Override public String toString() { return "AiAttachment[redacted]"; }
    }
    record AiQuestion(String question, String lessonContext, String mode, java.util.List<ChatMessage> history,java.util.List<Attachment> attachments) {
        public AiQuestion(String question, String lessonContext, String mode) { this(question,lessonContext,mode,java.util.List.of()); }
        public AiQuestion(String question,String lessonContext,String mode,java.util.List<ChatMessage> history) { this(question,lessonContext,mode,history,java.util.List.of()); }
    }
    record AiAnswer(String text, String[] sourceLabels, boolean needsTeacherReview) { }
}
