package vn.edu.khtn8.content.dto;

public record LessonDetail(Long id, String topicName, String title, String summary, int durationMinutes,
                           String objectives, String contentHtml, String progressStatus, int completionPercent) { }
