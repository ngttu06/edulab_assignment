package vn.edu.khtn8.ai;

import java.util.List;
import org.junit.jupiter.api.Test;
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
        var quota = mock(AiQuotaService.class);
        var service = new AiService(new AppProperties(),provider,lessons,quota,mock(vn.edu.khtn8.content.service.LearningPathService.class));
        var error = assertThrows(ResponseStatusException.class, () -> service.ask(user,2,"Question"));
        assertEquals(503,error.getStatusCode().value());
        verifyNoInteractions(provider,lessons,quota);
    }
    @Test void quotaExceededDoesNotCallProvider() {
        var properties = new AppProperties(); properties.getAi().setEnabled(true);
        var provider = mock(AiProvider.class);
        var lessons = mock(LessonService.class);
        var quota = mock(AiQuotaService.class);
        when(lessons.detail(1,2)).thenReturn(new LessonDetail(2L,"Topic","Title","Summary",15,"Goal","<p>Content</p>","NOT_STARTED",0));
        when(provider.providerName()).thenReturn("gatecheap");
        when(quota.reserve(1,"gatecheap")).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));
        var error = assertThrows(ResponseStatusException.class, () -> new AiService(properties,provider,lessons,quota,mock(vn.edu.khtn8.content.service.LearningPathService.class)).ask(user,2,"Question"));
        assertEquals(429,error.getStatusCode().value());
        verify(provider,never()).answer(any());
    }

    @Test void realAnswerHasPublishedSourceAndNoSampleLabel() {
        var properties = new AppProperties(); properties.getAi().setEnabled(true);
        var provider = mock(AiProvider.class); var lessons = mock(LessonService.class); var quota = mock(AiQuotaService.class);
        when(provider.providerName()).thenReturn("gatecheap"); when(quota.reserve(1,"gatecheap")).thenReturn(7L);
        when(lessons.detail(1,2)).thenReturn(new LessonDetail(2L,"Topic","Title","Summary",15,"Goal","<p>Content</p>","NOT_STARTED",0));
        when(provider.answer(any())).thenReturn(new AiProvider.AiAnswer("Answer",new String[]{"Untrusted source"},true));
        var result = new AiService(properties,provider,lessons,quota,mock(vn.edu.khtn8.content.service.LearningPathService.class)).ask(user,2,"Question");
        assertFalse(result.sample()); assertArrayEquals(new String[]{"Title"},result.sourceLabels());
        verify(provider).answer(new AiProvider.AiQuestion("Question","Môn học: ; khối lớp: 0; bài: Title\nContent","HINT")); verify(quota).finish(7,true);
    }

    @Test void failureIsRecorded() {
        var properties = new AppProperties(); properties.getAi().setEnabled(true);
        var provider = mock(AiProvider.class); var lessons = mock(LessonService.class); var quota = mock(AiQuotaService.class);
        when(provider.providerName()).thenReturn("gatecheap"); when(quota.reserve(1,"gatecheap")).thenReturn(7L);
        when(lessons.detail(1,2)).thenReturn(new LessonDetail(2L,"Topic","Title","Summary",15,"Goal","Content","NOT_STARTED",0));
        when(provider.answer(any())).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
        assertThrows(ResponseStatusException.class, () -> new AiService(properties,provider,lessons,quota,mock(vn.edu.khtn8.content.service.LearningPathService.class)).ask(user,2,"Question"));
        verify(quota).finish(7,false);
    }
    @Test void runtimeToggleOverridesLegacyConfigurationForLessonAndForumCallers() {
        var provider=mock(AiProvider.class); var lessons=mock(LessonService.class); var quota=mock(AiQuotaService.class);
        var runtime=mock(AiSettingsService.class);
        var service=new AiService(new AppProperties(),provider,lessons,quota,mock(vn.edu.khtn8.content.service.LearningPathService.class));
        org.springframework.test.util.ReflectionTestUtils.setField(service,"runtimeSettings",runtime);
        when(runtime.enabled()).thenReturn(true);
        when(provider.providerName()).thenReturn("gatecheap"); when(quota.reserve(1,"gatecheap")).thenReturn(7L);
        when(lessons.detail(1,2)).thenReturn(new LessonDetail(2L,"Topic","Title","Summary",15,"Goal","<p>Content</p>","NOT_STARTED",0));
        when(provider.answer(any())).thenReturn(new AiProvider.AiAnswer("Answer",new String[0],true));
        assertEquals("Answer",service.ask(user,2,"Question").text());
        when(runtime.enabled()).thenReturn(false);
        assertEquals(503,assertThrows(ResponseStatusException.class,()->service.ask(user,2,"Question")).getStatusCode().value());
        verify(provider,times(1)).answer(any());verify(quota,times(1)).reserve(1,"gatecheap");
    }
}
