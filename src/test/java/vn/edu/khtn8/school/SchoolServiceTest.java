package vn.edu.khtn8.school;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import vn.edu.khtn8.content.repository.LessonRepository;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchoolServiceTest {
    private AuthenticatedUser user(String role) { return new AuthenticatedUser(2L,"test","Test","hash",true,0,List.of(new SimpleGrantedAuthority("ROLE_"+role))); }
    @Test void studentCannotReadAnotherStudent() {
        var jdbc = mock(JdbcTemplate.class);
        var lessons = mock(LessonRepository.class);
        assertThrows(AccessDeniedException.class, () -> new SchoolService(jdbc,lessons).report(user("STUDENT"),3));
        verifyNoInteractions(jdbc,lessons);
    }
    @Test void parentWithoutVerifiedLinkIsDenied() {
        var service = new SchoolService(mock(JdbcTemplate.class),mock(LessonRepository.class));
        assertThrows(AccessDeniedException.class, () -> service.authorizeStudent(user("PARENT"),3));
    }
    @Test void teacherWithoutAssignedClassIsDenied() {
        var service = new SchoolService(mock(JdbcTemplate.class),mock(LessonRepository.class));
        assertThrows(AccessDeniedException.class, () -> service.authorizeStudent(user("TEACHER"),3));
    }
    @Test void parentCannotCreateStudentCode() {
        var jdbc = mock(JdbcTemplate.class);
        assertThrows(AccessDeniedException.class, () -> new SchoolService(jdbc,mock(LessonRepository.class)).generateCode(user("PARENT")));
        verifyNoInteractions(jdbc);
    }
    @Test void codeHashesAreStableAndNotPlaintext() {
        var code = "e822a80bc637391205ddedacbf00beaa";
        assertEquals(64, SchoolService.hash(code).length());
        assertEquals(SchoolService.hash(code), SchoolService.hash(code));
        assertNotEquals(code, SchoolService.hash(code));
    }
}
