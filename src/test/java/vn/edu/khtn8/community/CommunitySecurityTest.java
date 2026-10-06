package vn.edu.khtn8.community;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.khtn8.ai.AiService;
import vn.edu.khtn8.content.service.LessonService;
import vn.edu.khtn8.common.web.WebAuthenticationEntryPoint;
import vn.edu.khtn8.config.AppConfig;
import vn.edu.khtn8.config.SecurityConfig;
import vn.edu.khtn8.identity.security.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=ForumController.class,properties="edulab.security.jwt.secret=dGVzdC1vbmx5LW5vdC1mb3ItZGVwbG95bWVudC1zZWNyZXQta2V5LTMyeA==")
@Import({SecurityConfig.class,AppConfig.class,JwtAuthenticationFilter.class,WebAuthenticationEntryPoint.class})
class CommunitySecurityTest {
    @Autowired MockMvc mvc;
    @MockBean ForumService forum;
    @MockBean AiService ai;
    @MockBean LessonService lessons;
    @MockBean DatabaseUserDetailsService users;
    @MockBean JwtService jwt;
    final AuthenticatedUser student=new AuthenticatedUser(1L,"student","Student","unused",true,0,List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    @Test void communityAndAllFeedApisRequireAuthentication() throws Exception {
        mvc.perform(get("/forum")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/api/v1/forum/feed")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/forum/threads/1/posts")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/forum/threads/1/like").with(csrf())).andExpect(status().isUnauthorized());
        verifyNoInteractions(forum,ai,lessons);
    }
    @Test void likeUnlikeAndCommentsKeepCsrfProtection() throws Exception {
        mvc.perform(put("/api/v1/forum/threads/1/like").with(user(student))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/forum/threads/1/like").with(user(student))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/forum/threads/1/posts").with(user(student)).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Hello\",\"fileIds\":[]}")).andExpect(status().isForbidden());
        verifyNoInteractions(forum);
    }
    @Test void authenticatedCursorReadsAndIdempotentLikesReachService() throws Exception {
        when(forum.feed(student,2L,30L,10)).thenReturn(new ForumService.FeedPage(List.of(),null,false));
        mvc.perform(get("/api/v1/forum/feed?category=2&before=30").with(user(student)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"))
                .andExpect(jsonPath("$.hasMore").value(false));
        when(forum.like(student,1,true)).thenReturn(new ForumService.LikeState(true,1));
        mvc.perform(put("/api/v1/forum/threads/1/like").with(user(student)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.liked").value(true));
        when(forum.like(student,1,false)).thenReturn(new ForumService.LikeState(false,0));
        mvc.perform(delete("/api/v1/forum/threads/1/like").with(user(student)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(0));
        mvc.perform(get("/api/v1/forum/feed?before=not-a-number").with(user(student))).andExpect(status().isBadRequest());
        verifyNoInteractions(ai);
    }
}