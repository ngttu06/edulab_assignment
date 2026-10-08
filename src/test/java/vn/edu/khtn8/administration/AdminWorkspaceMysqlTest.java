package vn.edu.khtn8.administration;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import vn.edu.khtn8.school.SchoolService;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named="EDULAB_TEST_DB_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:3307/edulab_test.*")
@Transactional
class AdminWorkspaceMysqlTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->System.getenv("EDULAB_TEST_DB_URL"));
        p.add("spring.datasource.username",()->"edulab_test");p.add("spring.datasource.password",()->"local-test-only-password");
        p.add("edulab.security.jwt.secret",()->"dGVzdC1vbmx5LW5vdC1mb3ItZGVwbG95bWVudC1zZWNyZXQta2V5LTMyeA==");
    }
    @Autowired AdminWorkspaceService service;
    @Autowired SchoolService school;
    @Autowired JdbcTemplate jdbc;
    private AuthenticatedUser actor(String name,String role) {return new AuthenticatedUser(jdbc.queryForObject("SELECT id FROM users WHERE username=?",Long.class,name),name,name,"unused",true,0,List.of(new SimpleGrantedAuthority("ROLE_"+role)));}
    private AuthenticatedUser admin() {return actor("admin","ADMIN");}
    private long first(String table) {return jdbc.queryForObject("SELECT MIN(id) FROM "+table,Long.class);}
    private Map<String,Object> account(String name) {return new HashMap<>(Map.of("username",name,"full_name","Giáo viên kiểm thử","email",name+"@example.test","password","TestOnly123!","enabled",true));}
    private long classroom() {return service.save(admin(),"classes",null,Map.of("name","Admin test class","academic_year_id",first("academic_years"),"grade_level",10));}
    @Test void allResourcesListWithoutSqlErrorsAndNeverExposePasswords() {
        for(var resource:AdminResources.all()) {
            var result=service.list(admin(),resource.key(),"","all",0,"name");
            assertNotNull(result.items(),resource.key());
            for(var row:result.items())assertFalse(row.containsKey("password_hash"));
            assertNotNull(service.list(admin(),resource.key(),"%'_!","active",100,"oldest"));
        }
        assertFalse(service.detail(admin(),"administrators",admin().id()).containsKey("password_hash"));
    }
    @Test void teacherCrudLocksSessionsButKeepsHistory() {
        var input=account("admin_crud_teacher");long id=service.save(admin(),"teachers",null,input);
        assertTrue(jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,id).startsWith("$2"));
        input.put("full_name","Tên đã sửa");input.put("password","");service.save(admin(),"teachers",id,input);
        assertEquals("Tên đã sửa",service.detail(admin(),"teachers",id).get("full_name"));
        assertEquals(1,jdbc.queryForObject("SELECT auth_version FROM users WHERE id=?",Integer.class,id));
        service.remove(admin(),"teachers",id);assertEquals(false,service.detail(admin(),"teachers",id).get("enabled"));
        service.restore(admin(),"teachers",id);assertEquals(true,service.detail(admin(),"teachers",id).get("enabled"));
        assertThrows(ResponseStatusException.class,()->service.remove(admin(),"administrators",admin().id()));
        assertThrows(ResponseStatusException.class,()->service.detail(admin(),"students",id));
    }
    @Test void assignmentHasExactlyOneHomeroomAndRevokesTeacherAccess() {
        long c=classroom(),t=actor("giaovien","TEACHER").id(),other=service.save(admin(),"teachers",null,account("admin_other_teacher"));
        service.assignTeacher(admin(),c,t,true);service.assignTeacher(admin(),c,other,true);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM class_teachers WHERE classroom_id=? AND is_homeroom_teacher=TRUE",Integer.class,c));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM class_teachers WHERE classroom_id=?",Integer.class,c));
        school.authorizeClass(actor("giaovien","TEACHER"),c);service.removeTeacher(admin(),c,t);
        assertThrows(AccessDeniedException.class,()->school.authorizeClass(actor("giaovien","TEACHER"),c));
        service.remove(admin(),"teachers",other);assertThrows(ResponseStatusException.class,()->service.assignTeacher(admin(),c,other,false));
        service.remove(admin(),"classes",c);assertThrows(ResponseStatusException.class,()->service.assignTeacher(admin(),c,t,false));
        service.restore(admin(),"classes",c);service.assignTeacher(admin(),c,t,true);
    }
    @Test void classEnrollmentAndArchivePreserveMembership() {
        long c=classroom(),s=actor("hocsinh","STUDENT").id();service.enrollStudent(admin(),c,s,true);service.enrollStudent(admin(),c,s,false);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM class_enrollments WHERE classroom_id=?",Integer.class,c));
        service.remove(admin(),"classes",c);assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM class_enrollments WHERE classroom_id=?",Integer.class,c));
    }
    @Test void publicLessonEditingSanitizesVersionsAndPreservesProgress() {
        var input=new HashMap<String,Object>(Map.of("title","Admin lesson","topic_id",first("topics"),"summary","Tóm tắt","duration_minutes",30,"objectives","Mục tiêu","content_html","<h2>Nội dung</h2><script>alert(1)</script><p>Kiến thức</p>","status","PUBLISHED","required_score",0,"class_only",false,"classIds",List.of()));
        long id=service.save(admin(),"lessons",null,input);
        assertFalse(service.detail(admin(),"lessons",id).get("content_html").toString().contains("script"));
        jdbc.update("INSERT INTO lesson_progress(student_id,lesson_id,status,completion_percent) VALUES(?,?,'COMPLETED',100)",actor("hocsinh","STUDENT").id(),id);
        input.put("title","Admin lesson revised");service.save(admin(),"lessons",id,input);
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM lesson_versions WHERE lesson_id=?",Integer.class,id));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM lesson_versions WHERE lesson_id=? AND status='PUBLISHED'",Integer.class,id));
        service.remove(admin(),"lessons",id);assertEquals(100,jdbc.queryForObject("SELECT completion_percent FROM lesson_progress WHERE lesson_id=?",Integer.class,id));
        service.restore(admin(),"lessons",id);assertEquals("DRAFT",service.detail(admin(),"lessons",id).get("status"));
        input.put("prerequisite_id",id);assertThrows(ResponseStatusException.class,()->service.save(admin(),"lessons",id,input));
    }
    @Test void questionAndQuizCrudKeepAttemptSnapshots() {
        var q=new HashMap<String,Object>(Map.of("bank_id",first("question_banks"),"content","Câu hỏi quản trị","explanation","Giải thích","difficulty","EASY","options","Đúng\nSai","correctIndex",1,"active",true));
        long question=service.save(admin(),"questions",null,q);
        var quiz=new HashMap<String,Object>(Map.of("title","Admin quiz","duration_minutes",15,"max_attempts",3,"mode","PRACTICE","published",true,"class_only",false,"classIds",List.of(),"questionIds",List.of(question)));
        long id=service.save(admin(),"quizzes",null,quiz);
        jdbc.update("INSERT INTO quiz_attempts(quiz_id,student_id,attempt_number,status,title_snapshot,questions_snapshot) VALUES(?,?,1,'GRADED','Historical title','[]')",id,actor("hocsinh","STUDENT").id());
        q.put("options","Sai\nĐúng");q.put("correctIndex",2);service.save(admin(),"questions",question,q);
        quiz.put("title","Edited quiz");service.save(admin(),"quizzes",id,quiz);service.remove(admin(),"quizzes",id);
        assertEquals("Historical title",jdbc.queryForObject("SELECT title_snapshot FROM quiz_attempts WHERE quiz_id=?",String.class,id));
        service.restore(admin(),"quizzes",id);assertEquals(false,service.detail(admin(),"quizzes",id).get("published"));
    }
    @Test void serviceRejectsOtherRolesAndReadonlyWrites() {
        assertThrows(AccessDeniedException.class,()->service.overview(actor("giaovien","TEACHER")));
        assertThrows(ResponseStatusException.class,()->service.save(admin(),"audit-logs",null,Map.of()));
        assertThrows(ResponseStatusException.class,()->service.save(admin(),"teachers",null,account("invalid space")));
    }
    @Test void catalogCrudRespectsReferencesAndUniqueConstraints() {
        long subject=service.save(admin(),"subjects",null,Map.of("name","Admin subject","grade_level",9,"description","Description"));
        var course=new HashMap<String,Object>(Map.of("subject_id",subject,"title","Admin course","summary","Summary","kind","TOPIC","theme","green"));
        long courseId=service.save(admin(),"courses",null,course);
        var topic=new HashMap<String,Object>(Map.of("name","Admin topic","course_id",courseId,"display_order",1));
        long topicId=service.save(admin(),"topics",null,topic);topic.put("name","Renamed topic");service.save(admin(),"topics",topicId,topic);
        assertEquals(subject,((Number)service.detail(admin(),"topics",topicId).get("subject_id")).longValue());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->service.remove(admin(),"subjects",subject));
        service.remove(admin(),"topics",topicId);service.remove(admin(),"courses",courseId);service.remove(admin(),"subjects",subject);
        assertThrows(ResponseStatusException.class,()->service.detail(admin(),"subjects",subject));
        classroom();assertThrows(org.springframework.dao.DataIntegrityViolationException.class,this::classroom);
    }
    @Test void parentAccessAndFeedbackHistoryArePreserved() {
        var parent=actor("phuhuynh","PARENT");var student=actor("hocsinh","STUDENT");
        jdbc.update("DELETE FROM parent_student_links WHERE parent_id=? AND student_id=?",parent.id(),student.id());
        long link=service.save(admin(),"parent-links",null,Map.of("parent_id",parent.id(),"student_id",student.id(),"status","VERIFIED"));
        school.authorizeStudent(parent,student.id());service.remove(admin(),"parent-links",link);
        assertThrows(AccessDeniedException.class,()->school.authorizeStudent(parent,student.id()));
        assertNotNull(jdbc.queryForObject("SELECT revoked_at FROM parent_student_links WHERE id=?",java.sql.Timestamp.class,link));
        long feedback=service.save(admin(),"feedback",null,Map.of("student_id",student.id(),"body","Original feedback"));
        service.save(admin(),"feedback",feedback,Map.of("student_id",student.id(),"body","Updated feedback"));
        service.remove(admin(),"feedback",feedback);
        assertEquals("Original feedback",jdbc.queryForObject("SELECT body FROM teacher_feedback_revisions WHERE feedback_id=?",String.class,feedback));
    }
    @Test void assignmentArchiveAndQuestionValidation() {
        long c=classroom(),quiz=jdbc.queryForObject("SELECT MIN(id) FROM quizzes WHERE published=TRUE AND class_only=FALSE AND deleted_at IS NULL",Long.class);
        long assignment=service.save(admin(),"assignments",null,Map.of("classroom_id",c,"quiz_id",quiz,"due_at",java.time.Instant.now().plusSeconds(3600).toString()));
        service.remove(admin(),"quizzes",quiz);
        assertNotNull(jdbc.queryForObject("SELECT cancelled_at FROM class_assignments WHERE id=?",java.sql.Timestamp.class,assignment));
        assertThrows(ResponseStatusException.class,()->service.restore(admin(),"assignments",assignment));
        assertThrows(ResponseStatusException.class,()->service.save(admin(),"questions",null,Map.of("bank_id",first("question_banks"),"content","Question","explanation","Answer","difficulty","EASY","options","Only one","correctIndex",1,"active",true)));
    }
    @Test void sessionTokensAreRevokedAndFilesCannotBypassScanner() {
        long teacher=service.save(admin(),"teachers",null,account("admin_sessions_teacher"));
        jdbc.update("INSERT INTO refresh_sessions(user_id,token_hash,expires_at) VALUES(?,REPEAT('a',64),DATE_ADD(NOW(),INTERVAL 1 DAY))",teacher);
        service.remove(admin(),"teachers",teacher);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM refresh_sessions WHERE user_id=? AND revoked_at IS NULL",Integer.class,teacher));
        jdbc.update("INSERT INTO stored_files(storage_key,original_filename,content_type,size_bytes,sha256,scan_status,uploaded_by) VALUES('admin-test-file','sample.pdf','application/pdf',100,REPEAT('a',64),'CLEAN',?)",admin().id());
        long file=jdbc.queryForObject("SELECT id FROM stored_files WHERE storage_key='admin-test-file'",Long.class);
        service.save(admin(),"files",file,Map.of("original_filename","Renamed.pdf","scan_status","CLEAN"));
        service.remove(admin(),"files",file);assertEquals("REJECTED",service.detail(admin(),"files",file).get("scan_status"));
        assertThrows(ResponseStatusException.class,()->service.restore(admin(),"files",file));
        assertThrows(ResponseStatusException.class,()->service.save(admin(),"files",file,Map.of("original_filename","../unsafe.pdf")));
    }
    @Test void communityCrudAndPaginationEscapeWildcards() {
        long category=service.save(admin(),"forum-categories",null,Map.of("name","Admin category","description","Test","active",true));
        long thread=service.save(admin(),"forum-threads",null,Map.of("category_id",category,"title","Admin question","body","Body","status","PUBLISHED"));
        long post=service.save(admin(),"forum-posts",null,Map.of("thread_id",thread,"body","Reply","status","PUBLISHED"));
        service.remove(admin(),"forum-posts",post);service.restore(admin(),"forum-posts",post);
        var report=new HashMap<String,Object>(Map.of("thread_id",thread,"reason","Evidence","status","OPEN"));
        long reportId=service.save(admin(),"content-reports",null,report);service.remove(admin(),"forum-threads",thread);
        report.put("status","RESOLVED");service.save(admin(),"content-reports",reportId,report);
        assertEquals("Evidence",service.detail(admin(),"content-reports",reportId).get("reason"));
        long community=service.save(admin(),"community-posts",null,Map.of("body","Social body","status","PUBLISHED"));
        long comment=service.save(admin(),"community-comments",null,Map.of("post_id",community,"body","Comment","status","PUBLISHED"));
        service.remove(admin(),"community-comments",comment);assertEquals("REMOVED",service.detail(admin(),"community-comments",comment).get("status"));
        for(int i=0;i<25;i++)jdbc.update("INSERT INTO subjects(name,grade_level) VALUES(?,8)","admin_page_%_"+i);
        var first=service.list(admin(),"subjects","admin_page_%_","all",0,"newest");
        var second=service.list(admin(),"subjects","admin_page_%_","all",1,"newest");
        assertEquals(25,first.total());assertEquals(20,first.items().size());assertEquals(5,second.items().size());
        assertNotEquals(first.items().getFirst().get("id"),second.items().getFirst().get("id"));
    }
    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentLeadChangesSerializeOnClass() throws Exception {
        var admin=admin();String suffix=UUID.randomUUID().toString().substring(0,8);
        long c=service.save(admin,"classes",null,Map.of("name","Concurrent "+suffix,"academic_year_id",first("academic_years"),"grade_level",8));
        long a=service.save(admin,"teachers",null,account("concurrent_a_"+suffix)),b=service.save(admin,"teachers",null,account("concurrent_b_"+suffix));
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var latch=new java.util.concurrent.CountDownLatch(1);
            var first=pool.submit(()->{latch.await();service.assignTeacher(admin,c,a,true);return true;});
            var second=pool.submit(()->{latch.await();service.assignTeacher(admin,c,b,true);return true;});latch.countDown();
            assertTrue(first.get(15,java.util.concurrent.TimeUnit.SECONDS));assertTrue(second.get(15,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM class_teachers WHERE classroom_id=? AND is_homeroom_teacher=TRUE",Integer.class,c));
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM class_teachers WHERE classroom_id=?",Integer.class,c));
        } finally {
            jdbc.update("DELETE FROM class_teachers WHERE classroom_id=?",c);jdbc.update("DELETE FROM classrooms WHERE id=?",c);jdbc.update("DELETE FROM users WHERE id IN (?,?)",a,b);
        }
    }
}