package vn.edu.khtn8.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.khtn8.config.AppConfig;
import vn.edu.khtn8.config.SecurityConfig;
import vn.edu.khtn8.identity.security.DatabaseUserDetailsService;
import vn.edu.khtn8.identity.security.JwtAuthenticationFilter;
import vn.edu.khtn8.identity.security.JwtService;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=HealthController.class, properties="edulab.security.jwt.secret=dGVzdC1vbmx5LW5vdC1mb3ItZGVwbG95bWVudC1zZWNyZXQta2V5LTMyeA==")
@Import({SecurityConfig.class, AppConfig.class, JwtAuthenticationFilter.class, WebAuthenticationEntryPoint.class})
class HealthControllerTest {
    @Autowired MockMvc mvc;
    @MockBean JdbcTemplate jdbc;
    @MockBean DatabaseUserDetailsService users;
    @MockBean JwtService jwt;

    @Test void readinessIsPublicAndOnlyReportsStatus() throws Exception {
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(content().json("{\"status\":\"UP\"}"));
        mvc.perform(get("/api/v1/private")).andExpect(status().isUnauthorized());
    }
    @Test void databaseFailureReturns503WithoutLeakingDetails() throws Exception {
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenThrow(new DataAccessResourceFailureException("private database credentials"));
        mvc.perform(get("/actuator/health")).andExpect(status().isServiceUnavailable()).andExpect(content().json("{\"status\":\"DOWN\"}"));
    }
    @Test void unexpectedProbeResultIsNotHealthy() throws Exception {
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(null);
        mvc.perform(get("/actuator/health")).andExpect(status().isServiceUnavailable()).andExpect(content().json("{\"status\":\"DOWN\"}"));
    }
}