package vn.edu.khtn8.community;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.ai.AiService;
import vn.edu.khtn8.content.service.LessonService;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForumAiTest {
    private final AuthenticatedUser user=new AuthenticatedUser(1L,"student","Student","unused",true,0,List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    private final ForumService forum=mock(ForumService.class);
    private final AiService ai=mock(AiService.class);
    private final ForumController controller=new ForumController(forum,ai,mock(LessonService.class));
    @Test void failedOrDisabledAiKeepsTheCreatedThreadWithoutSavingAnAnswer() {
        when(forum.create(user,2,"Title","Body")).thenReturn(9L);
        when(ai.ask(user,3,"Title\nBody")).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
        var result=controller.create(user,new ForumController.ThreadRequest(2,"Title","Body",3L,true));
        assertEquals(9L,result.get("id"));assertEquals(true,result.get("aiUnavailable"));
        verify(forum).create(user,2,"Title","Body");verify(forum,never()).saveAi(any(),anyLong(),anyString(),anyString());
    }
    @Test void forumUsesLessonAssistantAndSavesServerVerifiedSourceLabels() {
        when(forum.create(eq(user),eq(2L),eq("Title"),anyString())).thenReturn(9L);
        when(ai.ask(eq(user),eq(3L),anyString())).thenReturn(new AiService.Answer("gatecheap",false,"Answer",new String[]{"Published lesson"},true));
        var result=controller.create(user,new ForumController.ThreadRequest(2,"Title","x".repeat(3000),3L,true));
        assertEquals(false,result.get("aiUnavailable"));
        verify(ai).ask(user,3,("Title\n"+"x".repeat(3000)).substring(0,2000));
        verify(forum).saveAi(user,9,"Answer","Published lesson");
    }
    @Test void noOptInOrNoLessonNeverInvokesAi() {
        controller.create(user,new ForumController.ThreadRequest(2,"Title","Body",3L,false));
        controller.create(user,new ForumController.ThreadRequest(2,"Title","Body",null,true));
        verifyNoInteractions(ai);verify(forum,never()).saveAi(any(),anyLong(),anyString(),anyString());
    }
}