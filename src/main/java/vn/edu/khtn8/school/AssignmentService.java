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
                SELECT a.id,a.due_at,a.cancelled_at,q.title,q.id quiz_id,
                    (SELECT COUNT(*) FROM class_enrollments e WHERE e.classroom_id=a.classroom_id AND e.active=TRUE) student_count,
                    (SELECT COUNT(*) FROM class_enrollments e WHERE e.classroom_id=a.classroom_id AND e.active=TRUE AND EXISTS(
                        SELECT 1 FROM quiz_attempts x WHERE x.student_id=e.student_id AND x.quiz_id=a.quiz_id AND x.status='GRADED'
                        AND x.submitted_at>=a.created_at AND x.submitted_at<=a.due_at)) completed_count
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
                JOIN class_enrollments e ON e.classroom_id=c.id AND e.student_id=? AND e.active=TRUE AND a.cancelled_at IS NULL AND c.archived_at IS NULL
                """ + (SchoolService.hasRole(user,"TEACHER") && !SchoolService.hasRole(user,"ADMIN") ? " WHERE EXISTS(SELECT 1 FROM class_teachers t WHERE t.classroom_id=c.id AND t.teacher_id="+user.id()+") " : "") + """
                ORDER BY a.due_at DESC LIMIT 100
                """,new BooleanColumnMapRowMapper("completed","overdue"),studentId,studentId);
    }
    @Transactional
    public void create(AuthenticatedUser user,long classId,long quizId,Instant dueAt) {
        SchoolService.require(user,"TEACHER","ADMIN");
        // Serialize with quiz deletion so no live assignment can be inserted after cancellation.
        jdbc.queryForList("SELECT id FROM quizzes WHERE id=? FOR UPDATE",quizId);
        school.requireActiveClass(user,classId);
        if(dueAt==null || !dueAt.isAfter(Instant.now())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Hạn hoàn thành phải ở tương lai.");
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM quizzes q WHERE q.id=? AND q.published=TRUE AND q.deleted_at IS NULL AND (q.class_only=FALSE OR EXISTS(SELECT 1 FROM quiz_classes qc WHERE qc.quiz_id=q.id AND qc.classroom_id=?)))",Boolean.class,quizId,classId)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chỉ giao quiz đã xuất bản.");
        jdbc.update("INSERT INTO class_assignments(classroom_id,quiz_id,created_by,due_at) VALUES(?,?,?,?)",classId,quizId,user.id(),Timestamp.from(dueAt));
    }
    public List<Map<String,Object>> feedback(AuthenticatedUser user,long studentId) {
        school.authorizeStudent(user,studentId);
        return jdbc.queryForList("SELECT f.id,f.teacher_id,f.body,f.created_at,f.updated_at,u.full_name teacher FROM teacher_feedback f JOIN users u ON u.id=f.teacher_id WHERE f.student_id=? AND f.deleted_at IS NULL ORDER BY f.id DESC LIMIT 50",studentId);
    }
    public void feedback(AuthenticatedUser user,long studentId,String body) {
        SchoolService.require(user,"TEACHER","ADMIN");school.authorizeStudent(user,studentId);
        validateBody(body);
        jdbc.update("INSERT INTO teacher_feedback(student_id,teacher_id,body) VALUES(?,?,?)",studentId,user.id(),body.trim());
    }
    public Map<String,Object> assignment(AuthenticatedUser user,long classId,long id) {
        school.authorizeClass(user,classId);
        var rows=jdbc.queryForList("SELECT a.*,q.title FROM class_assignments a JOIN quizzes q ON q.id=a.quiz_id WHERE a.id=? AND a.classroom_id=?",id,classId);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy bài giao.");
        return rows.getFirst();
    }
    private void lockAssignment(AuthenticatedUser user,long classId,long id) {
        school.requireActiveClass(user,classId);
        var rows=jdbc.queryForList("SELECT cancelled_at FROM class_assignments WHERE id=? AND classroom_id=? FOR UPDATE",id,classId);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy bài giao.");
        if(rows.getFirst().get("cancelled_at")!=null) throw new ResponseStatusException(HttpStatus.CONFLICT,"Bài giao đã hủy.");
    }
    @Transactional
    public void updateAssignment(AuthenticatedUser user,long classId,long id,Instant dueAt) {
        lockAssignment(user,classId,id);
        if(dueAt==null || !dueAt.isAfter(Instant.now())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Hạn hoàn thành phải ở tương lai.");
        jdbc.update("UPDATE class_assignments SET due_at=? WHERE id=?",Timestamp.from(dueAt),id);
        school.audit(user,"UPDATE_ASSIGNMENT","assignment",id);
    }
    @Transactional
    public void cancelAssignment(AuthenticatedUser user,long classId,long id) {
        lockAssignment(user,classId,id);
        jdbc.update("UPDATE class_assignments SET cancelled_at=CURRENT_TIMESTAMP(6) WHERE id=?",id);
        school.audit(user,"CANCEL_ASSIGNMENT","assignment",id);
    }
    public Map<String,Object> feedbackDetail(AuthenticatedUser user,long studentId,long id) {
        SchoolService.require(user,"TEACHER","ADMIN");school.authorizeStudent(user,studentId);
        var rows=jdbc.queryForList("SELECT f.*,u.full_name teacher FROM teacher_feedback f JOIN users u ON u.id=f.teacher_id WHERE f.id=? AND f.student_id=? AND f.deleted_at IS NULL",id,studentId);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy nhận xét.");
        return rows.getFirst();
    }
    private Map<String,Object> ownedFeedback(AuthenticatedUser user,long studentId,long id) {
        feedbackDetail(user,studentId,id);
        var row=jdbc.queryForMap("SELECT * FROM teacher_feedback WHERE id=? FOR UPDATE",id);
        if(row.get("deleted_at")!=null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy nhận xét.");
        if(!SchoolService.hasRole(user,"ADMIN") && ((Number)row.get("teacher_id")).longValue()!=user.id())
            throw new org.springframework.security.access.AccessDeniedException("Not feedback author");
        return row;
    }
    private void validateBody(String body) {
        if(body==null || body.isBlank() || body.length()>2000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nhận xét cần 1–2000 ký tự.");
    }
    @Transactional
    public void updateFeedback(AuthenticatedUser user,long studentId,long id,String body) {
        validateBody(body);var row=ownedFeedback(user,studentId,id);
        jdbc.update("INSERT INTO teacher_feedback_revisions(feedback_id,actor_id,body) VALUES(?,?,?)",id,user.id(),row.get("body"));
        jdbc.update("UPDATE teacher_feedback SET body=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",body.trim(),id);
        school.audit(user,"UPDATE_FEEDBACK","feedback",id);
    }
    @Transactional
    public void deleteFeedback(AuthenticatedUser user,long studentId,long id) {
        ownedFeedback(user,studentId,id);
        jdbc.update("UPDATE teacher_feedback SET deleted_at=CURRENT_TIMESTAMP(6) WHERE id=?",id);
        school.audit(user,"DELETE_FEEDBACK","feedback",id);
    }

    public List<vn.edu.khtn8.content.dto.AssignmentSummary> studentAssignments(long studentId, String status, Long courseId) {
        var sql = new StringBuilder("""
            SELECT a.id assignment_id, a.quiz_id, q.title, c.name class_name, a.due_at,
                   co.title course_name, co.id course_id,
                   EXISTS(SELECT 1 FROM quiz_attempts x WHERE x.student_id=? AND x.quiz_id=a.quiz_id
                          AND x.status='GRADED' AND x.submitted_at>=a.created_at AND x.submitted_at<=a.due_at) completed,
                   a.due_at<CURRENT_TIMESTAMP(6) overdue,
                   (SELECT MAX(x.score) FROM quiz_attempts x WHERE x.student_id=? AND x.quiz_id=a.quiz_id
                    AND x.status='GRADED' AND x.submitted_at>=a.created_at AND x.submitted_at<=a.due_at) score,
                   (SELECT MAX(x.submitted_at) FROM quiz_attempts x WHERE x.student_id=? AND x.quiz_id=a.quiz_id
                    AND x.status='GRADED' AND x.submitted_at>=a.created_at AND x.submitted_at<=a.due_at) submitted_at
            FROM class_assignments a
            JOIN quizzes q ON q.id=a.quiz_id
            JOIN classrooms c ON c.id=a.classroom_id
            LEFT JOIN courses co ON co.id=q.course_id
            JOIN class_enrollments e ON e.classroom_id=c.id AND e.student_id=? AND e.active=TRUE
            WHERE a.cancelled_at IS NULL AND c.archived_at IS NULL
            """);

        var params = new java.util.ArrayList<>();
        params.add(studentId);
        params.add(studentId);
        params.add(studentId);
        params.add(studentId);

        if (courseId != null) {
            sql.append(" AND co.id=?");
            params.add(courseId);
        }

        sql.append(" ORDER BY a.due_at DESC LIMIT 100");

        return jdbc.query(sql.toString(), (rs, n) -> {
            boolean completed = rs.getBoolean("completed");
            boolean overdue = rs.getBoolean("overdue");
            String assignmentStatus = completed ? "completed" : (overdue ? "overdue" : "todo");

            // Filter by status if specified
            if (status != null && !status.equals("all") && !status.equals(assignmentStatus)) {
                return null;
            }

            return new vn.edu.khtn8.content.dto.AssignmentSummary(
                rs.getLong("assignment_id"),
                rs.getLong("quiz_id"),
                rs.getString("title"),
                rs.getString("course_name") != null ? rs.getString("course_name") : "Không rõ khóa học",
                rs.getString("class_name"),
                rs.getTimestamp("due_at").toInstant(),
                completed,
                overdue,
                assignmentStatus,
                rs.getObject("score") != null ? ((Number)rs.getObject("score")).intValue() : null,
                rs.getTimestamp("submitted_at") != null ? rs.getTimestamp("submitted_at").toInstant() : null
            );
        }, params.toArray()).stream().filter(java.util.Objects::nonNull).toList();
    }
}