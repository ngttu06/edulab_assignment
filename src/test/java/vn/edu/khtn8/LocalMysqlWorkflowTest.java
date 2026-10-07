package vn.edu.khtn8;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.khtn8.administration.*;
import vn.edu.khtn8.assessment.QuizService;
import vn.edu.khtn8.content.service.LearningPathService;
import vn.edu.khtn8.identity.dto.LoginRequest;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import vn.edu.khtn8.identity.service.AuthenticationService;
import vn.edu.khtn8.school.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named="EDULAB_TEST_DB_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:3307/edulab_test.*")
@Transactional
class LocalMysqlWorkflowTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->System.getenv("EDULAB_TEST_DB_URL"));
        p.add("spring.datasource.username",()->"edulab_test");p.add("spring.datasource.password",()->"local-test-only-password");
        p.add("edulab.security.jwt.secret",()->"dGVzdC1vbmx5LW5vdC1mb3ItZGVwbG95bWVudC1zZWNyZXQta2V5LTMyeA==");
        p.add("edulab.upload.root",()->{try{return Files.createTempDirectory("edulab-workflow-").toString();}catch(Exception e){throw new IllegalStateException(e);}});
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired QuizService quizzes;
    @Autowired AdminService admin;
    @Autowired SchoolService school;
    @Autowired AssignmentService assignments;
    @Autowired LearningPathService path;
    @Autowired AuthenticationService authentication;
    @Autowired vn.edu.khtn8.identity.service.ProfileService profiles;
    @Autowired vn.edu.khtn8.identity.repository.UserRepository users;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    @Autowired vn.edu.khtn8.identity.security.JwtService jwt;
    long id(String name){return jdbc.queryForObject("SELECT id FROM users WHERE username=?",Long.class,name);}
    AuthenticatedUser user(String name,String role){return new AuthenticatedUser(id(name),name,name,"unused",true,0,List.of(new SimpleGrantedAuthority("ROLE_"+role)));}

    @Test void migrationAndAdminContentWorkflow() {
        assertEquals(16,jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=TRUE",Integer.class));
        var actor=user("admin","ADMIN");
        long topic=jdbc.queryForObject("SELECT MIN(id) FROM topics",Long.class);
        long lesson=admin.createLesson(actor,new AdminController.LessonInput(topic,"Bài kiểm thử","Tóm tắt",10,"Mục tiêu","<h2>Bài học</h2><script>alert(1)</script><p>Kiến thức</p>",null,0));
        admin.publishLesson(actor,lesson,true);
        assertFalse(jdbc.queryForObject("SELECT content_html FROM lesson_versions WHERE lesson_id=?",String.class,lesson).contains("<script>"));
        long question=admin.createQuestion(actor,new AdminController.QuestionInput("Câu hỏi","Giải thích","EASY",List.of("Đúng","Sai"),0));
        long quiz=admin.createQuiz(actor,new AdminController.QuizInput("Quiz mới",lesson,5,3,List.of(question)));
        admin.publishQuiz(actor,quiz,true);
        assertTrue(quizzes.list().stream().anyMatch(q->q.id()==quiz));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->admin.data(user("hocsinh","STUDENT")));
    }
    @Test @WithMockUser(roles="STUDENT") void autosaveAndExpiredSubmissionOnlyGradeSavedAnswers() {
        long student=id("hocsinh");var attempt=quizzes.start(student,quizzes.list().getFirst().id());
        var q=attempt.questions().getFirst();
        long correct=jdbc.queryForObject("SELECT id FROM question_options WHERE question_id=? AND is_correct=TRUE",Long.class,q.id());
        Map<Long,Long> answers=new java.util.HashMap<>();
        for(var question:attempt.questions())answers.put(question.id(),jdbc.queryForObject("SELECT id FROM question_options WHERE question_id=? AND is_correct=TRUE",Long.class,question.id()));
        var saved=quizzes.save(student,attempt.id(),answers);
        assertEquals(correct,saved.questions().getFirst().selectedOption());assertNull(saved.questions().getFirst().options().getFirst().correct());
        jdbc.update("UPDATE quiz_attempts SET deadline_at=DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 1 SECOND) WHERE id=?",attempt.id());
        var result=quizzes.submit(student,attempt.id(),Map.of());
        assertTrue(result.expired());assertEquals(0,BigDecimal.TEN.compareTo(result.score()));
        assertEquals(result.score(),quizzes.submit(student,attempt.id(),Map.of()).score());
    }
    @Test void assignmentsFeedbackAndParentRevocation() {
        var teacher=user("giaovien","TEACHER");var student=user("hocsinh","STUDENT");var parent=user("phuhuynh","PARENT");
        long classroom=((Number)school.classrooms(teacher).getFirst().get("id")).longValue();
        assignments.create(teacher,classroom,quizzes.list().getFirst().id(),Instant.now().plusSeconds(3600));
        long assignment=((Number)assignments.forClass(teacher,classroom).getFirst().get("id")).longValue();
        assertTrue(assignments.forStudent(student,student.id()).stream().anyMatch(a->((Number)a.get("id")).longValue()==assignment));
        school.redeem(parent,school.generateCode(student));
        assignments.feedback(teacher,student.id(),"Tiếp tục luyện tập.");
        assertTrue(assignments.feedback(parent,student.id()).stream().anyMatch(f->"Tiếp tục luyện tập.".equals(f.get("body"))));
        long link=((Number)school.links(parent).getFirst().get("id")).longValue();school.revoke(parent,link);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->assignments.forStudent(parent,student.id()));
    }
    @Test void prerequisiteIsEnforcedUntilCompletion() {
        var actor=user("admin","ADMIN");long topic=jdbc.queryForObject("SELECT MIN(id) FROM topics",Long.class);
        long prerequisite=jdbc.queryForObject("SELECT MIN(id) FROM lessons",Long.class);
        long lesson=admin.createLesson(actor,new AdminController.LessonInput(topic,"Bài tiếp","Tóm tắt",10,"Mục tiêu","<p>Kiến thức</p>",prerequisite,0));
        admin.publishLesson(actor,lesson,true);
        jdbc.update("DELETE FROM lesson_progress WHERE student_id=? AND lesson_id=?",id("hocsinh"),prerequisite);
        long student=id("hocsinh");assertThrows(org.springframework.web.server.ResponseStatusException.class,()->path.requireUnlocked(student,lesson));
        assertSame(Boolean.FALSE,path.steps(student).stream().filter(s->((Number)s.get("id")).longValue()==lesson).findFirst().orElseThrow().get("unlocked"));
        jdbc.update("INSERT INTO lesson_progress(student_id,lesson_id,status,completion_percent) VALUES(?,?,'COMPLETED',100)",student,prerequisite);
        assertDoesNotThrow(()->path.requireUnlocked(student,lesson));
        assertSame(Boolean.TRUE,path.steps(student).stream().filter(s->((Number)s.get("id")).longValue()==lesson).findFirst().orElseThrow().get("unlocked"));
        assertTrue(path.steps(student).stream().allMatch(s->s.get("unlocked") instanceof Boolean));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->path.requireUnlocked(student,Long.MAX_VALUE));
    }
    @Test void assignmentPredicatesAreBooleansForPendingCompletedAndOverdueRows() {
        var teacher=user("giaovien","TEACHER");var student=user("hocsinh","STUDENT");
        long classroom=((Number)school.classrooms(teacher).getFirst().get("id")).longValue();
        long quiz=quizzes.list().getFirst().id();
        assignments.create(teacher,classroom,quiz,Instant.now().plusSeconds(3600));
        long assignment=((Number)assignments.forClass(teacher,classroom).getFirst().get("id")).longValue();
        var pending=assignment(student,assignment);
        assertSame(Boolean.FALSE,pending.get("completed"));assertSame(Boolean.FALSE,pending.get("overdue"));
        int attemptNumber=jdbc.queryForObject("SELECT COALESCE(MAX(attempt_number),0)+1 FROM quiz_attempts WHERE student_id=? AND quiz_id=?",Integer.class,student.id(),quiz);
        jdbc.update("INSERT INTO quiz_attempts(student_id,quiz_id,attempt_number,status,submitted_at,score) VALUES(?,?,?,'GRADED',CURRENT_TIMESTAMP(6),10)",student.id(),quiz,attemptNumber);
        var completed=assignment(student,assignment);
        assertSame(Boolean.TRUE,completed.get("completed"));assertSame(Boolean.FALSE,completed.get("overdue"));
        jdbc.update("UPDATE class_assignments SET due_at=DATE_SUB(created_at,INTERVAL 1 SECOND) WHERE id=?",assignment);
        var overdue=assignment(student,assignment);
        assertSame(Boolean.FALSE,overdue.get("completed"));assertSame(Boolean.TRUE,overdue.get("overdue"));
    }
    private Map<String,Object> assignment(AuthenticatedUser student,long assignmentId) {
        return assignments.forStudent(student,student.id()).stream().filter(a->((Number)a.get("id")).longValue()==assignmentId).findFirst().orElseThrow();
    }
    @Test void refreshRotatesAndReuseRevokesFamily() {
        var request=new MockHttpServletRequest();request.setContextPath("/edulab");var response=new MockHttpServletResponse();
        authentication.login(new LoginRequest("hocsinh","ChangeMe123!"),request,response);
        String token=refreshToken(response);
        String family=jdbc.queryForObject("SELECT family_id FROM refresh_sessions WHERE token_hash=SHA2(?,256)",String.class,token);
        var unrelated=new MockHttpServletResponse();authentication.login(new LoginRequest("hocsinh","ChangeMe123!"),request,unrelated);
        String otherFamily=jdbc.queryForObject("SELECT family_id FROM refresh_sessions WHERE token_hash=SHA2(?,256)",String.class,refreshToken(unrelated));
        assertNotEquals(family,otherFamily);
        var next=new MockHttpServletResponse();authentication.refresh(token,request,next);
        assertNotEquals(token,refreshToken(next));
        assertEquals(family,jdbc.queryForObject("SELECT family_id FROM refresh_sessions WHERE token_hash=SHA2(?,256)",String.class,refreshToken(next)));
        assertThrows(org.springframework.security.authentication.BadCredentialsException.class,()->authentication.refresh(token,request,new MockHttpServletResponse()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM refresh_sessions WHERE family_id=? AND revoked_at IS NULL",Integer.class,family));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM refresh_sessions WHERE family_id=? AND revoked_at IS NULL",Integer.class,otherFamily));
    }
    @Test void profileUpdatesPersistAndPasswordChangeRevokesEverySession() {
        var request = new MockHttpServletRequest(); request.setContextPath("/edulab");
        var first = new MockHttpServletResponse(); var second = new MockHttpServletResponse();
        authentication.login(new LoginRequest("hocsinh", "ChangeMe123!"), request, first);
        authentication.login(new LoginRequest("hocsinh", "ChangeMe123!"), request, second);
        String oldRefresh = refreshToken(first);
        String oldAccess = first.getHeaders("Set-Cookie").stream().filter(s -> s.startsWith("EDULAB_ACCESS="))
                .findFirst().orElseThrow().split(";", 2)[0].substring("EDULAB_ACCESS=".length());
        var principal = AuthenticatedUser.from(users.findById(id("hocsinh")).orElseThrow());
        var updated = profiles.update(principal, new vn.edu.khtn8.identity.dto.UpdateProfileRequest(
                "  Học sinh kiểm thử  ", " PROFILE_WORKFLOW@Example.Test "));
        assertEquals("Học sinh kiểm thử", updated.fullName());
        assertEquals("profile_workflow@example.test", jdbc.queryForObject("SELECT email FROM users WHERE id=?", String.class, principal.id()));
        assertEquals("hocsinh", updated.username()); assertEquals("STUDENT", updated.role());
        assertEquals(principal.authVersion(), jdbc.queryForObject("SELECT auth_version FROM users WHERE id=?", Integer.class, principal.id()));
        var response = new MockHttpServletResponse();
        profiles.changePassword(principal, new vn.edu.khtn8.identity.dto.ChangePasswordRequest(
                "ChangeMe123!", "ProfileNewPassword123!", "ProfileNewPassword123!"), request, response);
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?", String.class, principal.id());
        assertTrue(encoder.matches("ProfileNewPassword123!", hash)); assertFalse(encoder.matches("ChangeMe123!", hash));
        int version = jdbc.queryForObject("SELECT auth_version FROM users WHERE id=?", Integer.class, principal.id());
        assertEquals(principal.authVersion() + 1, version);
        assertNotEquals(jwt.parse(oldAccess).get("av", Integer.class), version);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM refresh_sessions WHERE user_id=? AND revoked_at IS NULL", Integer.class, principal.id()));
        for (String cookie : new String[]{"EDULAB_ACCESS=", "EDULAB_REFRESH="}) {
            assertTrue(response.getHeaders("Set-Cookie").stream().anyMatch(s -> s.startsWith(cookie) && s.contains("Max-Age=0") && s.contains("HttpOnly")));
        }
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> profiles.profile(principal));
        authentication.login(new LoginRequest("hocsinh", "ProfileNewPassword123!"), request, new MockHttpServletResponse());
        assertThrows(org.springframework.security.authentication.BadCredentialsException.class,
                () -> authentication.refresh(oldRefresh, request, new MockHttpServletResponse()));
    }

    private String refreshToken(MockHttpServletResponse response) {
        return response.getHeaders("Set-Cookie").stream().filter(s->s.startsWith("EDULAB_REFRESH=")).findFirst().orElseThrow().split(";",2)[0].substring("EDULAB_REFRESH=".length());
    }
}