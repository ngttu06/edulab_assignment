package vn.edu.khtn8.content.video;

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
import vn.edu.khtn8.config.*;
import vn.edu.khtn8.content.service.LessonService;
import vn.edu.khtn8.identity.security.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@WebMvcTest(controllers=LessonVideoController.class,properties={"edulab.security.jwt.secret=dGVzdC1vbmx5LW5vdC1mb3ItZGVwbG95bWVudC1zZWNyZXQta2V5LTMyeA==","EDULAB_VIDEO_ALLOWED_HOSTS="})
@Import({SecurityConfig.class,AppConfig.class,JwtAuthenticationFilter.class,WebAuthenticationEntryPoint.class,VideoPolicy.class})
class LessonVideoSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean LessonVideoService videos;
    @MockBean LessonService lessons;
    @MockBean DatabaseUserDetailsService users;
    @MockBean JwtService jwt;
    AuthenticatedUser actor(String role) { return new AuthenticatedUser(1L,"test","Test","unused",true,0,List.of(new SimpleGrantedAuthority("ROLE_"+role))); }
    @Test void authenticationRoleAndCsrfRemainRequired() throws Exception {
        mvc.perform(get("/lessons/1/video")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/api/v1/lessons/1/video/progress")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/lessons/1/video/sessions").with(user(actor("STUDENT")))).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/teacher/lessons/1/video").with(user(actor("STUDENT"))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/lessons/1/video-teacher").with(user(actor("TEACHER"))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"teacherId\":1}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(videos);
    }
    @Test void headersOnlyPermitVideoSourcesOnVideoPages() throws Exception {
        when(videos.video(any(),eq(1L))).thenReturn(new VideoModels.Video(2,1,1,"YOUTUBE","https://www.youtube.com/watch?v=M7lc1UVf-VE","M7lc1UVf-VE",600,95));
        mvc.perform(get("/lessons/1/video").with(user(actor("STUDENT"))))
                .andExpect(status().isOk()).andExpect(header().string("Content-Security-Policy",containsString("frame-src https://www.youtube.com")))
                .andExpect(header().string("Content-Security-Policy",containsString("media-src 'self' blob: https:;")))
                .andExpect(header().string("Content-Security-Policy",containsString(" https: https://www.youtube.com; script-src 'self' 'unsafe-eval' https://cdn.jsdelivr.net https://www.youtube.com;")))
                .andExpect(header().string("Referrer-Policy","strict-origin-when-cross-origin"))
                .andExpect(header().string("Cache-Control","private, no-store"));
        mvc.perform(get("/api/v1/lessons/1/video/progress").with(user(actor("STUDENT"))))
                .andExpect(header().string("Content-Security-Policy",not(containsString("unsafe-eval"))))
                .andExpect(header().string("Content-Security-Policy",not(containsString("unsafe-inline"))))
                .andExpect(header().string("Content-Security-Policy",not(containsString("media-src"))))
                .andExpect(header().string("Content-Security-Policy",not(containsString(" https:;"))))
                .andExpect(header().string("Referrer-Policy","same-origin"));
        for (String route:List.of("/teacher/videos","/admin/videos"))
            mvc.perform(get(route).with(user(actor("ADMIN"))))
                    .andExpect(status().isOk()).andExpect(model().attribute("videoAllHosts",true))
                    .andExpect(model().attribute("playerAvailable",true))
                    .andExpect(header().string("Content-Security-Policy",containsString("media-src 'self' blob: https:;")))
                    .andExpect(header().string("Content-Security-Policy",containsString("frame-src https://www.youtube.com https://www.youtube-nocookie.com;")));
    }
    @Test void playerEvalPermissionNeverLeaksIntoOtherPagesOrAssets() throws Exception {
        for (String route:List.of("/", "/login", "/profile", "/js/video-player.js", "/js/vendor/playerjs.js"))
            mvc.perform(get(route).with(user(actor("ADMIN"))))
                    .andExpect(header().string("Content-Security-Policy",not(containsString("unsafe-eval"))))
                    .andExpect(header().string("Content-Security-Policy",not(containsString("script-src 'self' 'unsafe-inline'"))));
    }
    @Test void administratorsAndTeachersCanValidateAnyDomainHlsAndMp4WithCsrf() throws Exception {
        for (String scope:List.of("admin","teacher")) for (String extension:List.of("m3u8","mp4")) {
            String url="https://unconfigured.example.org/lesson."+extension+"?token=abc%2B123";
            String body="{\"provider\":\"PLAYERJS\",\"sourceUrl\":\""+url+"\",\"durationSeconds\":6,\"completionThreshold\":95}";
            mvc.perform(post("/api/v1/"+scope+"/videos/validate").with(user(actor(scope.toUpperCase(java.util.Locale.ROOT))))
                            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.url").value(url));
            mvc.perform(post("/api/v1/"+scope+"/videos/validate").with(user(actor(scope.toUpperCase(java.util.Locale.ROOT))))
                            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/teacher/videos/validate").with(user(actor("STUDENT"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"PLAYERJS\",\"sourceUrl\":\"https://video.example.com/x.mp4\",\"durationSeconds\":6,\"completionThreshold\":95}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(videos);
    }
    @Test void rejectsInvalidPayloadAndCallsServiceForValidReport() throws Exception {
        String invalid="{\"sessionId\":\"bad\",\"sequence\":0,\"durationMs\":0,\"ranges\":[]}";
        mvc.perform(post("/api/v1/lessons/1/video/progress").with(user(actor("STUDENT"))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        verifyNoInteractions(videos);
        String valid="{\"sessionId\":\"12345678-1234-1234-1234-123456789012\",\"sequence\":1,\"positionMs\":1000,\"durationMs\":10000,\"ranges\":[{\"startMs\":0,\"endMs\":1000}]}";
        mvc.perform(post("/api/v1/lessons/1/video/progress").with(user(actor("STUDENT"))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isOk());
        verify(videos).report(any(),eq(1L),any());
    }
    @Test void jspForwardKeepsOriginalVideoPolicyInsteadOfJspPath() throws Exception {
        mvc.perform(get("/api/v1/lessons/1/video/progress").with(user(actor("STUDENT")))
                        .requestAttr("jakarta.servlet.forward.request_uri","/lessons/1/video"))
                .andExpect(header().string("Content-Security-Policy",containsString("frame-src https://www.youtube.com")))
                .andExpect(header().string("Referrer-Policy","strict-origin-when-cross-origin"));
    }
}