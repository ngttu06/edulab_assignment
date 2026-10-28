package vn.edu.khtn8.content.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.khtn8.content.dto.LessonDetail;
import vn.edu.khtn8.content.dto.LessonSummary;
import vn.edu.khtn8.content.dto.CatalogFilter;

@Repository
public class LessonRepository {
    private final JdbcTemplate jdbc;
    public LessonRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<LessonSummary> findPublishedForStudent(long studentId) {
        return findPublishedForStudent(studentId,CatalogFilter.all());
    }
    public List<LessonSummary> findPublishedForStudent(long studentId,CatalogFilter filter) {
        var args = new java.util.ArrayList<Object>();
        args.add(studentId); args.addAll(java.util.Arrays.asList(filter.parameters()));
        return jdbc.query("""
                SELECT l.id, t.name topic_name, l.title, l.slug, l.summary, l.duration_minutes,c.id course_id,c.title course_title,s.name subject_name,s.grade_level,l.video_id,
                       COALESCE(p.status, 'NOT_STARTED') progress_status, COALESCE(p.completion_percent, 0) completion_percent
                FROM lessons l JOIN topics t ON t.id = l.topic_id
                JOIN courses c ON c.id=t.course_id JOIN subjects s ON s.id=c.subject_id
                LEFT JOIN lesson_progress p ON p.lesson_id = l.id AND p.student_id = ?
                WHERE l.status = 'PUBLISHED'
                  AND EXISTS (SELECT 1 FROM lesson_versions v WHERE v.lesson_id = l.id AND v.status = 'PUBLISHED')
                """ + " AND " + vn.edu.khtn8.school.ContentAccess.lesson("l",studentId) + CatalogFilter.SQL + " AND CONCAT(l.title,' ',COALESCE(l.summary,''),' ',t.name) LIKE ? ESCAPE '!' " + """
                ORDER BY s.grade_level,c.id,t.display_order,l.display_order,l.id
                """, (rs, n) -> new LessonSummary(rs.getLong("id"), rs.getString("topic_name"), rs.getString("title"),
                rs.getString("slug"), rs.getString("summary"), rs.getInt("duration_minutes"),
                rs.getString("progress_status"), rs.getInt("completion_percent"),rs.getLong("course_id"),rs.getString("course_title"),rs.getString("subject_name"),rs.getInt("grade_level"),rs.getObject("video_id")!=null), args.toArray());
    }

    public Optional<LessonDetail> findPublishedDetail(long lessonId, long studentId) {
        List<LessonDetail> rows = jdbc.query("""
                SELECT l.id, t.name topic_name, l.title, l.summary, l.duration_minutes,c.id course_id,c.title course_title,s.name subject_name,s.grade_level,l.video_id,
                       v.objectives, v.content_html, COALESCE(p.status, 'NOT_STARTED') progress_status,
                       COALESCE(p.completion_percent, 0) completion_percent
                FROM lessons l JOIN topics t ON t.id = l.topic_id
                JOIN courses c ON c.id=t.course_id JOIN subjects s ON s.id=c.subject_id
                JOIN lesson_versions v ON v.lesson_id = l.id AND v.status = 'PUBLISHED'
                LEFT JOIN lesson_progress p ON p.lesson_id = l.id AND p.student_id = ?
                WHERE l.id = ? AND l.status = 'PUBLISHED'
                """ + " AND " + vn.edu.khtn8.school.ContentAccess.lesson("l",studentId) + """
                ORDER BY v.version_number DESC LIMIT 1
                """, (rs, n) -> new LessonDetail(rs.getLong("id"), rs.getString("topic_name"), rs.getString("title"),
                rs.getString("summary"), rs.getInt("duration_minutes"), rs.getString("objectives"),
                rs.getString("content_html"), rs.getString("progress_status"), rs.getInt("completion_percent"),rs.getLong("course_id"),rs.getString("course_title"),rs.getString("subject_name"),rs.getInt("grade_level"),rs.getObject("video_id")!=null), studentId, lessonId);
        return rows.stream().findFirst();
    }

