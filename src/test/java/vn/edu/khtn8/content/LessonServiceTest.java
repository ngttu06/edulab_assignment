package vn.edu.khtn8.content;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.content.dto.*;
import vn.edu.khtn8.content.repository.LessonRepository;
import vn.edu.khtn8.content.service.LessonService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LessonServiceTest {
    @Test void validatesProgressState() {
        var repository = mock(LessonRepository.class);
        var service = new LessonService(repository);
        for (var progress : new ProgressRequest[]{new ProgressRequest("COMPLETED",99), new ProgressRequest("NOT_STARTED",1),
                new ProgressRequest("IN_PROGRESS",100), new ProgressRequest("UNKNOWN",0), new ProgressRequest("IN_PROGRESS",-1)}) {
            assertThrows(ResponseStatusException.class, () -> service.updateProgress(1,1,progress));
        }
        verifyNoInteractions(repository);
    }
    @Test void stripsActiveHtmlAndOnlyUsesPublishedDetail() {
        var repository = mock(LessonRepository.class);
        when(repository.findPublishedDetail(4,2)).thenReturn(Optional.of(new LessonDetail(4L,"Topic","Title","Summary",30,"Objectives",
                "<p>Safe</p><script>alert(1)</script><img src=x onerror=alert(1)><a href='javascript:alert(1)'>Bad</a>","NOT_STARTED",0)));
        String html = new LessonService(repository).detail(2,4).contentHtml();
        assertTrue(html.contains("Safe"));
        assertFalse(html.contains("script"));
        assertFalse(html.contains("onerror"));
        assertFalse(html.contains("javascript:"));
    }
}
