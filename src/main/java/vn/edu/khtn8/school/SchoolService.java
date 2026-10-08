package vn.edu.khtn8.school;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.content.repository.LessonRepository;
import vn.edu.khtn8.identity.security.AuthenticatedUser;

@Service
public class SchoolService {
    private final JdbcTemplate jdbc;
    private final LessonRepository lessons;
    private final SecureRandom random = new SecureRandom();
    public SchoolService(JdbcTemplate jdbc, LessonRepository lessons) { this.jdbc = jdbc; this.lessons = lessons; }

    public static boolean hasRole(AuthenticatedUser user, String role) {
        return user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
    public static void require(AuthenticatedUser user, String... roles) {
        for (String role : roles) if (hasRole(user, role)) return;
        throw new AccessDeniedException("Role not allowed");
    }
    private boolean exists(String sql, Object... args) { return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, args)); }

    public List<Map<String, Object>> classrooms(AuthenticatedUser user) {
        require(user, "TEACHER", "ADMIN");
        return jdbc.queryForList("""
                SELECT c.id,c.name,y.name academic_year FROM classrooms c JOIN academic_years y ON y.id=c.academic_year_id
                WHERE ? OR EXISTS(SELECT 1 FROM class_teachers t WHERE t.classroom_id=c.id AND t.teacher_id=?) ORDER BY c.id
                """, hasRole(user, "ADMIN"), user.id());
    }
    public List<Map<String, Object>> students(AuthenticatedUser user, long classId) {
        authorizeClass(user,classId);
        return jdbc.queryForList("""
                SELECT u.id,u.full_name FROM class_enrollments e JOIN users u ON u.id=e.student_id
                WHERE e.classroom_id=? AND e.active=TRUE AND u.role='STUDENT' ORDER BY u.full_name,u.id
                """, classId);
    }
    public void authorizeClass(AuthenticatedUser user,long classId) {
        require(user, "TEACHER", "ADMIN");
        if (!hasRole(user, "ADMIN") && !exists("SELECT EXISTS(SELECT 1 FROM class_teachers WHERE classroom_id=? AND teacher_id=?)", classId, user.id()))
            throw new AccessDeniedException("Class not assigned");
        if(!exists("SELECT EXISTS(SELECT 1 FROM classrooms WHERE id=?)",classId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy lớp học.");
    }
    public void authorizeStudent(AuthenticatedUser user, long studentId) {
        if (hasRole(user, "ADMIN") || (hasRole(user, "STUDENT") && user.id() == studentId)) return;
        if (hasRole(user, "PARENT") && exists("SELECT EXISTS(SELECT 1 FROM parent_student_links WHERE parent_id=? AND student_id=? AND status='VERIFIED')", user.id(), studentId)) return;
        if (hasRole(user, "TEACHER") && exists("""
                SELECT EXISTS(SELECT 1 FROM class_teachers t JOIN class_enrollments e ON e.classroom_id=t.classroom_id
                WHERE t.teacher_id=? AND e.student_id=? AND e.active=TRUE)
                """, user.id(), studentId)) return;
        throw new AccessDeniedException("Student not linked");
    }
    public Map<String, Object> report(AuthenticatedUser user, long studentId) {
        authorizeStudent(user, studentId);
        var students = jdbc.queryForList("SELECT id,full_name FROM users WHERE id=? AND role='STUDENT'", studentId);
        if (students.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy học sinh.");
        var visibleLessons=lessons.findPublishedForStudent(studentId);
        String attemptScope="";
        if(hasRole(user,"TEACHER") && !hasRole(user,"ADMIN")) {
            var ids=new java.util.HashSet<>(jdbc.queryForList("SELECT l.id FROM lessons l WHERE "+ContentAccess.lesson("l",user.id()),Long.class));
            visibleLessons=visibleLessons.stream().filter(l->ids.contains(l.id())).toList();
            attemptScope=" AND "+ContentAccess.quiz("q",user.id());
        }
        var summary=Map.of("total_lessons",visibleLessons.size(),"completed_lessons",visibleLessons.stream().filter(l->"COMPLETED".equals(l.progressStatus())).count(),
                "completion_percent",Math.round(visibleLessons.stream().mapToInt(l->l.completionPercent()).average().orElse(0)));
        return Map.of("student", students.getFirst(), "summary", summary, "lessons", visibleLessons,
                "attempts", jdbc.query("SELECT a.id,a.title_snapshot title,a.score,a.submitted_at,a.expired FROM quiz_attempts a JOIN quizzes q ON q.id=a.quiz_id WHERE a.student_id=? AND a.status='GRADED'"+attemptScope+" ORDER BY a.id DESC LIMIT 100",new vn.edu.khtn8.common.jdbc.BooleanColumnMapRowMapper("expired"),studentId));
    }
    public List<Map<String, Object>> links(AuthenticatedUser user) {
        require(user, "PARENT", "STUDENT");
        return jdbc.queryForList("""
                SELECT l.id,l.student_id,l.parent_id,s.full_name student_name,p.full_name parent_name
                FROM parent_student_links l JOIN users s ON s.id=l.student_id JOIN users p ON p.id=l.parent_id
                WHERE l.status='VERIFIED' AND (l.student_id=? OR l.parent_id=?) ORDER BY l.id
                """, user.id(), user.id());
    }
    @Transactional
    public String generateCode(AuthenticatedUser user) {
        require(user, "STUDENT");
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, user.id());
        jdbc.update("UPDATE parent_link_codes SET revoked_at=CURRENT_TIMESTAMP(6) WHERE student_id=? AND used_at IS NULL AND revoked_at IS NULL", user.id());
        byte[] bytes = new byte[16]; random.nextBytes(bytes);
        String code = HexFormat.of().formatHex(bytes);
        jdbc.update("INSERT INTO parent_link_codes(student_id,code_hash,expires_at) VALUES(?,?,DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 15 MINUTE))", user.id(), hash(code));
        return code;
    }
    @Transactional
    public void redeem(AuthenticatedUser user, String code) {
        require(user, "PARENT");
        var rows = jdbc.queryForList("SELECT id,student_id FROM parent_link_codes WHERE code_hash=? AND used_at IS NULL AND revoked_at IS NULL AND expires_at>CURRENT_TIMESTAMP(6) FOR UPDATE", hash(code.trim()));
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã không hợp lệ, đã dùng hoặc hết hạn.");
        var row = rows.getFirst();
        jdbc.update("""
                INSERT INTO parent_student_links(parent_id,student_id,status,confirmed_at) VALUES(?,?,'VERIFIED',CURRENT_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE status='VERIFIED',confirmed_at=CURRENT_TIMESTAMP(6),revoked_at=NULL
                """, user.id(), row.get("student_id"));
        jdbc.update("UPDATE parent_link_codes SET used_at=CURRENT_TIMESTAMP(6) WHERE id=?", row.get("id"));
    }
    @Transactional
    public void revoke(AuthenticatedUser user, long linkId) {
        require(user, "PARENT", "STUDENT");
        int changed = jdbc.update("UPDATE parent_student_links SET status='REVOKED',revoked_at=CURRENT_TIMESTAMP(6) WHERE id=? AND (parent_id=? OR student_id=?)", linkId, user.id(), user.id());
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy liên kết.");
    }
    public void revokeCodes(AuthenticatedUser user) {
        require(user, "STUDENT");
        jdbc.update("UPDATE parent_link_codes SET revoked_at=CURRENT_TIMESTAMP(6) WHERE student_id=? AND used_at IS NULL AND revoked_at IS NULL", user.id());
    }
    public void enroll(AuthenticatedUser user, long classId, long studentId) {
        require(user, "ADMIN");
        if (!exists("SELECT EXISTS(SELECT 1 FROM users WHERE id=? AND role='STUDENT' AND enabled=TRUE)", studentId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Học sinh không hợp lệ.");
        jdbc.update("INSERT INTO class_enrollments(classroom_id,student_id) VALUES(?,?) ON DUPLICATE KEY UPDATE active=TRUE", classId, studentId);
    }
    public void assign(AuthenticatedUser user, long classId, long teacherId) {
        require(user, "ADMIN");
        if (!exists("SELECT EXISTS(SELECT 1 FROM users WHERE id=? AND role='TEACHER' AND enabled=TRUE)", teacherId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giáo viên không hợp lệ.");
        jdbc.update("INSERT INTO class_teachers(classroom_id,teacher_id) VALUES(?,?) ON DUPLICATE KEY UPDATE teacher_id=VALUES(teacher_id)", classId, teacherId);
    }
    public List<Map<String, Object>> years(AuthenticatedUser user) {
        require(user, "ADMIN");
        return jdbc.queryForList("SELECT id,name FROM academic_years WHERE active=TRUE ORDER BY id DESC");
    }
    public List<Map<String, Object>> members(AuthenticatedUser user) {
        require(user, "ADMIN");
        return jdbc.queryForList("SELECT id,username,full_name,role FROM users WHERE enabled=TRUE AND role IN ('STUDENT','TEACHER') ORDER BY id LIMIT 500");
    }
    public void createClass(AuthenticatedUser user, long yearId, String name) {
        require(user, "ADMIN");
        jdbc.update("INSERT INTO classrooms(academic_year_id,name,grade_level) VALUES(?,?,8)", yearId, name.trim());
    }
    static String hash(String code) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
