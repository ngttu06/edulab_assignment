package vn.edu.khtn8.content.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.khtn8.content.dto.LessonDetail;
import vn.edu.khtn8.content.dto.LessonSummary;

@Repository
public class LessonRepository {
    private final JdbcTemplate jdbc;
    public LessonRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<LessonSummary> findPublishedForStudent(long studentId) {
        return jdbc.query("""
                SELECT l.id, t.name topic_name, l.title, l.slug, l.summary, l.duration_minutes,
                       COALESCE(p.status, 'NOT_STARTED') progress_status, COALESCE(p.completion_percent, 0) completion_percent
                FROM lessons l JOIN topics t ON t.id = l.topic_id
                LEFT JOIN lesson_progress p ON p.lesson_id = l.id AND p.student_id = ?
                WHERE l.status = 'PUBLISHED'
                  AND EXISTS (SELECT 1 FROM lesson_versions v WHERE v.lesson_id = l.id AND v.status = 'PUBLISHED')
                ORDER BY t.display_order, l.id
                """, (rs, n) -> new LessonSummary(rs.getLong("id"), rs.getString("topic_name"), rs.getString("title"),
                rs.getString("slug"), rs.getString("summary"), rs.getInt("duration_minutes"),
                rs.getString("progress_status"), rs.getInt("completion_percent")), studentId);
    }

    public Optional<LessonDetail> findPublishedDetail(long lessonId, long studentId) {
        List<LessonDetail> rows = jdbc.query("""
                SELECT l.id, t.name topic_name, l.title, l.summary, l.duration_minutes,
                       v.objectives, v.content_html, COALESCE(p.status, 'NOT_STARTED') progress_status,
                       COALESCE(p.completion_percent, 0) completion_percent
                FROM lessons l JOIN topics t ON t.id = l.topic_id
                JOIN lesson_versions v ON v.lesson_id = l.id AND v.status = 'PUBLISHED'
                LEFT JOIN lesson_progress p ON p.lesson_id = l.id AND p.student_id = ?
                WHERE l.id = ? AND l.status = 'PUBLISHED'
                ORDER BY v.version_number DESC LIMIT 1
                """, (rs, n) -> new LessonDetail(rs.getLong("id"), rs.getString("topic_name"), rs.getString("title"),
                rs.getString("summary"), rs.getInt("duration_minutes"), rs.getString("objectives"),
                rs.getString("content_html"), rs.getString("progress_status"), rs.getInt("completion_percent")), studentId, lessonId);
        return rows.stream().findFirst();
    }

    public void upsertProgress(long studentId, long lessonId, String status, int percent) {
        jdbc.update("""
                INSERT INTO lesson_progress(student_id, lesson_id, status, completion_percent, started_at, completed_at)
                VALUES (?, ?, ?, ?, CASE WHEN ? = 'NOT_STARTED' THEN NULL ELSE CURRENT_TIMESTAMP(6) END,
                        CASE WHEN ? = 'COMPLETED' THEN CURRENT_TIMESTAMP(6) ELSE NULL END)
                ON DUPLICATE KEY UPDATE status = VALUES(status), completion_percent = VALUES(completion_percent),
                started_at = CASE WHEN VALUES(status) = 'NOT_STARTED' THEN NULL ELSE COALESCE(started_at, CURRENT_TIMESTAMP(6)) END,
                completed_at = CASE WHEN VALUES(status) = 'COMPLETED' THEN COALESCE(completed_at, CURRENT_TIMESTAMP(6)) ELSE NULL END
                """, studentId, lessonId, status, percent, status, status);
    }

    public Map<String, Object> progressSummary(long studentId) {
        return jdbc.queryForMap("""
                SELECT COUNT(l.id) total_lessons,
                       COALESCE(SUM(CASE WHEN p.status = 'COMPLETED' THEN 1 ELSE 0 END), 0) completed_lessons,
                       COALESCE(ROUND(AVG(COALESCE(p.completion_percent, 0))), 0) completion_percent
                FROM lessons l
                LEFT JOIN lesson_progress p ON p.lesson_id = l.id AND p.student_id = ?
                WHERE l.status = 'PUBLISHED'
                  AND EXISTS (SELECT 1 FROM lesson_versions v WHERE v.lesson_id = l.id AND v.status = 'PUBLISHED')
                """, studentId);
    }
}
