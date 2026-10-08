package vn.edu.khtn8.content.service;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.common.jdbc.BooleanColumnMapRowMapper;

@Service
public class LearningPathService {
    private final JdbcTemplate jdbc;
    public LearningPathService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<Map<String,Object>> steps(long studentId) {
        return steps(studentId,vn.edu.khtn8.content.dto.CatalogFilter.all());
    }
    public List<Map<String,Object>> steps(long studentId,vn.edu.khtn8.content.dto.CatalogFilter filter) {
        var args=new java.util.ArrayList<Object>(List.of(studentId,studentId,studentId));
        args.addAll(java.util.Arrays.asList(filter.parameters()));
        return jdbc.query("""
                SELECT l.id,l.title,l.duration_minutes,t.name topic_name,c.id course_id,c.title course_title,s.name subject_name,s.grade_level,
                       COALESCE(p.status,'NOT_STARTED') progress_status, l.prerequisite_id,l.required_score,
                       (l.prerequisite_id IS NULL OR EXISTS(
                           SELECT 1 FROM lesson_progress x WHERE x.student_id=? AND x.lesson_id=l.prerequisite_id AND x.status='COMPLETED'
                       ) AND (l.required_score=0 OR EXISTS(
                           SELECT 1 FROM quiz_attempts a JOIN quizzes q ON q.id=a.quiz_id
                           WHERE a.student_id=? AND q.lesson_id=l.prerequisite_id AND a.status='GRADED' AND a.score>=l.required_score
                       ))) unlocked
                FROM lessons l JOIN topics t ON t.id=l.topic_id
                JOIN courses c ON c.id=t.course_id JOIN subjects s ON s.id=c.subject_id
                LEFT JOIN lesson_progress p ON p.lesson_id=l.id AND p.student_id=?
                WHERE l.status='PUBLISHED' AND EXISTS(SELECT 1 FROM lesson_versions v WHERE v.lesson_id=l.id AND v.status='PUBLISHED')
                """ + " AND " + vn.edu.khtn8.school.ContentAccess.lesson("l",studentId) + vn.edu.khtn8.content.dto.CatalogFilter.SQL + " AND l.title LIKE ? ESCAPE '!' " + """
                ORDER BY s.grade_level,c.id,t.display_order,l.id
                """,new BooleanColumnMapRowMapper("unlocked"),args.toArray());
    }
    public void requireUnlocked(long studentId,long lessonId) {
        var step = steps(studentId).stream().filter(s -> ((Number)s.get("id")).longValue()==lessonId).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy bài học."));
        if (!Boolean.TRUE.equals(step.get("unlocked")))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Hãy hoàn thành bài tiên quyết và quiz yêu cầu trước.");
    }

    public void requireQuizUnlocked(long studentId,long quizId) {
        var ids=jdbc.queryForList("SELECT lesson_id FROM quizzes WHERE id=? AND lesson_id IS NOT NULL",Long.class,quizId);
        if(!ids.isEmpty()) requireUnlocked(studentId,ids.getFirst());
    }
}