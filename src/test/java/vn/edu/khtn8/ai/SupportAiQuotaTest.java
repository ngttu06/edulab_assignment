package vn.edu.khtn8.ai;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SupportAiQuotaTest {
    private final AiSettingsService.RuntimeSettings config=new AiSettingsService.RuntimeSettings(true,"url","model","unused",20,200,1000);
    private JdbcTemplate database() {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT enabled FROM ai_settings WHERE id=1 FOR UPDATE",Boolean.class)).thenReturn(true);
        when(jdbc.queryForObject("SELECT UTC_DATE()",String.class)).thenReturn("2026-10-05");
        return jdbc;
    }
    @Test void disabledOrActiveTestRejectBeforeBudgetInsert() {
        var jdbc=database();
        when(jdbc.queryForObject(startsWith("SELECT EXISTS"),eq(Boolean.class),eq(5L))).thenReturn(true);
        var quota=new SupportAiQuota(jdbc,"test-hash-key");
        assertEquals(409,assertThrows(ResponseStatusException.class,()->quota.reserve(5L,"ip","guest",config)).getStatusCode().value());
        when(jdbc.queryForObject("SELECT enabled FROM ai_settings WHERE id=1 FOR UPDATE",Boolean.class)).thenReturn(false);
        assertEquals(503,assertThrows(ResponseStatusException.class,()->quota.reserve(null,"ip","guest",config)).getStatusCode().value());
        verify(jdbc,never()).update(any(org.springframework.jdbc.core.PreparedStatementCreator.class),any(org.springframework.jdbc.support.KeyHolder.class));
    }
    @Test void perIpMinuteLimitIgnoresGuestRotationAndNeverReserves() {
        var jdbc=database();
        when(jdbc.queryForObject(contains("(actor_hash=? OR address_hash=?)"),eq(Integer.class),any(),any(),any())).thenReturn(0);
        when(jdbc.queryForObject(contains("INTERVAL 1 MINUTE"),eq(Integer.class),any())).thenReturn(5);
        when(jdbc.queryForObject("SELECT COUNT(*) FROM support_ai_requests WHERE created_at>=?",Integer.class,"2026-10-05")).thenReturn(10);
        when(jdbc.queryForObject(contains("status='PENDING'"),eq(Integer.class))).thenReturn(0);
        var quota=new SupportAiQuota(jdbc,"test-hash-key");
        for(String guest:new String[]{"guest-one","guest-two"})
            assertEquals(429,assertThrows(ResponseStatusException.class,()->quota.reserve(null,"same-ip",guest,config)).getStatusCode().value());
        verify(jdbc,never()).update(any(org.springframework.jdbc.core.PreparedStatementCreator.class),any(org.springframework.jdbc.support.KeyHolder.class));
    }
    @Test void globalDailyBudgetAndConcurrentRequestCapBothBlock() {
        var jdbc=database();
        when(jdbc.queryForObject(contains("(actor_hash=? OR address_hash=?)"),eq(Integer.class),any(),any(),any())).thenReturn(0);
        when(jdbc.queryForObject(contains("INTERVAL 1 MINUTE"),eq(Integer.class),any())).thenReturn(0);
        when(jdbc.queryForObject("SELECT COUNT(*) FROM support_ai_requests WHERE created_at>=?",Integer.class,"2026-10-05")).thenReturn(200);
        when(jdbc.queryForObject(contains("status='PENDING'"),eq(Integer.class))).thenReturn(0);
        var quota=new SupportAiQuota(jdbc,"test-hash-key");
        assertEquals(429,assertThrows(ResponseStatusException.class,()->quota.reserve(null,"ip","guest",config)).getStatusCode().value());
        when(jdbc.queryForObject("SELECT COUNT(*) FROM support_ai_requests WHERE created_at>=?",Integer.class,"2026-10-05")).thenReturn(0);
        when(jdbc.queryForObject(contains("status='PENDING'"),eq(Integer.class))).thenReturn(4);
        assertEquals(429,assertThrows(ResponseStatusException.class,()->quota.reserve(null,"ip","guest",config)).getStatusCode().value());
    }
}