    public boolean lockHasVideo(long lessonId) {
        return jdbc.query("SELECT video_id FROM lessons WHERE id=? FOR UPDATE", (rs,n) -> rs.getObject("video_id")!=null,lessonId)
                .stream().findFirst().orElse(false);
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
                """ + " AND " + vn.edu.khtn8.school.ContentAccess.lesson("l",studentId), studentId);
    }

    public vn.edu.khtn8.content.dto.CourseStructure courseStructure(long studentId, long courseId) {
        // Get course info
        var courseInfo = jdbc.queryForMap("""
            SELECT c.id, c.title, s.name subject_name, s.grade_level,
                   COUNT(DISTINCT l.id) total_lessons,
                   COALESCE(SUM(CASE WHEN p.status = 'COMPLETED' THEN 1 ELSE 0 END), 0) completed_lessons,
                   COALESCE(ROUND(AVG(COALESCE(p.completion_percent, 0))), 0) completion_percent
            FROM courses c
            JOIN subjects s ON s.id = c.subject_id
            JOIN topics t ON t.course_id = c.id
            JOIN lessons l ON l.topic_id = t.id AND l.status = 'PUBLISHED'
            LEFT JOIN lesson_progress p ON p.lesson_id = l.id AND p.student_id = ?
            WHERE c.id = ?
            GROUP BY c.id, c.title, s.name, s.grade_level
            """, studentId, courseId);

        // Get topics with lessons
        var topics = jdbc.query("""
            SELECT t.id topic_id, t.name topic_name, t.display_order,
                   COUNT(l.id) total_count,
                   COALESCE(SUM(CASE WHEN p.status = 'COMPLETED' THEN 1 ELSE 0 END), 0) completed_count
            FROM topics t
            LEFT JOIN lessons l ON l.topic_id = t.id AND l.status = 'PUBLISHED'
            LEFT JOIN lesson_progress p ON p.lesson_id = l.id AND p.student_id = ?
            WHERE t.course_id = ?
            GROUP BY t.id, t.name, t.display_order
            ORDER BY t.display_order, t.id
            """, (rs, n) -> {
                long topicId = rs.getLong("topic_id");
                String topicName = rs.getString("topic_name");
                int displayOrder = rs.getInt("display_order");
                int totalCount = rs.getInt("total_count");
                int completedCount = rs.getInt("completed_count");

                // Get lessons for this topic
                var lessons = jdbc.query("""
                    SELECT l.id, l.title, l.duration_minutes, l.video_id, l.prerequisite_id,
                           COALESCE(p.completion_percent, 0) completion_percent,
                           COALESCE(p.status, 'NOT_STARTED') status
                    FROM lessons l
                    LEFT JOIN lesson_progress p ON p.lesson_id = l.id AND p.student_id = ?
                    WHERE l.topic_id = ? AND l.status = 'PUBLISHED'
                    ORDER BY l.display_order, l.id
                    """, (lrs, ln) -> {
                        long lessonId = lrs.getLong("id");
                        String title = lrs.getString("title");
                        int duration = lrs.getInt("duration_minutes");
                        boolean hasVideo = lrs.getObject("video_id") != null;
                        Long prereqId = lrs.getObject("prerequisite_id", Long.class);
                        int completion = lrs.getInt("completion_percent");
                        String status = lrs.getString("status");

                        // Determine if locked (simplified - should check prerequisites properly)
                        boolean isLocked = false;
                        String lessonStatus = completion == 100 ? "completed"
                                            : completion > 0 ? "in_progress"
                                            : isLocked ? "locked" : "available";

                        return new vn.edu.khtn8.content.dto.CourseStructure.LessonItem(
                            lessonId, title, duration, completion, hasVideo, false,
                            isLocked, prereqId, lessonStatus
                        );
                    }, studentId, topicId);

                return new vn.edu.khtn8.content.dto.CourseStructure.TopicModule(
                    topicId, topicName, displayOrder, completedCount, totalCount, lessons
                );
            }, studentId, courseId);

        return new vn.edu.khtn8.content.dto.CourseStructure(
            ((Number)courseInfo.get("id")).longValue(),
            (String)courseInfo.get("title"),
            (String)courseInfo.get("subject_name"),
            ((Number)courseInfo.get("grade_level")).intValue(),
            ((Number)courseInfo.get("completion_percent")).intValue(),
            ((Number)courseInfo.get("completed_lessons")).intValue(),
            ((Number)courseInfo.get("total_lessons")).intValue(),
            topics
        );
    }

    public vn.edu.khtn8.content.dto.LessonNavigation lessonNavigation(long studentId, long courseId, long lessonId) {
        var lessons = jdbc.query("""
            SELECT l.id, l.title
            FROM lessons l
            JOIN topics t ON t.id = l.topic_id
            WHERE t.course_id = ? AND l.status = 'PUBLISHED'
            ORDER BY t.display_order, l.display_order, l.id
            """, (rs, n) -> Map.of("id", rs.getLong("id"), "title", rs.getString("title")), courseId);

        int currentIndex = -1;
        for (int i = 0; i < lessons.size(); i++) {
            if (((Number)lessons.get(i).get("id")).longValue() == lessonId) {
                currentIndex = i;
                break;
            }
        }

        Long prevId = currentIndex > 0 ? ((Number)lessons.get(currentIndex - 1).get("id")).longValue() : null;
        String prevTitle = currentIndex > 0 ? (String)lessons.get(currentIndex - 1).get("title") : null;
        Long nextId = currentIndex < lessons.size() - 1 ? ((Number)lessons.get(currentIndex + 1).get("id")).longValue() : null;
        String nextTitle = currentIndex < lessons.size() - 1 ? (String)lessons.get(currentIndex + 1).get("title") : null;

        return new vn.edu.khtn8.content.dto.LessonNavigation(
            prevId, prevTitle, nextId, nextTitle, currentIndex + 1, lessons.size()
        );
    }
}
