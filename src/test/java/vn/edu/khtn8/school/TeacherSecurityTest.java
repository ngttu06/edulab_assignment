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
    @Test void deletionRequiresRoleAndCsrf() throws Exception {
        for(String kind:List.of("lessons","quizzes")) {
            mvc.perform(delete("/api/v1/teacher/"+kind+"/1").with(user(actor("TEACHER")))).andExpect(status().isForbidden());
            mvc.perform(delete("/api/v1/teacher/"+kind+"/1").with(user(actor("STUDENT"))).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(delete("/api/v1/teacher/"+kind+"/1").with(user(actor("TEACHER"))).with(csrf())).andExpect(status().isOk());
        }
        verify(authoring).deleteLesson(any(),eq(1L));verify(authoring).deleteQuiz(any(),eq(1L));
    }
    @Test void duplicationRequiresTeacherAndCsrf() throws Exception {
        for(String kind:List.of("lessons","quizzes")) {
            String path="/api/v1/teacher/"+kind+"/1/duplicate";
            mvc.perform(post(path).with(user(actor("TEACHER")))).andExpect(status().isForbidden());
            mvc.perform(post(path).with(user(actor("STUDENT"))).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(post(path).with(user(actor("TEACHER"))).with(csrf())).andExpect(status().isOk());
        }
        verify(authoring).duplicateLesson(any(),eq(1L));verify(authoring).duplicateQuiz(any(),eq(1L));
    }
    @Test void lessonPreviewSanitizesHtmlAndRequiresCsrf() throws Exception {
        String body="{\"html\":\"<h2>Hello</h2><script>alert(1)</script>\"}";
        mvc.perform(post("/api/v1/teacher/lessons/preview").with(user(actor("TEACHER"))).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/teacher/lessons/preview").with(user(actor("STUDENT"))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/teacher/lessons/preview").with(user(actor("TEACHER"))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.html").value(org.hamcrest.Matchers.containsString("<h2>Hello</h2>")))
                .andExpect(jsonPath("$.html").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>"))));
    }
    @Test void listAndEditorHaveSeparateViews() throws Exception {
        for(String kind:List.of("lessons","quizzes")) {
            mvc.perform(get("/teacher/"+kind).with(user(actor("TEACHER")))).andExpect(status().isOk()).andExpect(model().attribute("contentPage","/WEB-INF/jsp/teacher/content-list.jsp"));
            mvc.perform(get("/teacher/"+kind+"/new").with(user(actor("TEACHER")))).andExpect(status().isOk()).andExpect(model().attribute("contentPage","/WEB-INF/jsp/teacher/"+(kind.equals("lessons")?"lesson":"quiz")+"-form.jsp"));
        }
        mvc.perform(get("/teacher/quizzes/import").with(user(actor("TEACHER")))).andExpect(status().isOk()).andExpect(model().attribute("importMode",true));
    }
}