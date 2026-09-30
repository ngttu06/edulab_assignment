package vn.edu.khtn8.ai;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.common.config.AppProperties;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiQuotaServiceTest {
    @Test void activeTestBlocksAiBeforeQuotaReservation() {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForObject(startsWith("SELECT EXISTS"),eq(Boolean.class),eq(1L))).thenReturn(true);
        var service=new AiQuotaService(jdbc,new AppProperties());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.reserve(1,"gatecheap")).getStatusCode().value());
        verify(jdbc,never()).queryForObject(startsWith("SELECT COUNT"),eq(Integer.class),eq(1L));
    }
}