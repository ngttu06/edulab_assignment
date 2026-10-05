package vn.edu.khtn8.ai;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SupportAiServiceTest {
    @Test void disabledNeverReadsCatalogReservesBudgetOrCallsProvider() {
        var jdbc=mock(JdbcTemplate.class);var settings=mock(AiSettingsService.class);var provider=mock(AiProvider.class);var quota=mock(SupportAiQuota.class);
        when(settings.current()).thenReturn(new AiSettingsService.RuntimeSettings(false,"https://gatecheap.vn/v1","model","secret",20,200,1000));
        assertThrows(ResponseStatusException.class,()->new SupportAiService(jdbc,settings,provider,quota).chat(null,"ip","guest","Q",List.of()));
        verifyNoInteractions(jdbc,provider,quota);
    }
    @Test void supportContextOnlyPublicDataAndLinksNeverComeFromAiHtml() {
        var jdbc=mock(JdbcTemplate.class);var settings=mock(AiSettingsService.class);var provider=mock(AiProvider.class);var quota=mock(SupportAiQuota.class);
        when(settings.current()).thenReturn(new AiSettingsService.RuntimeSettings(true,"https://gatecheap.vn/v1","model","secret",20,200,1000));
        when(quota.reserve(isNull(),anyString(),anyString(),any())).thenReturn(3L);
        when(jdbc.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<SupportAiService.Course>>any()))
                .thenReturn(List.of(new SupportAiService.Course(1,"Toán 8","Toán",8,"Tóm tắt",2,40)));
        when(provider.providerName()).thenReturn("gatecheap");
        when(provider.answer(any())).thenReturn(new AiProvider.AiAnswer("Toán 8 /courses/1 <script>bad</script> https://evil.test",new String[0],false));
        var answer=new SupportAiService(jdbc,settings,provider,quota).chat(null,"ip","guest","Tư vấn",List.of());
        assertEquals(List.of("/courses","/courses/1","/login"),answer.links().stream().map(SupportAiService.Link::path).toList());
        verify(provider).answer(argThat(q->q.mode().equals("SUPPORT") && q.lessonContext().contains("Chưa có")
                && q.lessonContext().contains("Toán 8") && !q.lessonContext().contains("secret")));
        verify(quota).finish(3,true);
    }
    @Test void providerFailuresConsumeQuotaAndAreRecorded() {
        var jdbc=mock(JdbcTemplate.class);var settings=mock(AiSettingsService.class);var provider=mock(AiProvider.class);var quota=mock(SupportAiQuota.class);
        when(settings.current()).thenReturn(new AiSettingsService.RuntimeSettings(true,"url","model","secret",20,200,1000));
        when(quota.reserve(isNull(),anyString(),anyString(),any())).thenReturn(4L);
        when(jdbc.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<SupportAiService.Course>>any())).thenReturn(List.of());
        when(provider.answer(any())).thenThrow(new IllegalStateException());
        assertThrows(IllegalStateException.class,()->new SupportAiService(jdbc,settings,provider,quota).chat(null,"ip","guest","Q",List.of()));
        verify(quota).finish(4,false);
    }
}