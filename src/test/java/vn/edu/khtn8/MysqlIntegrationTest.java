package vn.edu.khtn8;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.edu.khtn8.assessment.QuizService;
import vn.edu.khtn8.content.repository.LessonRepository;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import vn.edu.khtn8.school.SchoolService;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class MysqlIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");
    static final Path UPLOAD_ROOT = tempRoot();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("edulab.security.jwt.secret", () -> "dGVzdC1vbmx5LW5vdC1mb3ItZGVwbG95bWVudC1zZWNyZXQta2V5LTMyeA==");
        properties.add("edulab.upload.root", UPLOAD_ROOT::toString);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired QuizService quizzes;
    @Autowired SchoolService school;
    @Autowired LessonRepository lessons;
    @Autowired PasswordEncoder encoder;

    private long id(String username) { return jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, username); }
    private AuthenticatedUser user(String username, String role) { return new AuthenticatedUser(id(username),username,username,"unused",true,0,List.of(new SimpleGrantedAuthority("ROLE_"+role))); }

    @Test void migrationsAndDemoPasswordMatch() {
        assertEquals(15, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=TRUE", Integer.class));
        assertTrue(encoder.matches("ChangeMe123!", jdbc.queryForObject("SELECT password_hash FROM users WHERE username='hocsinh'", String.class)));
        assertEquals(8, lessons.findPublishedForStudent(id("hocsinh")).size());
        assertEquals(8L, ((Number)lessons.progressSummary(id("hocsinh")).get("total_lessons")).longValue());
    }
    @Test @WithMockUser(roles="STUDENT") void quizSnapshotsAndSubmissionRoundTrip() {
        long student = id("hocsinh");
        var attempt = quizzes.start(student, quizzes.list().getFirst().id());
        assertNull(attempt.questions().getFirst().options().getFirst().correct());
        Map<Long,Long> answers=new java.util.HashMap<>();
        for(var question:attempt.questions())answers.put(question.id(),jdbc.queryForObject("SELECT id FROM question_options WHERE question_id=? AND is_correct=TRUE",Long.class,question.id()));
        var result = quizzes.submit(student, attempt.id(), answers);
        assertEquals("GRADED",result.status());
        assertEquals(0,java.math.BigDecimal.TEN.compareTo(result.score()));
        assertEquals(result.score(), quizzes.submit(student, attempt.id(), Map.of()).score());
    }
    @Test void oneTimeParentCodeAndRevocation() {
        var student = user("hocsinh","STUDENT");
        var parent = user("phuhuynh","PARENT");
        String code = school.generateCode(student);
        school.redeem(parent,code);
        assertNotNull(school.report(parent,student.id()));
        long linkId = ((Number)school.links(parent).getFirst().get("id")).longValue();
        school.revoke(parent,linkId);
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> school.authorizeStudent(parent,student.id()));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> school.redeem(parent,code));
    }
    private static Path tempRoot() {
        try { return Files.createTempDirectory("edulab-integration-"); }
        catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
    }
}
