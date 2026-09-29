package vn.edu.khtn8.ai;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.common.config.AppProperties;
import vn.edu.khtn8.content.dto.LessonDetail;
import vn.edu.khtn8.content.service.LessonService;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class AiServiceTest {
    private final AuthenticatedUser user = new AuthenticatedUser(1L,"student","Student","unused",true,0,
            List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    @Test void disabledDoesNotCallProviderOrReadContext() {
        var provider = mock(AiProvider.class);
        var lessons = mock(LessonService.class);
        var jdbc = mock(JdbcTemplate.class);
        var service = new AiService(new AppProperties(),provider,lessons,jdbc);
        var error = assertThrows(ResponseStatusException.class, () -> service.ask(user,2,"Question"));
        assertEquals(503,error.getStatusCode().value());
        verifyNoInteractions(provider,lessons,jdbc);
    }
    @Test void quotaExceededDoesNotCallProvider() {
        var properties = new AppProperties(); properties.getAi().setEnabled(true);
        var provider = mock(AiProvider.class);
        var lessons = mock(LessonService.class);
        var jdbc = mock(JdbcTemplate.class);
        when(lessons.detail(1,2)).thenReturn(new LessonDetail(2L,"Topic","Title","Summary",15,"Goal","<p>Content</p>","NOT_STARTED",0));
        when(jdbc.queryForObject("SELECT COUNT(*) FROM ai_requests WHERE user_id=? AND created_at>=DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 1 DAY)",Integer.class,1L)).thenReturn(20);
        var error = assertThrows(ResponseStatusException.class, () -> new AiService(properties,provider,lessons,jdbc).ask(user,2,"Question"));
        assertEquals(429,error.getStatusCode().value());
        verifyNoInteractions(provider);
    }
}
