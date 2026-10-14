package vn.edu.khtn8.assessment;

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

@WebMvcTest(controllers={AssessmentReviewController.class,QuizImportController.class},properties="edulab.security.jwt.secret=dGVzdC1vbmx5LW5vdC1mb3ItZGVwbG95bWVudC1zZWNyZXQta2V5LTMyeA==")
@Import({SecurityConfig.class,AppConfig.class,JwtAuthenticationFilter.class,WebAuthenticationEntryPoint.class})
class AssessmentSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean AssessmentReviewService reviews;
    @MockBean QuizAiReviewService ai;
    @MockBean QuizImportService imports;
    @MockBean DatabaseUserDetailsService users;
    @MockBean JwtService jwt;
    AuthenticatedUser actor(String role) {return new AuthenticatedUser(1L,"actor","Actor","unused",true,0,List.of(new SimpleGrantedAuthority("ROLE_"+role)));}
    @Test void teacherReportsAndAttemptsRequireTeacher() throws Exception {
        for(String path:List.of("/api/v1/teacher/quiz-attempts/9","/api/v1/teacher/quizzes/1/report","/teacher/quizzes/1/report.csv")) {
            mvc.perform(get(path)).andExpect(path.startsWith("/api/")?status().isUnauthorized():status().is3xxRedirection());
            mvc.perform(get(path).with(user(actor("STUDENT")))).andExpect(status().isForbidden());
        }verifyNoInteractions(reviews);
    }
    @Test void aiAndTabEventsRequireCsrfAndValidUuid() throws Exception {
        for(String path:List.of("/api/v1/quiz-attempts/9/ai-review","/api/v1/quiz-attempts/9/questions/1/ai-explanation","/api/v1/quiz-attempts/9/tab-events"))
            mvc.perform(post(path).with(user(actor("STUDENT"))).contentType(MediaType.APPLICATION_JSON).content("{\"eventId\":\"bad\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/quiz-attempts/9/tab-events").with(user(actor("STUDENT"))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"eventId\":\"bad\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(reviews,ai);
    }
    @Test void documentImportAndAnswerGenerationRequireRoleAndCsrf() throws Exception {
        var file=new MockMultipartFile("file","quiz.pdf","application/pdf","%PDF-1.4".getBytes());
        mvc.perform(multipart("/api/v1/teacher/quizzes/document-import").file(file).with(user(actor("TEACHER")))).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/teacher/quizzes/document-import").file(file).with(user(actor("STUDENT"))).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/teacher/quizzes/document-import/abc/generate-answers").with(user(actor("TEACHER")))).andExpect(status().isForbidden());
        verifyNoInteractions(imports);
    }
}