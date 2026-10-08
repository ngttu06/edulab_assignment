package vn.edu.khtn8.school;

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
import vn.edu.khtn8.identity.security.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers={AssignmentController.class,SchoolController.class,TeacherWorkspaceController.class},properties="edulab.security.jwt.secret=dGVzdC1vbmx5LW5vdC1mb3ItZGVwbG95bWVudC1zZWNyZXQta2V5LTMyeA==")
@Import({SecurityConfig.class,AppConfig.class,JwtAuthenticationFilter.class,WebAuthenticationEntryPoint.class})
class WorkspaceSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean AssignmentService assignments;
    @MockBean SchoolService school;
    @MockBean TeacherClassService classes;
    @MockBean TeacherInsightsService insights;
    @MockBean vn.edu.khtn8.assessment.QuizService quizzes;
    @MockBean DatabaseUserDetailsService users;
    @MockBean JwtService jwt;
    AuthenticatedUser actor(String role) {return new AuthenticatedUser(1L,"test","Test","unused",true,0,List.of(new SimpleGrantedAuthority("ROLE_"+role)));}
    @Test void assignmentAndFeedbackDeletionNeedTeacherAndCsrf() throws Exception {
        for(String path:List.of("/api/v1/teacher/classes/1/assignments/2","/api/v1/teacher/students/3/feedback/4")) {
            mvc.perform(delete(path)).andExpect(status().isForbidden());
            mvc.perform(delete(path).with(user(actor("TEACHER")))).andExpect(status().isForbidden());
            mvc.perform(delete(path).with(user(actor("STUDENT"))).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(delete(path).with(user(actor("TEACHER"))).with(csrf())).andExpect(status().isOk());
        }
        verify(assignments).cancelAssignment(any(),eq(1L),eq(2L));
        verify(assignments).deleteFeedback(any(),eq(3L),eq(4L));
    }
    @Test void teacherCannotManageClassDefinitionOrTeacherAssignments() throws Exception {
        for(String path:List.of("/api/v1/admin/classes/1","/api/v1/admin/classes/1/teachers/2")) {
            mvc.perform(delete(path).with(user(actor("TEACHER"))).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(delete(path).with(user(actor("ADMIN"))).with(csrf())).andExpect(status().isOk());
        }
        verify(school).archiveClass(any(),eq(1L));verify(school).unassign(any(),eq(1L),eq(2L));
    }
    @Test void invalidUpdatesDoNotReachServices() throws Exception {
        for(String path:List.of("/api/v1/teacher/classes/1/assignments/2","/api/v1/teacher/students/3/feedback/4","/api/v1/admin/classes/1"))
            mvc.perform(put(path).with(user(actor("ADMIN"))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(assignments,school);
    }
    @Test void resultsExportRequiresAssignedTeacherAndEscapesSpreadsheetFormula() throws Exception {
        String path="/teacher/classes/1/results.csv";
        mvc.perform(get(path).with(user(actor("STUDENT")))).andExpect(status().isForbidden());
        when(insights.results(any(),eq(1L))).thenReturn(List.of(java.util.Map.of("full_name","=SUM(1,1)","username","test","active",true,
                "title","Tên \"quiz\"","attempts",0L,"graded_count",0L)));
        var response=mvc.perform(get(path).with(user(actor("TEACHER")))).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition","attachment; filename=ket-qua-lop-1.csv")).andReturn().getResponse();
        String body=new String(response.getContentAsByteArray(),java.nio.charset.StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertTrue(body.startsWith("\uFEFFHọc sinh"));
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"'=SUM(1,1)\""));
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"Tên \"\"quiz\"\"\""));
        verify(insights,never()).results(argThat(u->SchoolService.hasRole(u,"STUDENT")),anyLong());
    }
}