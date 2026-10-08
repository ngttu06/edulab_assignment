package vn.edu.khtn8.school;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.khtn8.common.web.WebAuthenticationEntryPoint;
import vn.edu.khtn8.config.*;
import vn.edu.khtn8.identity.security.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=TeacherController.class,properties="edulab.security.jwt.secret=dGVzdC1vbmx5LW5vdC1mb3ItZGVwbG95bWVudC1zZWNyZXQta2V5LTMyeA==")
@Import({SecurityConfig.class,AppConfig.class,JwtAuthenticationFilter.class,WebAuthenticationEntryPoint.class,QuizCsv.class})
class TeacherSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean TeacherAuthoringService authoring;
    @MockBean TeacherClassService classes;
    @MockBean DatabaseUserDetailsService users;
    @MockBean JwtService jwt;
    AuthenticatedUser actor(String role) {return new AuthenticatedUser(1L,"test","Test","unused",true,0,List.of(new SimpleGrantedAuthority("ROLE_"+role)));}
    @Test void requiresTeacherAndCsrf() throws Exception {
        mvc.perform(get("/api/v1/teacher/quizzes/1")).andExpect(status().isUnauthorized());
        for(String role:List.of("STUDENT","PARENT")) {
            mvc.perform(get("/api/v1/teacher/quizzes/1").with(user(actor(role)))).andExpect(status().isForbidden());
            mvc.perform(get("/teacher/quizzes/template.csv").with(user(actor(role)))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/teacher/quizzes").with(user(actor("TEACHER"))).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());verifyNoInteractions(authoring,classes);
    }
    @Test void validatesInputBeforeCallingService() throws Exception {
        mvc.perform(post("/api/v1/teacher/quizzes").with(user(actor("TEACHER"))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());verifyNoInteractions(authoring);
    }
    @Test void templateRoundTripsThroughImportWithoutSaving() throws Exception {
        var bytes=mvc.perform(get("/teacher/quizzes/template.csv").with(user(actor("TEACHER")))).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition","attachment; filename=quiz-mau.csv")).andReturn().getResponse().getContentAsByteArray();
        var file=new MockMultipartFile("file","quiz.csv","text/csv",bytes);
        mvc.perform(multipart("/api/v1/teacher/quizzes/import").file(file).with(user(actor("TEACHER"))).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3)).andExpect(jsonPath("$[1].correctIndex").value(1));
        mvc.perform(multipart("/api/v1/teacher/quizzes/import").file(file).with(user(actor("TEACHER"))))
                .andExpect(status().isForbidden());verifyNoInteractions(authoring,classes);
    }
    @Test void rejectsWrongExtensionAndLargeImport() throws Exception {
        mvc.perform(multipart("/api/v1/teacher/quizzes/import").file(new MockMultipartFile("file","quiz.xlsx","text/plain",new byte[2])).with(user(actor("TEACHER"))).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/teacher/quizzes/import").file(new MockMultipartFile("file","quiz.csv","text/csv",new byte[1024*1024+1])).with(user(actor("TEACHER"))).with(csrf())).andExpect(status().isPayloadTooLarge());
    }
}