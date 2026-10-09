package vn.edu.khtn8.ai;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.content.dto.LessonDetail;
import vn.edu.khtn8.content.service.LessonService;
import vn.edu.khtn8.content.video.*;
import vn.edu.khtn8.files.StorageSettingsService;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class VideoAiServiceTest {
    final LessonVideoService videos=mock(LessonVideoService.class);
    final LessonService lessons=mock(LessonService.class);
    final AiSettingsService settings=mock(AiSettingsService.class);
    final StorageSettingsService storage=mock(StorageSettingsService.class);
    final AiQuotaService quota=mock(AiQuotaService.class);
    final AiProvider provider=mock(AiProvider.class);
    final VideoAiService service=new VideoAiService(videos,lessons,settings,storage,quota,provider,new VideoFramePolicy());
    final AuthenticatedUser user=new AuthenticatedUser(1L,"student","Student","unused",true,0,List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    VideoAiController.Question question(VideoAiController.Frame frame) {
        return new VideoAiController.Question(2,"Vì sao?",5000,List.of(new VideoAiController.Message("user","Câu trước")),frame);
    }
    @BeforeEach void fixture() {
        when(settings.enabled()).thenReturn(true);
        when(videos.video(user,1)).thenReturn(new VideoModels.Video(2,1,1,"YOUTUBE","https://example.com/private-token","M7lc1UVf-VE",600,95));
        when(lessons.detail(1,1)).thenReturn(new LessonDetail(1L,"Chủ đề","Bài học","",10,"","<p>Nội dung</p>","NOT_STARTED",0));
        when(storage.limits()).thenReturn(new StorageSettingsService.Limits(false,null,1,1,1,true,false));
        when(provider.providerName()).thenReturn("gatecheap");
        when(quota.reserve(1,"gatecheap")).thenReturn(7L);
        when(provider.answer(any())).thenReturn(new AiProvider.AiAnswer("Hướng dẫn",new String[]{"untrusted"},true));
    }
    @Test void frameAndHistoryActuallyReachProviderWithoutSourceUrl() throws Exception {
        var result=service.chat(user,1,question(new VideoAiController.Frame(VideoFramePolicyTest.jpeg(100,60),3000)));
        assertTrue(result.frameAnalyzed());assertEquals(3000,result.positionMs());
        assertArrayEquals(new String[]{"Bài học"},result.sourceLabels());
        var call=ArgumentCaptor.forClass(AiProvider.AiQuestion.class);verify(provider).answer(call.capture());
        assertEquals(1,call.getValue().attachments().size());assertEquals("image/jpeg",call.getValue().attachments().getFirst().contentType());
        assertEquals(List.of(new AiProvider.ChatMessage("user","Câu trước")),call.getValue().history());
        assertTrue(call.getValue().lessonContext().contains("3000 ms"));
        assertFalse(call.getValue().lessonContext().contains("private-token"));verify(quota).finish(7,true);
    }
    @Test void textOnlyDoesNotPretendToSeeVideo() {
        assertFalse(service.chat(user,1,question(null)).frameAnalyzed());
        var call=ArgumentCaptor.forClass(AiProvider.AiQuestion.class);verify(provider).answer(call.capture());
        assertTrue(call.getValue().attachments().isEmpty());assertTrue(call.getValue().lessonContext().contains("Không có khung hình"));
    }
    @Test void disabledAndLockedLessonsNeverReachProvider() {
        when(settings.enabled()).thenReturn(false);
        assertEquals(503,assertThrows(ResponseStatusException.class,()->service.chat(user,1,question(null))).getStatusCode().value());
        verifyNoInteractions(videos,quota,provider);
        when(settings.enabled()).thenReturn(true);
        when(videos.video(user,1)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.chat(user,1,question(null))).getStatusCode().value());
        verifyNoInteractions(quota,provider);
    }
    @Test void staleRevisionAndInvalidTimestampAreRejected() {
        var stale=new VideoAiController.Question(3,"Q",0,List.of(),null);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.chat(user,1,stale)).getStatusCode().value());
        var beyond=new VideoAiController.Question(2,"Q",600001,List.of(),null);
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.chat(user,1,beyond)).getStatusCode().value());
        verifyNoInteractions(quota,provider);
    }
    @Test void quotaAndActiveTestGatePreventProviderCall() {
        when(quota.reserve(1,"gatecheap")).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,"TEST"));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.chat(user,1,question(null))).getStatusCode().value());
        verify(provider,never()).answer(any());
    }
    @Test void imagesDisabledAndStubDoNotConsumeQuota() throws Exception {
        var request=question(new VideoAiController.Frame(VideoFramePolicyTest.jpeg(100,60),0));
        when(storage.limits()).thenReturn(new StorageSettingsService.Limits(false,null,1,1,1,false,false));
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.chat(user,1,request)).getStatusCode().value());
        when(provider.providerName()).thenReturn("stub-local");
        assertThrows(ResponseStatusException.class,()->service.chat(user,1,request));verifyNoInteractions(quota);
    }
    @Test void invalidFrameAndProviderFailureMarkRequestFailed() {
        assertThrows(ResponseStatusException.class,()->service.chat(user,1,question(new VideoAiController.Frame("data:image/jpeg;base64,AAAA",0))));
        verify(quota).finish(7,false);verify(provider,never()).answer(any());
        when(provider.answer(any())).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
        assertThrows(ResponseStatusException.class,()->service.chat(user,1,question(null)));
        verify(quota,times(2)).finish(7,false);
    }
}