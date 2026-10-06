package vn.edu.khtn8.content.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.content.dto.CatalogFilter;

@Service
public class CatalogService {
    private final JdbcTemplate jdbc;
    public CatalogService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String,Object>> subjects() {
        return jdbc.queryForList("SELECT id,name,grade_level FROM subjects ORDER BY name,grade_level,id");
    }
    public List<Integer> grades() { return jdbc.queryForList("SELECT DISTINCT grade_level FROM subjects ORDER BY grade_level", Integer.class); }

    public Map<String,Object> publicStatistics() {
        return jdbc.queryForMap("""
                SELECT (SELECT COUNT(*) FROM courses) courses,
                    (SELECT COUNT(*) FROM subjects WHERE EXISTS(SELECT 1 FROM courses c WHERE c.subject_id=subjects.id)) subjects,
                    (SELECT COUNT(*) FROM lessons l JOIN topics t ON t.id=l.topic_id JOIN courses c ON c.id=t.course_id
                        WHERE l.status='PUBLISHED' AND EXISTS(SELECT 1 FROM lesson_versions v WHERE v.lesson_id=l.id AND v.status='PUBLISHED')) lessons,
                    (SELECT COUNT(DISTINCT c.teacher_id) FROM courses c JOIN users u ON u.id=c.teacher_id WHERE u.enabled=TRUE) teachers
                """);
    }
    public List<Map<String,Object>> publicTeachers() {
        return jdbc.queryForList("""
                SELECT u.full_name name,MIN(c.id) course_id,COUNT(DISTINCT c.id) course_count,
                    GROUP_CONCAT(DISTINCT s.name ORDER BY s.name SEPARATOR ' · ') subjects
                FROM courses c JOIN users u ON u.id=c.teacher_id JOIN subjects s ON s.id=c.subject_id
                WHERE u.enabled=TRUE GROUP BY u.id,u.full_name ORDER BY u.full_name,u.id LIMIT 8
                """);
    }

    public List<Map<String,Object>> courses(long studentId, CatalogFilter filter) {
        var args = new ArrayList<Object>();
        args.add(studentId); args.addAll(Arrays.asList(filter.parameters()));
        return jdbc.queryForList("""
                SELECT c.*,s.name subject_name,s.grade_level,u.full_name teacher_name,
                    COUNT(l.id) lesson_count,COALESCE(SUM(l.duration_minutes),0) duration_minutes,
                    COALESCE(SUM(p.status='COMPLETED'),0) completed_lessons,
                    COALESCE(ROUND(AVG(COALESCE(p.completion_percent,0))),0) completion_percent
                FROM courses c JOIN subjects s ON s.id=c.subject_id LEFT JOIN users u ON u.id=c.teacher_id
                LEFT JOIN topics t ON t.course_id=c.id
                LEFT JOIN lessons l ON l.topic_id=t.id AND l.status='PUBLISHED'
                    AND EXISTS(SELECT 1 FROM lesson_versions v WHERE v.lesson_id=l.id AND v.status='PUBLISHED')
                LEFT JOIN lesson_progress p ON p.lesson_id=l.id AND p.student_id=?
                WHERE 1=1
                """ + CatalogFilter.SQL + " AND CONCAT(c.title,' ',c.summary,' ',s.name) LIKE ? ESCAPE '!' " + """
                GROUP BY c.id,s.id,u.id ORDER BY s.grade_level,c.id LIMIT 60
                """, args.toArray());
    }
    public Map<String,Object> course(long studentId,long id) {
        return courses(studentId,new CatalogFilter(null,null,id,"")).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy khóa học."));
    }
    public Map<String,Object> lessonContext(long lessonId) {
        return jdbc.queryForMap("""
                SELECT c.id course_id,c.title course_title,s.name subject_name,s.grade_level
                FROM lessons l JOIN topics t ON t.id=l.topic_id JOIN courses c ON c.id=t.course_id
                JOIN subjects s ON s.id=c.subject_id WHERE l.id=?
                """,lessonId);
    }
    public List<Long> quizIds(CatalogFilter filter) {
        return jdbc.queryForList("""
                SELECT q.id FROM quizzes q LEFT JOIN courses c ON c.id=q.course_id
                LEFT JOIN subjects s ON s.id=c.subject_id WHERE 1=1
                """ + CatalogFilter.SQL + " AND q.title LIKE ? ESCAPE '!'",Long.class,filter.parameters());
    }
}