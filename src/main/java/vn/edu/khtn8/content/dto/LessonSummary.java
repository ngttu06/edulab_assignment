package vn.edu.khtn8.content.dto;

public record LessonSummary(Long id, String topicName, String title, String slug, String summary,
                            int durationMinutes, String progressStatus, int completionPercent) { }
