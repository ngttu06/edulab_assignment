package vn.edu.khtn8.content.dto;

public record LessonSummary(Long id, String topicName, String title, String slug, String summary,
                            int durationMinutes, String progressStatus, int completionPercent,
                            Long courseId, String courseTitle, String subjectName, int gradeLevel) {
    public LessonSummary(Long id,String topicName,String title,String slug,String summary,int durationMinutes,String progressStatus,int completionPercent) {
        this(id,topicName,title,slug,summary,durationMinutes,progressStatus,completionPercent,null,"","",0);
    }
}
