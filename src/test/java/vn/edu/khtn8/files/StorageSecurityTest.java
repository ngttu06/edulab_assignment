package vn.edu.khtn8.files;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.khtn8.common.web.WebAuthenticationEntryPoint;
import vn.edu.khtn8.config.AppConfig;
import vn.edu.khtn8.config.SecurityConfig;
import vn.edu.khtn8.identity.security.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers={FileController.class,StorageAdminController.class},properties="edulab.security.jwt.secret=dGVzdC1vbmx5LW5vdC1mb3ItZGVwbG95bWVudC1zZWNyZXQta2V5LTMyeA==")
@Import({SecurityConfig.class,AppConfig.class,JwtAuthenticationFilter.class,WebAuthenticationEntryPoint.class})
class StorageSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean FileService files;
    @MockBean DirectUploadService uploads;
    @MockBean StorageSettingsService settings;
    @MockBean R2Client r2;
    @MockBean DatabaseUserDetailsService users;
    @MockBean JwtService jwt;
    private AuthenticatedUser actor(String role) { return new AuthenticatedUser(1L,"account","Name","unused",true,0,List.of(new SimpleGrantedAuthority("ROLE_"+role))); }
    @Test void inlinePreviewAlsoRequiresLogin() throws Exception {
        mvc.perform(get("/api/v1/files/7/image")).andExpect(status().isUnauthorized());
        verifyNoInteractions(files);
    }
    @Test void presignAndCompleteNeedAuthenticationAndCsrf() throws Exception {
        String body="{\"filename\":\"a.png\",\"contentType\":\"image/png\",\"size\":9}";
        mvc.perform(post("/api/v1/files/presign").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/files/presign").with(user(actor("STUDENT"))).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/files/uploads/id/complete").with(user(actor("STUDENT")))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/files/uploads/id").with(user(actor("STUDENT")))).andExpect(status().isForbidden());
        verifyNoInteractions(uploads,r2);
    }
    @Test void invalidInputsDoNotReserveQuotaAndOldMultipartRouteIsGone() throws Exception {
        for(String body:List.of("{\"filename\":\"\",\"contentType\":\"image/png\",\"size\":1}","{\"filename\":\"a.png\",\"contentType\":\"image/png\",\"size\":0}"))
            mvc.perform(post("/api/v1/files/presign").with(user(actor("STUDENT"))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/files").with(user(actor("STUDENT"))).with(csrf())).andExpect(status().isGone());
        verifyNoInteractions(uploads,files,r2);
    }
    @Test void storageConfigurationOnlyAdminAndSecretsNeverReturned() throws Exception {
        mvc.perform(get("/api/v1/admin/storage-settings")).andExpect(status().isUnauthorized());
        for(String role:List.of("STUDENT","PARENT","TEACHER")) {
            mvc.perform(get("/api/v1/admin/storage-settings").with(user(actor(role)))).andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/admin/storage-settings").with(user(actor(role))).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/admin/storage-settings/test").with(user(actor(role))).with(csrf())).andExpect(status().isForbidden());
        }
        when(settings.view(any())).thenReturn(new StorageSettingsService.View(true,StorageEndpointPolicy.DEFAULT_ENDPOINT,"edulab","auto","edulab",true,true,5242880,10485760,104857600,false,false));
        mvc.perform(get("/api/v1/admin/storage-settings").with(user(actor("ADMIN"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.keyConfigured").value(true)).andExpect(jsonPath("$.secretKey").doesNotExist())
                .andExpect(jsonPath("$.accessKey").doesNotExist()).andExpect(jsonPath("$.encryptedSecretKey").doesNotExist());
        verifyNoInteractions(r2);
    }
}