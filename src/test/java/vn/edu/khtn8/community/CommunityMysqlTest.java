package vn.edu.khtn8.community;

import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import vn.edu.khtn8.content.service.CatalogService;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named="EDULAB_TEST_DB_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:3307/edulab_test.*")
@Transactional
class CommunityMysqlTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->System.getenv("EDULAB_TEST_DB_URL"));
        p.add("spring.datasource.username",()->"edulab_test");p.add("spring.datasource.password",()->"local-test-only-password");
        p.add("edulab.security.jwt.secret",()->"dGVzdC1vbmx5LW5vdC1mb3ItZGVwbG95bWVudC1zZWNyZXQta2V5LTMyeA==");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired ForumService forum;
    @Autowired CatalogService catalog;
    AuthenticatedUser user(String name,String role) {
        long id=jdbc.queryForObject("SELECT id FROM users WHERE username=?",Long.class,name);
        return new AuthenticatedUser(id,name,name,"unused",true,0,List.of(new SimpleGrantedAuthority("ROLE_"+role)));
    }
    long category() { return jdbc.queryForObject("SELECT MIN(id) FROM forum_categories WHERE classroom_id IS NULL AND active=TRUE",Long.class); }
    long number(Object value) { return ((Number)value).longValue(); }
    @Test void cursorFeedIsDescendingStableAndExcludesHiddenThreads() {
        var student=user("hocsinh","STUDENT");long category=category();
        long first=forum.create(student,category,"First","Body");
        long second=forum.create(student,category,"Second","Body");
        long third=forum.create(student,category,"Third","Body");
        long hidden=forum.create(student,category,"Hidden","Private body");
        forum.moderate(user("admin","ADMIN"),hidden,"HIDDEN");
        var page=forum.feed(student,category,null,2);
        assertEquals(List.of(third,second),page.items().stream().map(i->number(i.get("id"))).toList());
        assertEquals(second,page.nextCursor());assertTrue(page.hasMore());
        forum.create(student,category,"Newer","Body");
        var next=forum.feed(student,category,page.nextCursor(),2);
        assertEquals(first,number(next.items().getFirst().get("id")));
        assertTrue(next.items().stream().allMatch(i->number(i.get("id"))<second));
        assertEquals(400,assertThrows(ResponseStatusException.class,()->forum.feed(student,null,0L,10)).getStatusCode().value());
        assertThrows(ResponseStatusException.class,()->forum.feed(student,null,null,21));
    }
    @Test void classPermissionsApplyToFeedCommentsLikesAndInactiveCategories() {
        var student=user("hocsinh","STUDENT");var other=user("phuhuynh","PARENT");var admin=user("admin","ADMIN");
        long classroom=jdbc.queryForObject("SELECT MIN(id) FROM classrooms",Long.class);
        jdbc.update("INSERT INTO forum_categories(name,classroom_id) VALUES('Private test',?)",classroom);
        long category=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        long thread=forum.create(admin,category,"Private","Do not leak");
        assertTrue(forum.feed(other,null,null,20).items().stream().noneMatch(t->number(t.get("id"))==thread));
        assertEquals(404,assertThrows(ResponseStatusException.class,()->forum.feed(other,category,null,10)).getStatusCode().value());
        assertThrows(ResponseStatusException.class,()->forum.comments(other,thread,null,10));
        assertThrows(ResponseStatusException.class,()->forum.like(other,thread,true));
        assertDoesNotThrow(()->forum.feed(student,category,null,10));
        jdbc.update("UPDATE forum_categories SET active=FALSE WHERE id=?",category);
        assertThrows(ResponseStatusException.class,()->forum.like(admin,thread,true));
    }
    @Test void likesArePersistentIdempotentAndHiddenOrLockedRulesAreEnforced() {
        var student=user("hocsinh","STUDENT");var other=user("phuhuynh","PARENT");var admin=user("admin","ADMIN");
        long thread=forum.create(student,category(),"Likes","Body");
        assertEquals(new ForumService.LikeState(true,1),forum.like(student,thread,true));
        assertEquals(new ForumService.LikeState(true,1),forum.like(student,thread,true));
        assertEquals(2,forum.like(other,thread,true).count());
        var item=forum.feed(student,null,null,10).items().stream().filter(t->number(t.get("id"))==thread).findFirst().orElseThrow();
        assertEquals(true,item.get("liked"));assertEquals(2L,item.get("like_count"));
        assertEquals(1,forum.like(student,thread,false).count());assertEquals(1,forum.like(student,thread,false).count());
        forum.moderate(admin,thread,"LOCKED");
        assertDoesNotThrow(()->forum.like(student,thread,true));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->forum.reply(student,thread,"No",List.of())).getStatusCode().value());
        forum.moderate(admin,thread,"HIDDEN");
        assertThrows(ResponseStatusException.class,()->forum.like(student,thread,true));
        assertThrows(ResponseStatusException.class,()->forum.comments(student,thread,null,10));
    }
    @Test void commentCursorIsAscendingAndCountsOnlyPublishedReplies() {
        var student=user("hocsinh","STUDENT");long thread=forum.create(student,category(),"Comments","Body");
        long first=forum.reply(student,thread,"<script>text only</script>",List.of());
        long hidden=forum.reply(student,thread,"Hidden",List.of());
        long last=forum.reply(student,thread,"Last",List.of());
        jdbc.update("UPDATE forum_posts SET status='HIDDEN' WHERE id=?",hidden);
        var page=forum.comments(student,thread,null,1);
        assertEquals(first,number(page.items().getFirst().get("id")));assertTrue(page.hasMore());
        assertEquals("<script>text only</script>",page.items().getFirst().get("body"));
        assertEquals(List.of(),page.items().getFirst().get("files"));
        var next=forum.comments(student,thread,page.nextCursor(),1);
        assertEquals(last,number(next.items().getFirst().get("id")));assertFalse(next.hasMore());
        var item=forum.feed(student,null,null,10).items().stream().filter(t->number(t.get("id"))==thread).findFirst().orElseThrow();
        assertEquals(2L,item.get("comment_count"));
        assertThrows(ResponseStatusException.class,()->forum.comments(student,thread,-1L,10));
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentDuplicateLikesCreateExactlyOneRow() throws Exception {
        var student=user("hocsinh","STUDENT");long thread=forum.create(student,category(),"Concurrent likes test","Body");
        try (var executor=Executors.newFixedThreadPool(4)) {
            var tasks=java.util.stream.IntStream.range(0,8).<java.util.concurrent.Callable<ForumService.LikeState>>mapToObj(i->()->forum.like(student,thread,true)).toList();
            for (var result:executor.invokeAll(tasks)) assertEquals(1,result.get().count());
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM forum_thread_likes WHERE thread_id=?",Integer.class,thread));
        } finally { jdbc.update("DELETE FROM forum_thread_likes WHERE thread_id=?",thread);jdbc.update("DELETE FROM forum_threads WHERE id=?",thread); }
    }
    @Test void homepageCountsAreUncappedAndTeachersComeFromActualCourseAssignments() {
        long course=jdbc.queryForObject("SELECT MIN(id) FROM courses",Long.class);
        long teacherId=user("giaovien","TEACHER").id();
        jdbc.update("UPDATE courses SET teacher_id=? WHERE id=?",teacherId,course);
        for (int i=0;i<61;i++) jdbc.update("INSERT INTO courses(subject_id,title,summary) SELECT subject_id,?,summary FROM courses WHERE id=?","Count fixture "+i,course);
        var stats=catalog.publicStatistics();
        assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM courses",Long.class),number(stats.get("courses")));
        assertTrue(number(stats.get("courses"))>60);
        assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM lessons l WHERE status='PUBLISHED' AND EXISTS(SELECT 1 FROM lesson_versions v WHERE v.lesson_id=l.id AND v.status='PUBLISHED')",Long.class),number(stats.get("lessons")));
        for (var teacher:catalog.publicTeachers()) assertNotNull(catalog.course(0,number(teacher.get("course_id"))));
        assertTrue(catalog.publicTeachers().stream().anyMatch(t->number(t.get("course_id"))==course));
    }
}