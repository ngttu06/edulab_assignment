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
import vn.edu.khtn8.common.web.WebAuthenticationEntryPoint;
import vn.edu.khtn8.config.AppConfig;
import vn.edu.khtn8.config.SecurityConfig;
import vn.edu.khtn8.content.service.CatalogService;
import vn.edu.khtn8.identity.security.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=SocialCommunityController.class,properties="edulab.security.jwt.secret=dGVzdC1vbmx5LW5vdC1kZXBsb3ltZW50LXNlY3JldC1rZXktMzJ4")
@Import({SecurityConfig.class,AppConfig.class,JwtAuthenticationFilter.class,WebAuthenticationEntryPoint.class})
class SocialCommunitySecurityTest {
    @Autowired MockMvc mvc;
    @MockBean SocialCommunityService social;
    @MockBean CatalogService catalog;
    @MockBean DatabaseUserDetailsService users;
    @MockBean JwtService jwt;
    final AuthenticatedUser student=new AuthenticatedUser(1L,"student","Student","unused",true,0,List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    @Test void feedRequiresAuthenticationAndMutationsRequireCsrf() throws Exception {
        mvc.perform(get("/community")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/api/v1/community/posts")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/community/posts").with(user(student)).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"hi\",\"imageIds\":[]}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/community/posts/1/like").with(user(student))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/community/posts/1").with(user(student))).andExpect(status().isForbidden());
        verifyNoInteractions(social);
    }
    @Test void validationAndIndependentRoutes() throws Exception {
        when(social.feed(student,false,null,10)).thenReturn(new SocialCommunityService.Page(List.of(),null,false));
        mvc.perform(get("/api/v1/community/posts").with(user(student))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","private, no-store"));
        mvc.perform(post("/api/v1/community/posts").with(user(student)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"hi\",\"imageIds\":[1,2,3,4,5,6]}"))
                .andExpect(status().isBadRequest());
        when(social.create(student,"Chào mọi người",List.of())).thenReturn(42L);
        mvc.perform(post("/api/v1/community/posts").with(user(student)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Chào mọi người\",\"imageIds\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(42));
        verify(social).create(student,"Chào mọi người",List.of());
    }
    @Test void communityPagesSupplyStudyFiltersAndUseDiscoveryLayout() throws Exception {
        when(social.feed(student,false,null,10)).thenReturn(new SocialCommunityService.Page(List.of(),null,false));
        when(social.post(student,42L)).thenReturn(java.util.Map.of("id",42L,"body","Một ngày vui"));
        when(catalog.grades()).thenReturn(List.of(8,10));
        when(catalog.subjects()).thenReturn(List.of(java.util.Map.of("id",1,"name","Khoa học tự nhiên","grade_level",8)));
        mvc.perform(get("/community").with(user(student))).andExpect(status().isOk())
                .andExpect(view().name("layout/default"))
                .andExpect(model().attribute("contentPage","/WEB-INF/jsp/community/list.jsp"))
                .andExpect(model().attributeExists("grades","subjects"));
        mvc.perform(get("/community/posts/42").with(user(student))).andExpect(status().isOk())
                .andExpect(view().name("layout/default"))
                .andExpect(model().attributeExists("grades","subjects"));
        verify(catalog,times(2)).grades();
        verify(catalog,times(2)).subjects();
    }
    @Test void reactionsValidateTypesAndRequireAuthenticationAndCsrf() throws Exception {
        String url="/api/v1/community/posts/42/reaction";
        mvc.perform(put(url).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"reaction\":\"LOVE\"}")).andExpect(status().isUnauthorized());
        mvc.perform(put(url).with(user(student)).contentType(MediaType.APPLICATION_JSON).content("{\"reaction\":\"LOVE\"}")).andExpect(status().isForbidden());
        mvc.perform(delete(url).with(user(student))).andExpect(status().isForbidden());
        for(String json:List.of("{}","{\"reaction\":null}","{\"reaction\":\"INVALID\"}"))
            mvc.perform(put(url).with(user(student)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isBadRequest());
        verifyNoInteractions(social);
        for(var type:SocialCommunityService.ReactionType.values()) {
            when(social.react(student,42,type)).thenReturn(new SocialCommunityService.Reaction(type,1,java.util.Map.of(type.name(),1L)));
            mvc.perform(put(url).with(user(student)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"reaction\":\""+type.name()+"\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.reaction").value(type.name())).andExpect(jsonPath("$.count").value(1));
        }
        when(social.react(student,42,null)).thenReturn(new SocialCommunityService.Reaction(null,0,java.util.Map.of()));
        mvc.perform(delete(url).with(user(student)).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.count").value(0));
    }
    @Test void commentsReturnRenderableDataAndValidateEdits() throws Exception {
        String url="/api/v1/community/posts/42/comments";
        mvc.perform(put(url+"/9").with(user(student)).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"hello\"}")).andExpect(status().isForbidden());
        for(String body:List.of(" ","x".repeat(1001)))
            mvc.perform(put(url+"/9").with(user(student)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\""+body+"\"}")).andExpect(status().isBadRequest());
        when(social.comment(student,42,"Chào bạn")).thenReturn(9L);
        when(social.commentView(student,42,9)).thenReturn(java.util.Map.of("id",9,"body","Chào bạn","author","Student","mine",true));
        mvc.perform(post(url).with(user(student)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Chào bạn\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.body").value("Chào bạn")).andExpect(jsonPath("$.mine").value(true));
        mvc.perform(put(url+"/9").with(user(student)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Đã sửa\"}"))
                .andExpect(status().isOk());
        verify(social).editComment(student,42,9,"Đã sửa");
    }
}