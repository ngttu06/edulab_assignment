package vn.edu.khtn8.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.khtn8.common.web.WebAuthenticationEntryPoint;
import vn.edu.khtn8.config.AppConfig;
import vn.edu.khtn8.config.SecurityConfig;
import vn.edu.khtn8.identity.security.DatabaseUserDetailsService;
import vn.edu.khtn8.identity.security.JwtAuthenticationFilter;
import vn.edu.khtn8.identity.security.JwtService;
import vn.edu.khtn8.identity.service.AuthenticationService;
import vn.edu.khtn8.identity.service.RegistrationService;
import vn.edu.khtn8.identity.web.AuthController;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@WebMvcTest(controllers = AuthController.class, properties = "edulab.security.jwt.secret=dGVzdC1vbmx5LW5vdC1mb3ItZGVwbG95bWVudC1zZWNyZXQta2V5LTMyeA==")
@Import({SecurityConfig.class, AppConfig.class, JwtAuthenticationFilter.class, WebAuthenticationEntryPoint.class})
class AuthSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean AuthenticationService authentication;
    @MockBean RegistrationService registration;
    @MockBean DatabaseUserDetailsService users;
    @MockBean JwtService jwt;
    @MockBean vn.edu.khtn8.identity.security.LoginRateLimiter limiter;

    @Test void loginPageUsesSharedAuthenticationLayout() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk()).andExpect(view().name("layout/auth"))
                .andExpect(model().attribute("pageTitle", "Đăng nhập · EduLab"))
                .andExpect(model().attribute("contentPage", "/WEB-INF/jsp/auth/login.jsp"));
    }
    @Test void registerPageUsesSharedAuthenticationLayout() throws Exception {
        mvc.perform(get("/register"))
                .andExpect(status().isOk()).andExpect(view().name("layout/auth"))
                .andExpect(model().attribute("pageTitle", "Đăng ký · EduLab"))
                .andExpect(model().attribute("contentPage", "/WEB-INF/jsp/auth/register.jsp"));
    }
    @Test void sharedAuthenticationJspIncludesAreNotBlocked() throws Exception {
        // MockMvc has no JSP servlet: 404 means the include passed the security filter.
        for (String fragment : new String[]{"layout/header.jsp", "layout/footer.jsp", "auth/login.jsp", "auth/register.jsp"}) {
            mvc.perform(get("/WEB-INF/jsp/" + fragment).with(request -> {
                        request.setDispatcherType(DispatcherType.INCLUDE);
                        return request;
                    }))
                    .andExpect(status().isNotFound());
        }
    }
    @Test void unauthenticatedApiIs401() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }
    @Test void internalJspForwardIsNotRedirectedToLogin() throws Exception {
        // MockMvc has no JSP servlet: 404 means the forward passed the security filter.
        mvc.perform(get("/WEB-INF/jsp/layout/auth.jsp").with(request -> {
                    request.setDispatcherType(DispatcherType.FORWARD);
                    return request;
                }))
                .andExpect(status().isNotFound());
    }
    @Test void directJspRequestStillRequiresAuthentication() throws Exception {
        mvc.perform(get("/WEB-INF/jsp/auth/login.jsp"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }
    @Test void internalJspIncludeIsNotBlockedForPublicHome() throws Exception {
        mvc.perform(get("/WEB-INF/jsp/home.jsp").with(request->{request.setDispatcherType(DispatcherType.INCLUDE);return request;}))
                .andExpect(status().isNotFound());
    }
    @Test void loginWithoutCsrfIs403() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"student\",\"password\":\"password123\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
    @Test void browserCsrfEndpointSupportsLoginAndBadCredentialsReturn401() throws Exception {
        var response = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        Cookie cookie = response.getCookie("XSRF-TOKEN");
        assertNotNull(cookie);
        var token = json.readTree(response.getContentAsString());
        when(authentication.login(any(), any(), any())).thenThrow(new BadCredentialsException("wrong"));
        mvc.perform(post("/api/v1/auth/login").cookie(cookie)
                .header(token.get("headerName").asText(), token.get("token").asText())
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"student\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
