package vn.edu.khtn8.content.service;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.content.dto.*;
import vn.edu.khtn8.content.repository.LessonRepository;

@Service
public class LessonService {
    private final LessonRepository lessons;
    public LessonService(LessonRepository lessons) { this.lessons = lessons; }
    public List<LessonSummary> list(long studentId) { return lessons.findPublishedForStudent(studentId); }
    public List<LessonSummary> list(long studentId,CatalogFilter filter) { return lessons.findPublishedForStudent(studentId,filter); }
    public LessonDetail detail(long studentId, long lessonId) {
        LessonDetail lesson = lessons.findPublishedDetail(lessonId, studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài học."));
        String safeHtml = org.jsoup.Jsoup.clean(lesson.contentHtml(), org.jsoup.safety.Safelist.basic().addTags("h2","h3","h4"));
        return new LessonDetail(lesson.id(), lesson.topicName(), lesson.title(), lesson.summary(),
                lesson.durationMinutes(), lesson.objectives(), safeHtml, lesson.progressStatus(), lesson.completionPercent(),
                lesson.courseId(),lesson.courseTitle(),lesson.subjectName(),lesson.gradeLevel(),lesson.hasVideo());
    }
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('STUDENT')")
    @org.springframework.transaction.annotation.Transactional
    public void updateProgress(long studentId, long lessonId, ProgressRequest request) {
        boolean valid = switch (request.status() == null ? "" : request.status()) {
            case "NOT_STARTED" -> request.completionPercent() == 0;
            case "IN_PROGRESS" -> request.completionPercent() >= 0 && request.completionPercent() < 100;
            case "COMPLETED" -> request.completionPercent() == 100;
            default -> false;
        };
        if (!valid)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái và phần trăm tiến độ không hợp lệ.");
        if (lessons.lockHasVideo(lessonId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bài học có video: tiến độ được lưu theo các đoạn đã xem, không thể đánh dấu thủ công.");
        detail(studentId, lessonId);
        lessons.upsertProgress(studentId, lessonId, request.status(), request.completionPercent());
    }

    public CourseStructure courseStructure(long studentId, long courseId) {
        return lessons.courseStructure(studentId, courseId);
    }

    public LessonNavigation lessonNavigation(long studentId, long courseId, long lessonId) {
        return lessons.lessonNavigation(studentId, courseId, lessonId);
    }
}
