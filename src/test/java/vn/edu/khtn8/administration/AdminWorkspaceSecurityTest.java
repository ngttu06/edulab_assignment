package vn.edu.khtn8.administration;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.khtn8.common.web.WebAuthenticationEntryPoint;
import vn.edu.khtn8.config.*;
import vn.edu.khtn8.identity.security.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=AdminWorkspaceController.class,properties="edulab.security.jwt.secret=dGVzdC1vbmx5LW5vdC1mb3ItZGVwbG95bWVudC1zZWNyZXQta2V5LTMyeA==")
@Import({SecurityConfig.class,AppConfig.class,JwtAuthenticationFilter.class,WebAuthenticationEntryPoint.class})
class AdminWorkspaceSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean AdminWorkspaceService service;
    @MockBean DatabaseUserDetailsService users;
    @MockBean JwtService jwt;
    private AuthenticatedUser actor(String role) {return new AuthenticatedUser(1L,"test","Test","unused",true,0,List.of(new SimpleGrantedAuthority("ROLE_"+role)));}
    @Test void onlyAdminCanUseWorkspacePagesAndApis() throws Exception {
        for(String role:List.of("TEACHER","STUDENT","PARENT")) {
            for(String path:List.of("/admin","/admin/teachers","/admin/classes/1","/admin/settings/ai","/api/v1/admin/workspace/teachers/1"))
                mvc.perform(get(path).with(user(actor(role)))).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/admin/workspace/teachers").with(user(actor(role))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
    @Test void mutationsRequireCsrfAndUseAdminNamespace() throws Exception {
        mvc.perform(delete("/api/v1/admin/workspace/teachers/2").with(user(actor("ADMIN")))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/admin/workspace/teachers/2").with(user(actor("ADMIN"))).with(csrf())).andExpect(status().isOk());
        verify(service).remove(any(),eq("teachers"),eq(2L));
    }
    @Test void dashboardUsesIndependentLayoutAndNoStore() throws Exception {
        when(service.overview(any())).thenReturn(Map.of());
        mvc.perform(get("/admin").with(user(actor("ADMIN")))).andExpect(status().isOk()).andExpect(view().name("layout/admin"))
                .andExpect(header().string("Cache-Control","private, no-store")).andExpect(model().attribute("adminActive","dashboard"));
    }
    @Test void invalidAssignmentsAreRejectedBeforeService() throws Exception {
        mvc.perform(post("/api/v1/admin/workspace/teaching-assignments").with(user(actor("ADMIN"))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"classId\":0,\"teacherId\":-1,\"homeroom\":true}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void unknownResourcesAndReadOnlyFormsCannotBeEdited() throws Exception {
        mvc.perform(get("/admin/not-a-table").with(user(actor("ADMIN")))).andExpect(status().isNotFound());
        mvc.perform(get("/admin/audit-logs/new").with(user(actor("ADMIN")))).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }
}