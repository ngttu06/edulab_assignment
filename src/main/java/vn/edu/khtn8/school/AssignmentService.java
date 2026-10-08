package vn.edu.khtn8.school;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import vn.edu.khtn8.common.jdbc.BooleanColumnMapRowMapper;

@Service
public class AssignmentService {
    private final JdbcTemplate jdbc;
    private final SchoolService school;
    public AssignmentService(JdbcTemplate jdbc,SchoolService school) {this.jdbc=jdbc;this.school=school;}
    public List<Map<String,Object>> forClass(AuthenticatedUser user,long classId) {
        school.students(user,classId);
        return jdbc.queryForList("""
                SELECT a.id,a.due_at,q.title,q.id quiz_id,
                    (SELECT COUNT(*) FROM class_enrollments e WHERE e.classroom_id=a.classroom_id AND e.active=TRUE) student_count,
                    (SELECT COUNT(DISTINCT x.student_id) FROM quiz_attempts x JOIN class_enrollments e ON e.student_id=x.student_id
                     WHERE e.classroom_id=a.classroom_id AND e.active=TRUE AND x.quiz_id=a.quiz_id AND x.status='GRADED'
                       AND x.submitted_at>=a.created_at AND x.submitted_at<=a.due_at) completed_count
                FROM class_assignments a JOIN quizzes q ON q.id=a.quiz_id WHERE a.classroom_id=? ORDER BY a.id DESC LIMIT 100
                """,classId);
    }
    public List<Map<String,Object>> forStudent(AuthenticatedUser user,long studentId) {
        school.authorizeStudent(user,studentId);
        return jdbc.query("""
                SELECT a.id,a.due_at,q.id quiz_id,q.title,c.name class_name,
                    EXISTS(SELECT 1 FROM quiz_attempts x WHERE x.student_id=? AND x.quiz_id=a.quiz_id AND x.status='GRADED'
                       AND x.submitted_at>=a.created_at AND x.submitted_at<=a.due_at) completed,
                    a.due_at<CURRENT_TIMESTAMP(6) overdue
                FROM class_assignments a JOIN quizzes q ON q.id=a.quiz_id JOIN classrooms c ON c.id=a.classroom_id
                JOIN class_enrollments e ON e.classroom_id=c.id AND e.student_id=? AND e.active=TRUE
                """ + (SchoolService.hasRole(user,"TEACHER") && !SchoolService.hasRole(user,"ADMIN") ? " WHERE EXISTS(SELECT 1 FROM class_teachers t WHERE t.classroom_id=c.id AND t.teacher_id="+user.id()+") " : "") + """
                ORDER BY a.due_at DESC LIMIT 100
                """,new BooleanColumnMapRowMapper("completed","overdue"),studentId,studentId);
    }
    @Transactional
    public void create(AuthenticatedUser user,long classId,long quizId,Instant dueAt) {
        school.students(user,classId);
        if(!dueAt.isAfter(Instant.now())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Hạn hoàn thành phải ở tương lai.");
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM quizzes q WHERE q.id=? AND q.published=TRUE AND (q.class_only=FALSE OR EXISTS(SELECT 1 FROM quiz_classes qc WHERE qc.quiz_id=q.id AND qc.classroom_id=?)))",Boolean.class,quizId,classId)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chỉ giao quiz đã xuất bản.");
        jdbc.update("INSERT INTO class_assignments(classroom_id,quiz_id,created_by,due_at) VALUES(?,?,?,?)",classId,quizId,user.id(),Timestamp.from(dueAt));
    }
    public List<Map<String,Object>> feedback(AuthenticatedUser user,long studentId) {
        school.authorizeStudent(user,studentId);
        return jdbc.queryForList("SELECT f.body,f.created_at,u.full_name teacher FROM teacher_feedback f JOIN users u ON u.id=f.teacher_id WHERE f.student_id=? ORDER BY f.id DESC LIMIT 50",studentId);
    }
    public void feedback(AuthenticatedUser user,long studentId,String body) {
        SchoolService.require(user,"TEACHER","ADMIN");school.authorizeStudent(user,studentId);
        jdbc.update("INSERT INTO teacher_feedback(student_id,teacher_id,body) VALUES(?,?,?)",studentId,user.id(),body.trim());
    }
}