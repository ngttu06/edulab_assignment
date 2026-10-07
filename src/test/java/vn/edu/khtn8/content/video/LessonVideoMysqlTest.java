package vn.edu.khtn8.content.video;

import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.administration.*;
import vn.edu.khtn8.content.dto.ProgressRequest;
import vn.edu.khtn8.content.service.*;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.khtn8.content.video.VideoModels.*;

@SpringBootTest(properties="EDULAB_VIDEO_ALLOWED_HOSTS=media.example.com")
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named="EDULAB_TEST_DB_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:3307/edulab_test.*")
@Transactional
class LessonVideoMysqlTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->System.getenv("EDULAB_TEST_DB_URL"));
        p.add("spring.datasource.username",()->"edulab_test"); p.add("spring.datasource.password",()->"local-test-only-password");
        p.add("edulab.security.jwt.secret",()->"dGVzdC1vbmx5LW5vdC1mb3ItZGVwbG95bWVudC1zZWNyZXQta2V5LTMyeA==");
        p.add("edulab.upload.root",()->{ try { return Files.createTempDirectory("edulab-video-test-").toString(); } catch(Exception e) { throw new IllegalStateException(e); } });
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired LessonVideoService videos;
    @Autowired AdminService admin;
    @Autowired LessonService lessons;
    @Autowired LearningPathService path;
    AuthenticatedUser user(String name,String role) {
        long id=jdbc.queryForObject("SELECT id FROM users WHERE username=?",Long.class,name);
        return new AuthenticatedUser(id,name,name,"unused",true,0,List.of(new SimpleGrantedAuthority("ROLE_"+role)));
    }
    long lesson() {
        long topic=jdbc.queryForObject("SELECT MIN(id) FROM topics",Long.class);
        long id=admin.createLesson(user("admin","ADMIN"),new AdminController.LessonInput(topic,"Video test","Summary",10,"Objectives","<p>Content</p>",null,0));
        admin.publishLesson(user("admin","ADMIN"),id,true); return id;
    }
    @Test void teacherNeedsAssignmentAndCannotDelegateOrReport() {
        long id=lesson(); var teacher=user("giaovien","TEACHER"); var actor=user("admin","ADMIN");
        var input=new Input("YOUTUBE","https://youtu.be/M7lc1UVf-VE",600,95);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->videos.save(teacher,id,input));
        assertFalse(videos.manageable(teacher).stream().anyMatch(r->((Number)r.get("id")).longValue()==id));
        videos.assign(actor,id,teacher.id());
        var video=videos.save(teacher,id,input);
        assertEquals("YOUTUBE",video.provider());
        assertTrue(lessons.detail(teacher.id(),id).hasVideo());
        assertTrue(videos.manageable(teacher).stream().anyMatch(r->((Number)r.get("id")).longValue()==id));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->videos.assign(teacher,id,null));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->videos.begin(teacher,id));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->videos.configuration(user("phuhuynh","PARENT"),id));
        videos.assign(actor,id,null);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->videos.remove(teacher,id));
    }
    @Test @WithMockUser(roles="STUDENT") void tracksUnionResumeRetryAndRejectsForgedReports() {
        long id=lesson(); var actor=user("admin","ADMIN"); var student=user("hocsinh","STUDENT");
        var video=videos.save(actor,id,new Input("YOUTUBE","https://youtu.be/M7lc1UVf-VE",600,95));
        assertThrows(ResponseStatusException.class,()->lessons.updateProgress(student.id(),id,new ProgressRequest("COMPLETED",100)));
        var session=videos.begin(student,id);
        var first=videos.report(student,id,new Report(session.sessionId(),1,1000,600000,List.of(new Range(0,1000))));
        assertEquals(1000,first.watchedMs()); assertEquals("IN_PROGRESS",first.status());
        var retry=videos.report(student,id,new Report(session.sessionId(),1,1000,600000,List.of(new Range(0,1000))));
        assertEquals(first,retry);
        jdbc.update("UPDATE video_watch_sessions SET last_report_at=DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 10 SECOND) WHERE id=?",session.sessionId());
        var second=videos.report(student,id,new Report(session.sessionId(),2,305000,600000,List.of(new Range(300000,305000),new Range(0,1000))));
        assertEquals(6000,second.watchedMs()); assertEquals(305000,second.positionMs());
        assertEquals(List.of(new Range(0,1000),new Range(300000,305000)),second.ranges());
        assertEquals(second,videos.progress(student,id));
        assertEquals(1,lessons.detail(student.id(),id).completionPercent());
        assertThrows(ResponseStatusException.class,()->videos.report(student,id,new Report(session.sessionId(),3,600000,600000,List.of(new Range(0,600000)))));
        assertThrows(ResponseStatusException.class,()->videos.report(student,id,new Report(session.sessionId(),3,1000,1000,List.of())));
        var next=videos.begin(student,id);
        assertEquals(second,next.progress());
        assertThrows(ResponseStatusException.class,()->videos.report(student,id,new Report(session.sessionId(),3,1000,600000,List.of())));
        assertThrows(ResponseStatusException.class,()->videos.report(student,id,new Report(next.sessionId(),2,1000,600000,List.of())));
        assertEquals(video.id(),second.videoId());
    }
    @Test @WithMockUser(roles="STUDENT") void completionUnlocksPrerequisiteAndNewRevisionPreservesHistory() {
        long id=lesson(); var actor=user("admin","ADMIN"); var student=user("hocsinh","STUDENT");
        long dependent=lesson(); jdbc.update("UPDATE lessons SET prerequisite_id=? WHERE id=?",id,dependent);
        var video=videos.save(actor,id,new Input("PLAYERJS","https://media.example.com/test.m3u8",10,95));
        assertThrows(ResponseStatusException.class,()->path.requireUnlocked(student.id(),dependent));
        var session=videos.begin(student,id);
        jdbc.update("UPDATE video_watch_sessions SET last_report_at=DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 10 SECOND) WHERE id=?",session.sessionId());
        var done=videos.report(student,id,new Report(session.sessionId(),1,9500,10000,List.of(new Range(0,9500))));
        assertEquals("COMPLETED",done.status()); assertEquals(95,done.completionPercent());
        assertDoesNotThrow(()->path.requireUnlocked(student.id(),dependent));
        videos.save(actor,id,new Input("YOUTUBE","https://youtu.be/M7lc1UVf-VE",20,95));
        assertEquals(0,videos.progress(student,id).watchedMs());
        assertEquals(0,lessons.detail(student.id(),id).completionPercent());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM video_progress WHERE student_id=? AND video_id=?",Integer.class,student.id(),video.id()));
        assertThrows(ResponseStatusException.class,()->path.requireUnlocked(student.id(),dependent));
        videos.remove(actor,id); assertFalse(lessons.detail(student.id(),id).hasVideo());
        assertDoesNotThrow(()->lessons.updateProgress(student.id(),id,new ProgressRequest("COMPLETED",100)));
    }
}