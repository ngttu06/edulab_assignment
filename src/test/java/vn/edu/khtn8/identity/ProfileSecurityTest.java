package vn.edu.khtn8.identity;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.common.web.WebAuthenticationEntryPoint;
import vn.edu.khtn8.config.AppConfig;
import vn.edu.khtn8.config.SecurityConfig;
import vn.edu.khtn8.identity.dto.ProfileResponse;
import vn.edu.khtn8.identity.dto.UpdateProfileRequest;
import vn.edu.khtn8.identity.security.*;
import vn.edu.khtn8.identity.service.ProfileService;
import vn.edu.khtn8.identity.web.ProfileController;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ProfileController.class, properties = "edulab.security.jwt.secret=dGVzdC1vbmx5LW5vdC1mb3ItZGVwbG95bWVudC1zZWNyZXQta2V5LTMyeA==")
@Import({SecurityConfig.class, AppConfig.class, JwtAuthenticationFilter.class, WebAuthenticationEntryPoint.class})
class ProfileSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean ProfileService profiles;
    @MockBean LoginRateLimiter limiter;
    @MockBean DatabaseUserDetailsService users;
    @MockBean JwtService jwt;

    private AuthenticatedUser principal(String role) {
        return new AuthenticatedUser(7L, "account", "Tên", "private-hash", true, 0,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test void anonymousUsersCannotAccessProfilePageOrApi() throws Exception {
        mvc.perform(get("/profile")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/api/v1/profile")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/profile").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Tên\",\"email\":\"name@example.test\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/profile/password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"Password123!\",\"newPassword\":\"NewPassword123!\",\"confirmPassword\":\"NewPassword123!\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(profiles);
    }

    @Test void everyRoleCanReadItsOwnProfileInSharedLayout() throws Exception {
        for (String role : new String[]{"STUDENT", "PARENT", "TEACHER", "ADMIN"}) {
            var principal = principal(role);
            var response = new ProfileResponse(7L, "account", "Tên", "name@example.test", role);
            when(profiles.profile(principal)).thenReturn(response);
            mvc.perform(get("/profile").with(user(principal)))
                    .andExpect(status().isOk()).andExpect(view().name("layout/default"))
                    .andExpect(model().attribute("profile", response))
                    .andExpect(model().attribute("contentPage", "/WEB-INF/jsp/profile.jsp"));
            mvc.perform(get("/api/v1/profile").with(user(principal)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("name@example.test"))
                    .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.authVersion").doesNotExist());
        }
    }

    @Test void mutationsRequireCsrf() throws Exception {
        mvc.perform(put("/api/v1/profile").with(user(principal("STUDENT"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/profile/password").with(user(principal("STUDENT"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(profiles);
    }

    @Test void invalidProfileAndPasswordInputsNeverReachService() throws Exception {
        for (String body : new String[]{"{\"fullName\":\"   \",\"email\":\"a@example.test\"}",
                "{\"fullName\":\"Tên\",\"email\":\"invalid\"}", "{\"fullName\":\"Tên\",\"email\":\"\"}",
                "{\"fullName\":\"" + "a".repeat(161) + "\",\"email\":\"a@example.test\"}"}) {
            mvc.perform(put("/api/v1/profile").with(user(principal("STUDENT"))).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        for (String next : new String[]{"short", " ", "a".repeat(73)}) {
            mvc.perform(post("/api/v1/profile/password").with(user(principal("STUDENT"))).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"Password123!\",\"newPassword\":\"" + next + "\",\"confirmPassword\":\"" + next + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(profiles, limiter);
    }

    @Test void updateUsesPrincipalIdAndIgnoresSubmittedIdentityAndRole() throws Exception {
        var principal = principal("STUDENT");
        var input = new UpdateProfileRequest("Tên mới", "name@example.test");
        when(profiles.update(principal, input)).thenReturn(new ProfileResponse(7L, "account", "Tên mới", "name@example.test", "STUDENT"));
        mvc.perform(put("/api/v1/profile").with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":999,\"username\":\"admin\",\"role\":\"ADMIN\",\"fullName\":\"Tên mới\",\"email\":\"name@example.test\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(7)).andExpect(jsonPath("$.role").value("STUDENT"));
        verify(profiles).update(principal, input);
    }

    @Test void validPasswordChangeIsRateLimitedAndReturnsNoContent() throws Exception {
        mvc.perform(post("/api/v1/profile/password").with(user(principal("STUDENT"))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"Password123!\",\"newPassword\":\"NewPassword123!\",\"confirmPassword\":\"NewPassword123!\"}"))
                .andExpect(status().isNoContent());
        verify(limiter).check("password:7", "Quá nhiều lần đổi mật khẩu. Vui lòng đợi một phút.");
        verify(profiles).changePassword(eq(principal("STUDENT")), any(), any(), any());
    }

    @Test void exhaustedPasswordRateLimitDoesNotInvokeService() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Vui lòng đợi một phút."))
                .when(limiter).check(anyString(), anyString());
        mvc.perform(post("/api/v1/profile/password").with(user(principal("STUDENT"))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"Password123!\",\"newPassword\":\"NewPassword123!\",\"confirmPassword\":\"NewPassword123!\"}"))
                .andExpect(status().isTooManyRequests());
        verifyNoInteractions(profiles);
    }
}