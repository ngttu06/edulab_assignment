package vn.edu.khtn8.school;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.assessment.QuizService;
import vn.edu.khtn8.content.dto.ProgressRequest;
import vn.edu.khtn8.content.service.*;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import static vn.edu.khtn8.school.AuthoringModels.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named="EDULAB_TEST_DB_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:3307/edulab_test.*")
@Transactional
class TeacherMysqlTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->System.getenv("EDULAB_TEST_DB_URL"));
        p.add("spring.datasource.username",()->"edulab_test");p.add("spring.datasource.password",()->"local-test-only-password");
        p.add("edulab.security.jwt.secret",()->"dGVzdC1vbmx5LW5vdC1mb3ItZGVwbG95bWVudC1zZWNyZXQta2V5LTMyeA==");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired TeacherAuthoringService authoring;
    @Autowired TeacherClassService classes;
    @Autowired SchoolService school;
    @Autowired AssignmentService assignments;
    @Autowired TeacherInsightsService insights;
    @Autowired LessonService lessons;
    @Autowired LearningPathService path;
    @Autowired CatalogService catalog;
    @Autowired QuizService quizzes;
    AuthenticatedUser user(String name,String role) {
        long id=jdbc.queryForObject("SELECT id FROM users WHERE username=?",Long.class,name);
        return new AuthenticatedUser(id,name,name,"unused",true,0,List.of(new SimpleGrantedAuthority("ROLE_"+role)));
    }
    AuthenticatedUser teacher() {return user("giaovien","TEACHER");}
    AuthenticatedUser student() {return user("hocsinh","STUDENT");}
    long classroom() {return ((Number)school.classrooms(teacher()).getFirst().get("id")).longValue();}
    long otherClass() {
        jdbc.update("INSERT INTO classrooms(academic_year_id,name,grade_level) SELECT MIN(id),'Teacher test other',8 FROM academic_years");
        return jdbc.queryForObject("SELECT id FROM classrooms WHERE name='Teacher test other'",Long.class);
    }
    AuthenticatedUser outsider(String role) {
        String name="teacher_test_"+role;
        jdbc.update("INSERT INTO users(username,email,password_hash,full_name,role) VALUES(?,?,'unused',?,?)",name,name+"@example.test",name,role);
        return user(name,role);
    }
    LessonInput lessonInput(long classId,String status) {
        return new LessonInput(jdbc.queryForObject("SELECT MIN(id) FROM topics",Long.class),"Bài riêng","Mô tả",20,"Mục tiêu","<h2>Kiến thức</h2><script>alert(1)</script><p>Bài học</p>",status,List.of(classId));
    }
    QuizInput quizInput(long classId,boolean published) {
        return new QuizInput("Quiz riêng",15,3,"PRACTICE",null,published,List.of(new QuestionInput("Câu hỏi","Giải thích",List.of("Sai","Đúng"),1,BigDecimal.ONE)),List.of(classId));
    }
    @Test @WithMockUser(roles="STUDENT") void lessonLifecycleAndClassVisibility() {
        long classId=classroom();var teacher=teacher();var student=student();var outsider=outsider("STUDENT");
        long completedBefore=(long)classes.report(teacher,classId,student.id()).get("completed");
        long id=authoring.saveLesson(teacher,null,lessonInput(classId,"DRAFT"));
        assertThrows(ResponseStatusException.class,()->lessons.detail(student.id(),id));
        authoring.saveLesson(teacher,id,lessonInput(classId,"PUBLISHED"));
        assertFalse(lessons.detail(student.id(),id).contentHtml().contains("script"));
        assertThrows(ResponseStatusException.class,()->lessons.detail(outsider.id(),id));
        assertFalse(path.steps(0).stream().anyMatch(r->((Number)r.get("id")).longValue()==id));
        assertDoesNotThrow(()->path.requireUnlocked(student.id(),id));
        lessons.updateProgress(student.id(),id,new ProgressRequest("COMPLETED",100));
        assertEquals(completedBefore+1,classes.report(teacher,classId,student.id()).get("completed"));
        var roster=rows(classes.roster(teacher,classId,"hocsinh",0),"students");
        assertFalse(roster.isEmpty());assertEquals(100,((Number)roster.getFirst().get("completion_percent")).intValue());
        classes.membership(teacher,classId,student.id(),false);
        assertThrows(ResponseStatusException.class,()->lessons.detail(student.id(),id));
        assertEquals(completedBefore+1,classes.report(teacher,classId,student.id()).get("completed"));
        classes.enroll(teacher,classId,"hocsinh");assertEquals(100,lessons.detail(student.id(),id).completionPercent());
        authoring.saveLesson(teacher,id,lessonInput(classId,"ARCHIVED"));
        assertThrows(ResponseStatusException.class,()->lessons.detail(student.id(),id));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM lesson_versions WHERE lesson_id=?",Integer.class,id));
    }
    @Test @WithMockUser(roles="STUDENT") void quizSnapshotScoresAndReportRemainStableAfterEditing() {
        long classId=classroom();var teacher=teacher();var student=student();var outsider=outsider("STUDENT");
        long id=authoring.saveQuiz(teacher,null,quizInput(classId,true));
        var loaded=authoring.quiz(teacher,id);
        assertEquals(1,((QuestionInput)((List<?>)loaded.get("questions")).getFirst()).correctIndex());
        assertTrue(quizzes.list(student.id()).stream().anyMatch(q->q.id()==id));
        assertFalse(quizzes.list(outsider.id()).stream().anyMatch(q->q.id()==id));
        assertThrows(ResponseStatusException.class,()->quizzes.start(outsider.id(),id));
        var before=rows(classes.report(teacher,classId,student.id()),"quizzes").getFirst();
        assertEquals("NOT_STARTED",before.get("status"));assertNull(before.get("best_score"));
        assignments.create(teacher,classId,id,Instant.now().plusSeconds(3600));
        var attempt=quizzes.start(student.id(),id);var q=attempt.questions().getFirst();
        assertNull(q.options().getFirst().correct());
        long correct=q.options().get(1).id();
        authoring.saveQuiz(teacher,id,new QuizInput("Đề đã sửa",10,3,"PRACTICE",null,true,List.of(new QuestionInput("Câu khác","",List.of("Đúng","Sai"),0,BigDecimal.TEN)),List.of(classId)));
        var result=quizzes.submit(student.id(),attempt.id(),Map.of(q.id(),correct));
        assertEquals(new BigDecimal("10.00"),result.score());assertEquals("Quiz riêng",result.title());
        var report=rows(classes.report(teacher,classId,student.id()),"quizzes").getFirst();
        assertEquals(new BigDecimal("10.00"),report.get("best_score"));assertEquals(new BigDecimal("10.00"),report.get("latest_score"));
        assertEquals(1L,((Number)report.get("graded_count")).longValue());
        var overview=rows(classes.roster(teacher,classId,"",0),"quizzes").stream().filter(r->((Number)r.get("id")).longValue()==id).findFirst().orElseThrow();
        assertEquals(1L,((Number)overview.get("completed")).longValue());
        classes.membership(teacher,classId,student.id(),false);
        assertThrows(ResponseStatusException.class,()->quizzes.start(student.id(),id));
        assertEquals(new BigDecimal("10.00"),quizzes.get(student.id(),attempt.id()).score());
    }
    @Test void rejectsCrossClassManagementAndNonOwnerEditing() {
        long own=classroom(),other=otherClass();var teacher=teacher();var differentTeacher=outsider("TEACHER");
        long lesson=authoring.saveLesson(teacher,null,lessonInput(own,"PUBLISHED"));
        long quiz=authoring.saveQuiz(teacher,null,quizInput(own,true));
        assertThrows(AccessDeniedException.class,()->classes.roster(teacher,other,"",0));
        assertThrows(AccessDeniedException.class,()->classes.enroll(teacher,other,"hocsinh"));
        assertThrows(AccessDeniedException.class,()->classes.membership(teacher,other,student().id(),false));
        assertThrows(AccessDeniedException.class,()->classes.report(teacher,other,student().id()));
        assertThrows(AccessDeniedException.class,()->authoring.saveLesson(teacher,null,lessonInput(other,"PUBLISHED")));
        assertThrows(AccessDeniedException.class,()->authoring.lesson(differentTeacher,lesson));
        assertThrows(AccessDeniedException.class,()->authoring.quiz(differentTeacher,quiz));
        assertThrows(AccessDeniedException.class,()->authoring.saveQuiz(student(),null,quizInput(own,true)));
        assertThrows(ResponseStatusException.class,()->assignments.create(user("admin","ADMIN"),other,quiz,Instant.now().plusSeconds(3600)));
        assertThrows(ResponseStatusException.class,()->classes.membership(teacher,own,differentTeacher.id(),true));
    }
    @Test void invalidInputsDoNotCreatePartialContent() {
        long classId=classroom();var teacher=teacher();int before=jdbc.queryForObject("SELECT COUNT(*) FROM quizzes",Integer.class);
        var input=new QuizInput("Invalid",15,1,"PRACTICE",null,true,List.of(new QuestionInput("Question","",List.of("A","B"),3,BigDecimal.ONE)),List.of(classId));
        assertThrows(ResponseStatusException.class,()->authoring.saveQuiz(teacher,null,input));
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM quizzes",Integer.class));
    }
    @Test void legacyReportDoesNotExposeOtherClassPrivateContent() {
        long other=otherClass();var teacher=teacher();var student=student();var admin=user("admin","ADMIN");
        school.enroll(admin,other,student.id());long id=authoring.saveLesson(admin,null,lessonInput(other,"PUBLISHED"));
        var report=school.report(teacher,student.id());
        assertFalse(((List<vn.edu.khtn8.content.dto.LessonSummary>)report.get("lessons")).stream().anyMatch(l->l.id()==id));
    }
    @Test @WithMockUser(roles="STUDENT") void softDeletePreservesProgressAttemptsAndCancelsAssignments() {
        var t=teacher();var s=student();long c=classroom();
        long completedBefore=(long)classes.report(t,c,s.id()).get("completed");
        long lesson=authoring.saveLesson(t,null,lessonInput(c,"PUBLISHED"));
        lessons.updateProgress(s.id(),lesson,new ProgressRequest("COMPLETED",100));
        authoring.deleteLesson(t,lesson);
        assertNotNull(authoring.lesson(t,lesson).get("deleted_at"));
        assertThrows(ResponseStatusException.class,()->lessons.detail(s.id(),lesson));
        assertEquals(completedBefore+1,classes.report(t,c,s.id()).get("completed"));
        assertThrows(ResponseStatusException.class,()->authoring.saveLesson(t,lesson,lessonInput(c,"PUBLISHED")));
        long quiz=authoring.saveQuiz(t,null,quizInput(c,true));
        assignments.create(t,c,quiz,Instant.now().plusSeconds(3600));
        var attempt=quizzes.start(s.id(),quiz);var q=attempt.questions().getFirst();
        authoring.deleteQuiz(t,quiz);
        assertThrows(ResponseStatusException.class,()->quizzes.start(s.id(),quiz));
        assertEquals(new BigDecimal("10.00"),quizzes.submit(s.id(),attempt.id(),Map.of(q.id(),q.options().get(1).id())).score());
        assertTrue(assignments.forStudent(s,s.id()).stream().noneMatch(a->((Number)a.get("quiz_id")).longValue()==quiz));
        assertNotNull(assignments.forClass(t,c).getFirst().get("cancelled_at"));
        assertEquals(1L,((Number)rows(classes.report(t,c,s.id()),"quizzes").getFirst().get("graded_count")).longValue());
    }
    @Test void assignmentCrudChecksClassAndKeepsOriginalQuiz() {
        var t=teacher();long c=classroom();long quiz=authoring.saveQuiz(t,null,quizInput(c,true));
        assignments.create(t,c,quiz,Instant.now().plusSeconds(3600));
        long id=((Number)assignments.forClass(t,c).getFirst().get("id")).longValue();
        Instant due=Instant.now().plusSeconds(7200).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        assignments.updateAssignment(t,c,id,due);
        assertEquals(quiz,((Number)assignments.assignment(t,c,id).get("quiz_id")).longValue());
        assertEquals(due,((java.sql.Timestamp)assignments.assignment(t,c,id).get("due_at")).toInstant());
        assertThrows(AccessDeniedException.class,()->assignments.cancelAssignment(outsider("TEACHER"),c,id));
        assertThrows(ResponseStatusException.class,()->assignments.assignment(user("admin","ADMIN"),otherClass(),id));
        assertThrows(ResponseStatusException.class,()->assignments.updateAssignment(t,c,id,Instant.now().minusSeconds(60)));
        assignments.cancelAssignment(t,c,id);assertNotNull(assignments.assignment(t,c,id).get("cancelled_at"));
        assertThrows(ResponseStatusException.class,()->assignments.updateAssignment(t,c,id,due));
    }
    @Test void feedbackCrudPreservesRevisionAndRestrictsAuthors() {
        var t=teacher();var s=student();var other=outsider("TEACHER");long c=classroom();
        school.assign(user("admin","ADMIN"),c,other.id());assignments.feedback(t,s.id(),"Ban đầu");
        long id=((Number)assignments.feedback(t,s.id()).getFirst().get("id")).longValue();
        assertThrows(AccessDeniedException.class,()->assignments.updateFeedback(other,s.id(),id,"Không được"));
        assertThrows(AccessDeniedException.class,()->assignments.deleteFeedback(other,s.id(),id));
        assignments.updateFeedback(t,s.id(),id,"Đã sửa");
        assertEquals("Đã sửa",assignments.feedbackDetail(t,s.id(),id).get("body"));
        assertEquals("Ban đầu",jdbc.queryForObject("SELECT body FROM teacher_feedback_revisions WHERE feedback_id=?",String.class,id));
        assignments.deleteFeedback(t,s.id(),id);
        assertTrue(assignments.feedback(s,s.id()).stream().noneMatch(f->((Number)f.get("id")).longValue()==id));
        assertNotNull(jdbc.queryForObject("SELECT deleted_at FROM teacher_feedback WHERE id=?",java.sql.Timestamp.class,id));
    }
    @Test @WithMockUser(roles="STUDENT") void archivedClassIsReadOnlyAndRetainsMembershipHistory() {
        var t=teacher();var admin=user("admin","ADMIN");var s=student();long c=classroom();
        long quiz=authoring.saveQuiz(t,null,quizInput(c,true));
        long year=((Number)school.classroom(t,c).get("academic_year_id")).longValue();
        assertThrows(AccessDeniedException.class,()->school.updateClass(t,c,year,"Không được"));
        assertThrows(AccessDeniedException.class,()->school.archiveClass(t,c));
        school.updateClass(admin,c,year,"Lớp đổi tên");assertEquals("Lớp đổi tên",school.classroom(t,c).get("name"));
        school.archiveClass(admin,c);
        assertTrue(school.classrooms(t).stream().noneMatch(x->((Number)x.get("id")).longValue()==c));
        assertDoesNotThrow(()->classes.report(t,c,s.id()));
        assertThrows(ResponseStatusException.class,()->classes.enroll(t,c,"hocsinh"));
        assertThrows(ResponseStatusException.class,()->classes.membership(t,c,s.id(),false));
        assertThrows(ResponseStatusException.class,()->assignments.create(t,c,quiz,Instant.now().plusSeconds(3600)));
        assertThrows(ResponseStatusException.class,()->quizzes.start(s.id(),quiz));
        assertEquals(true,classes.member(t,c,s.id()).get("active"));
    }
    @Test void deletionRequiresAuthorAndCurrentClassAssignment() {
        var t=teacher();long c=classroom();long quiz=authoring.saveQuiz(t,null,quizInput(c,true));
        long lesson=authoring.saveLesson(t,null,lessonInput(c,"DRAFT"));var other=outsider("TEACHER");
        assertThrows(AccessDeniedException.class,()->authoring.deleteQuiz(other,quiz));
        assertThrows(AccessDeniedException.class,()->authoring.deleteLesson(other,lesson));
        school.unassign(user("admin","ADMIN"),c,t.id());
        assertThrows(AccessDeniedException.class,()->authoring.deleteQuiz(t,quiz));
        assertThrows(AccessDeniedException.class,()->authoring.deleteLesson(t,lesson));
    }
    @Test void teacherOverviewResultsAndDuplicationRespectAssignment() {
        var teacher=teacher();long classId=classroom();
        long lesson=authoring.saveLesson(teacher,null,lessonInput(classId,"PUBLISHED"));
        long quiz=authoring.saveQuiz(teacher,null,quizInput(classId,true));
        long lessonCopy=authoring.duplicateLesson(teacher,lesson),quizCopy=authoring.duplicateQuiz(teacher,quiz);
        assertEquals("DRAFT",authoring.lesson(teacher,lessonCopy).get("status"));
        assertEquals(false,authoring.quiz(teacher,quizCopy).get("published"));
        assertEquals(1,((List<?>)authoring.quiz(teacher,quizCopy).get("questions")).size());
        assertEquals(true,authoring.quiz(teacher,quiz).get("published"));
        assignments.create(teacher,classId,quiz,Instant.now().plusSeconds(3600));
        long assignment=((Number)assignments.forClass(teacher,classId).getFirst().get("id")).longValue();
        assertEquals(1,insights.submissionStatus(teacher,classId,assignment).size());
        assertEquals(false,insights.submissionStatus(teacher,classId,assignment).getFirst().get("completed"));
        assertTrue(insights.results(teacher,classId).stream().anyMatch(r->r.get("best_score")==null && ((Number)r.get("quiz_id")).longValue()==quiz));
        assertFalse(insights.assignments(teacher).isEmpty());
        assertFalse(((List<?>)insights.overview(teacher).get("upcoming")).isEmpty());
        assertEquals(1L,insights.classroom(teacher,classId).get("studentCount"));
        var outsider=outsider("TEACHER");
        assertTrue(insights.assignments(outsider).isEmpty());
        assertThrows(AccessDeniedException.class,()->insights.results(outsider,classId));
        assertThrows(AccessDeniedException.class,()->insights.submissionStatus(outsider,classId,assignment));
        assertThrows(AccessDeniedException.class,()->authoring.duplicateQuiz(outsider,quiz));
    }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> rows(Map<String,Object> report,String key) {return (List<Map<String,Object>>)report.get(key);}
}