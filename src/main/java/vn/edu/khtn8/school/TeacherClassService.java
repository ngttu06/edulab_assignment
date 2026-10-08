package vn.edu.khtn8.school;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.common.jdbc.BooleanColumnMapRowMapper;
import vn.edu.khtn8.identity.security.AuthenticatedUser;

@Service
public class TeacherClassService {
    private final JdbcTemplate jdbc;
    private final SchoolService school;
    public TeacherClassService(JdbcTemplate jdbc,SchoolService school) {this.jdbc=jdbc;this.school=school;}
    public List<Long> assignableQuizIds(AuthenticatedUser user,long classId) {
        school.authorizeClass(user,classId);
        return jdbc.queryForList("SELECT q.id FROM quizzes q WHERE q.published=TRUE AND (q.class_only=FALSE OR EXISTS(SELECT 1 FROM quiz_classes qc WHERE qc.quiz_id=q.id AND qc.classroom_id=?))",Long.class,classId);
    }
    public Map<String,Object> roster(AuthenticatedUser user,long classId,String search,int page) {
        school.authorizeClass(user,classId);
        if(page<0 || page>100000 || search.length()>100) throw bad("Bộ lọc không hợp lệ.");
        String term="%"+search.trim().replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        String filter=" FROM class_enrollments e JOIN users u ON u.id=e.student_id WHERE e.classroom_id=? AND (u.full_name LIKE ? ESCAPE '!' OR u.username LIKE ? ESCAPE '!')";
        var students=jdbc.query("""
                SELECT u.id,u.username,u.full_name,e.active,
                    (SELECT COALESCE(ROUND(AVG(COALESCE(p.completion_percent,0))),0)
                     FROM class_lessons cl JOIN lessons l ON l.id=cl.lesson_id AND l.status='PUBLISHED'
                     LEFT JOIN lesson_progress p ON p.lesson_id=l.id AND p.student_id=u.id
                     WHERE cl.classroom_id=e.classroom_id) completion_percent
                """+filter+" ORDER BY e.active DESC,u.full_name,u.id LIMIT 25 OFFSET ?",new BooleanColumnMapRowMapper("active"),classId,term,term,page*25);
        return Map.of("classroom",jdbc.queryForMap("SELECT id,name FROM classrooms WHERE id=?",classId),"students",students,
                "total",jdbc.queryForObject("SELECT COUNT(*)"+filter,Long.class,classId,term,term),"page",page,"search",search,
                "lessons",jdbc.queryForList("SELECT l.id,l.title,(SELECT COUNT(*) FROM lesson_progress p JOIN class_enrollments e ON e.student_id=p.student_id AND e.classroom_id=? AND e.active=TRUE WHERE p.lesson_id=l.id AND p.status='COMPLETED') completed FROM lessons l JOIN class_lessons cl ON cl.lesson_id=l.id WHERE cl.classroom_id=? AND l.status='PUBLISHED' ORDER BY l.id",classId,classId),
                "quizzes",jdbc.queryForList("""
                    SELECT q.id,q.title,
                        (SELECT COUNT(DISTINCT a.student_id) FROM quiz_attempts a
                         JOIN class_enrollments e ON e.student_id=a.student_id AND e.classroom_id=? AND e.active=TRUE
                         WHERE a.quiz_id=q.id AND a.status='GRADED') completed
                    FROM quizzes q WHERE q.published=TRUE AND (
                        EXISTS(SELECT 1 FROM quiz_classes qc WHERE qc.quiz_id=q.id AND qc.classroom_id=?) OR
                        EXISTS(SELECT 1 FROM class_assignments ca WHERE ca.quiz_id=q.id AND ca.classroom_id=?))
                    ORDER BY q.id DESC
                    """,classId,classId,classId));
    }
    @Transactional
    public void enroll(AuthenticatedUser user,long classId,String username) {
        school.requireActiveClass(user,classId);
        var ids=jdbc.queryForList("SELECT id FROM users WHERE username=? AND role='STUDENT' AND enabled=TRUE",Long.class,username.trim());
        if(ids.isEmpty()) throw bad("Không tìm thấy tài khoản học sinh đang hoạt động với tên đăng nhập này.");
        jdbc.update("INSERT INTO class_enrollments(classroom_id,student_id) VALUES(?,?) ON DUPLICATE KEY UPDATE active=TRUE",classId,ids.getFirst());
        audit(user,classId,ids.getFirst(),true);
    }
    @Transactional
    public void membership(AuthenticatedUser user,long classId,long studentId,boolean active) {
        school.requireActiveClass(user,classId);
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM class_enrollments WHERE classroom_id=? AND student_id=?)",Boolean.class,classId,studentId))) throw bad("Học sinh không thuộc lớp này.");
        if(active && !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE id=? AND role='STUDENT' AND enabled=TRUE)",Boolean.class,studentId))) throw bad("Tài khoản học sinh không hoạt động.");
        jdbc.update("UPDATE class_enrollments SET active=? WHERE classroom_id=? AND student_id=?",active,classId,studentId);audit(user,classId,studentId,active);
    }
    private void audit(AuthenticatedUser user,long classId,long studentId,boolean active) {
        jdbc.update("INSERT INTO audit_logs(actor_user_id,action,resource_type,resource_id) VALUES(?,?,?,?)",user.id(),active?"ENROLL_STUDENT":"REMOVE_STUDENT","class_enrollment",classId+":"+studentId);
    }
    public Map<String,Object> report(AuthenticatedUser user,long classId,long studentId) {
        school.authorizeClass(user,classId);
        member(user,classId,studentId);
        var lessons=jdbc.queryForList("""
                SELECT l.id,l.title,COALESCE(p.completion_percent,0) completion_percent,COALESCE(p.status,'NOT_STARTED') status,p.completed_at
                FROM class_lessons cl JOIN lessons l ON l.id=cl.lesson_id
                LEFT JOIN lesson_progress p ON p.lesson_id=l.id AND p.student_id=?
                WHERE cl.classroom_id=? AND (l.status='PUBLISHED' OR p.student_id IS NOT NULL) ORDER BY l.id
                """,studentId,classId);
        var quizzes=jdbc.queryForList("""
                SELECT q.id,q.title,COUNT(a.id) attempts,COUNT(CASE WHEN a.status='GRADED' THEN 1 END) graded_count,
                    MAX(CASE WHEN a.status='GRADED' THEN a.score END) best_score,
                    (SELECT x.score FROM quiz_attempts x WHERE x.quiz_id=q.id AND x.student_id=? AND x.status='GRADED' ORDER BY x.id DESC LIMIT 1) latest_score,
                    CASE WHEN COUNT(a.id)=0 THEN 'NOT_STARTED' WHEN SUM(a.status='IN_PROGRESS')>0 THEN 'IN_PROGRESS' ELSE 'GRADED' END status
                FROM quizzes q LEFT JOIN quiz_attempts a ON a.quiz_id=q.id AND a.student_id=?
                WHERE EXISTS(SELECT 1 FROM quiz_classes qc WHERE qc.quiz_id=q.id AND qc.classroom_id=?)
                   OR EXISTS(SELECT 1 FROM class_assignments ca WHERE ca.quiz_id=q.id AND ca.classroom_id=?)
                GROUP BY q.id,q.title ORDER BY q.id DESC
                """,studentId,studentId,classId,classId);
        var attempts=jdbc.query("""
                SELECT a.id,a.quiz_id,a.title_snapshot title,a.attempt_number,a.status,a.score,a.submitted_at,a.expired
                FROM quiz_attempts a WHERE a.student_id=? AND (
                    EXISTS(SELECT 1 FROM quiz_classes qc WHERE qc.quiz_id=a.quiz_id AND qc.classroom_id=?) OR
                    EXISTS(SELECT 1 FROM class_assignments ca WHERE ca.quiz_id=a.quiz_id AND ca.classroom_id=?))
                ORDER BY a.id DESC LIMIT 200
                """,new BooleanColumnMapRowMapper("expired"),studentId,classId,classId);
        return Map.of("student",jdbc.queryForMap("SELECT id,full_name,username FROM users WHERE id=?",studentId),"lessons",lessons,"quizzes",quizzes,"attempts",attempts,
                "completed",lessons.stream().filter(l->"COMPLETED".equals(l.get("status"))).count());
    }
    private ResponseStatusException bad(String message) {return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    public Map<String,Object> member(AuthenticatedUser user,long classId,long studentId) {
        school.authorizeClass(user,classId);
        var rows=jdbc.query("SELECT u.id,u.username,u.full_name,e.active,c.archived_at FROM class_enrollments e JOIN users u ON u.id=e.student_id JOIN classrooms c ON c.id=e.classroom_id WHERE e.classroom_id=? AND e.student_id=?",new BooleanColumnMapRowMapper("active"),classId,studentId);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Học sinh không thuộc lớp này.");
        return rows.getFirst();
    }
}