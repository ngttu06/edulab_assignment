package vn.edu.khtn8.assessment;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.ai.*;
import vn.edu.khtn8.files.*;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuizImportServiceTest {
    final FileScanner scanner=mock(FileScanner.class);
    final AiSettingsService settings=mock(AiSettingsService.class);
    final AiProvider provider=mock(AiProvider.class);
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final AiQuotaService quota=mock(AiQuotaService.class);
    final StorageSettingsService storage=mock(StorageSettingsService.class);
    final QuizImportService service=new QuizImportService(jdbc,new ObjectMapper(),scanner,storage,settings,provider,quota);
    final AuthenticatedUser teacher=new AuthenticatedUser(1L,"teacher","Teacher","unused",true,0,List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));
    @Test void preservesMissingAnswersAndRejectsInvalidJsonOrIndexes() {
        var q=service.validateQuestions(service.parse("[{\"content\":\"Câu hỏi\",\"options\":[\"A\",\"B\"],\"correctIndex\":null,\"explanation\":\"\",\"points\":1}]"));
        assertTrue(q.get(0).get("correctIndex").isNull());
        assertThrows(ResponseStatusException.class,()->service.parse("{} trailing"));
        assertThrows(ResponseStatusException.class,()->service.validateQuestions(service.parse(q.toString().replace("null","3"))));
        assertThrows(ResponseStatusException.class,()->service.validateQuestions(service.parse(q.toString().replace("null","\"0\""))));
        assertThrows(ResponseStatusException.class,()->service.validateQuestions(service.parse(q.toString().replace("null","4294967296"))));
    }
    @Test void neverSendsUnscannedFileToAi() throws Exception {
        when(settings.enabled()).thenReturn(true);when(provider.providerName()).thenReturn("gatecheap");
        when(storage.limits()).thenReturn(new StorageSettingsService.Limits(false,null,5242880,10485760,104857600,true,true));
        when(scanner.scan(any())).thenReturn("PENDING_SCAN");
        var file=new MockMultipartFile("file","test.pdf","application/pdf","%PDF-1.4".getBytes());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.extract(teacher,file)).getStatusCode().value());
        verify(provider,never()).answer(any());verifyNoInteractions(quota,jdbc);
    }
    @Test void generationRequiresOwnedSessionAndExtractionNeverGeneratesAnswers() {
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());
        assertThrows(ResponseStatusException.class,()->service.generate(teacher,"other-session"));verify(provider,never()).answer(any());
    }
}