package vn.edu.khtn8.content.dto;

public record LessonDetail(Long id, String topicName, String title, String summary, int durationMinutes,
                           String objectives, String contentHtml, String progressStatus, int completionPercent,
                           Long courseId, String courseTitle, String subjectName, int gradeLevel, boolean hasVideo) {
    public LessonDetail(Long id,String topicName,String title,String summary,int durationMinutes,String objectives,String contentHtml,String progressStatus,int completionPercent,Long courseId,String courseTitle,String subjectName,int gradeLevel) {
        this(id,topicName,title,summary,durationMinutes,objectives,contentHtml,progressStatus,completionPercent,courseId,courseTitle,subjectName,gradeLevel,false);
    }
    public LessonDetail(Long id,String topicName,String title,String summary,int durationMinutes,String objectives,String contentHtml,String progressStatus,int completionPercent) {
        this(id,topicName,title,summary,durationMinutes,objectives,contentHtml,progressStatus,completionPercent,null,"","",0);
    }
}
