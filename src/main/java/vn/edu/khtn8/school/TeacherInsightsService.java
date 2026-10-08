package vn.edu.khtn8.school;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.edu.khtn8.common.jdbc.BooleanColumnMapRowMapper;
import vn.edu.khtn8.identity.security.AuthenticatedUser;

/** Read-only aggregates; every class-scoped query is limited to assigned classes. */
@Service
public class TeacherInsightsService {
    private final JdbcTemplate jdbc;
    private final SchoolService school;
    public TeacherInsightsService(JdbcTemplate jdbc,SchoolService school) {this.jdbc=jdbc;this.school=school;}

    public Map<String,Object> overview(AuthenticatedUser user) {
        SchoolService.require(user,"TEACHER","ADMIN");
        var classes=school.classrooms(user);
        long students=jdbc.queryForObject("""
                SELECT COUNT(*) FROM class_enrollments e JOIN classrooms c ON c.id=e.classroom_id
                WHERE e.active=TRUE AND c.archived_at IS NULL AND (? OR EXISTS(
                    SELECT 1 FROM class_teachers t WHERE t.classroom_id=c.id AND t.teacher_id=?))
                """,Long.class,SchoolService.hasRole(user,"ADMIN"),user.id());
        long drafts=jdbc.queryForObject("SELECT COUNT(*) FROM lessons WHERE class_only=TRUE AND deleted_at IS NULL AND status='DRAFT' AND (? OR created_by=?)",Long.class,SchoolService.hasRole(user,"ADMIN"),user.id())
                +jdbc.queryForObject("SELECT COUNT(*) FROM quizzes WHERE class_only=TRUE AND deleted_at IS NULL AND published=FALSE AND (? OR created_by=?)",Long.class,SchoolService.hasRole(user,"ADMIN"),user.id());
        var upcoming=jdbc.queryForList("""
                SELECT a.id,a.classroom_id,c.name class_name,q.title,a.due_at,
                    (SELECT COUNT(*) FROM class_enrollments e WHERE e.classroom_id=c.id AND e.active=TRUE) student_count,
                    (SELECT COUNT(*) FROM class_enrollments e WHERE e.classroom_id=c.id AND e.active=TRUE AND EXISTS(
                        SELECT 1 FROM quiz_attempts x WHERE x.student_id=e.student_id AND x.quiz_id=a.quiz_id AND x.status='GRADED'
                        AND x.submitted_at>=a.created_at AND x.submitted_at<=a.due_at)) completed_count
                FROM class_assignments a JOIN classrooms c ON c.id=a.classroom_id JOIN quizzes q ON q.id=a.quiz_id
                WHERE c.archived_at IS NULL AND a.cancelled_at IS NULL AND a.due_at>CURRENT_TIMESTAMP(6)
                  AND (? OR EXISTS(SELECT 1 FROM class_teachers t WHERE t.classroom_id=c.id AND t.teacher_id=?))
                ORDER BY a.due_at,a.id LIMIT 6
                """,SchoolService.hasRole(user,"ADMIN"),user.id());
        return Map.of("classes",classes,"studentCount",students,"draftCount",drafts,"upcoming",upcoming);
    }

    public Map<String,Object> classroom(AuthenticatedUser user,long classId) {
        school.authorizeClass(user,classId);
        long students=jdbc.queryForObject("SELECT COUNT(*) FROM class_enrollments WHERE classroom_id=? AND active=TRUE",Long.class,classId);
        long lessons=jdbc.queryForObject("SELECT COUNT(*) FROM class_lessons cl JOIN lessons l ON l.id=cl.lesson_id WHERE cl.classroom_id=? AND l.status='PUBLISHED' AND l.deleted_at IS NULL",Long.class,classId);
        var recent=jdbc.queryForList("""
                SELECT a.id,q.title,a.due_at,a.cancelled_at FROM class_assignments a JOIN quizzes q ON q.id=a.quiz_id
                WHERE a.classroom_id=? ORDER BY a.id DESC LIMIT 5
                """,classId);
        return Map.of("studentCount",students,"lessonCount",lessons,"recent",recent);
    }

    public List<Map<String,Object>> assignments(AuthenticatedUser user) {
        SchoolService.require(user,"TEACHER","ADMIN");
        return jdbc.queryForList("""
                SELECT a.id,a.classroom_id,c.name class_name,q.title,a.due_at,a.cancelled_at,
                    (SELECT COUNT(*) FROM class_enrollments e WHERE e.classroom_id=c.id AND e.active=TRUE) student_count,
                    (SELECT COUNT(*) FROM class_enrollments e WHERE e.classroom_id=c.id AND e.active=TRUE AND EXISTS(
                        SELECT 1 FROM quiz_attempts x WHERE x.student_id=e.student_id AND x.quiz_id=a.quiz_id AND x.status='GRADED'
                        AND x.submitted_at>=a.created_at AND x.submitted_at<=a.due_at)) completed_count
                FROM class_assignments a JOIN classrooms c ON c.id=a.classroom_id JOIN quizzes q ON q.id=a.quiz_id
                WHERE (? OR EXISTS(SELECT 1 FROM class_teachers t WHERE t.classroom_id=c.id AND t.teacher_id=?))
                ORDER BY a.id DESC LIMIT 200
                """,SchoolService.hasRole(user,"ADMIN"),user.id());
    }

    public List<Map<String,Object>> submissionStatus(AuthenticatedUser user,long classId,long assignmentId) {
        school.authorizeClass(user,classId);
        return jdbc.query("""
                SELECT u.id,u.full_name,u.username,e.active,
                    EXISTS(SELECT 1 FROM quiz_attempts x WHERE x.student_id=u.id AND x.quiz_id=a.quiz_id AND x.status='GRADED'
                        AND x.submitted_at>=a.created_at AND x.submitted_at<=a.due_at) completed
                FROM class_assignments a JOIN class_enrollments e ON e.classroom_id=a.classroom_id
                JOIN users u ON u.id=e.student_id WHERE a.id=? AND a.classroom_id=?
                ORDER BY e.active DESC,u.full_name,u.id
                """,new BooleanColumnMapRowMapper("active","completed"),assignmentId,classId);
    }

    public List<Map<String,Object>> results(AuthenticatedUser user,long classId) {
        school.authorizeClass(user,classId);
        return jdbc.query("""
                SELECT u.id,u.full_name,u.username,e.active,q.id quiz_id,q.title,
                    COUNT(a.id) attempts,COUNT(CASE WHEN a.status='GRADED' THEN 1 END) graded_count,
                    MAX(CASE WHEN a.status='GRADED' THEN a.score END) best_score
                FROM class_enrollments e JOIN users u ON u.id=e.student_id
                JOIN quizzes q ON EXISTS(SELECT 1 FROM quiz_classes qc WHERE qc.quiz_id=q.id AND qc.classroom_id=e.classroom_id)
                    OR EXISTS(SELECT 1 FROM class_assignments ca WHERE ca.quiz_id=q.id AND ca.classroom_id=e.classroom_id)
                LEFT JOIN quiz_attempts a ON a.quiz_id=q.id AND a.student_id=u.id
                WHERE e.classroom_id=? GROUP BY u.id,u.full_name,u.username,e.active,q.id,q.title
                ORDER BY u.full_name,u.id,q.title,q.id
                """,new BooleanColumnMapRowMapper("active"),classId);
    }
}