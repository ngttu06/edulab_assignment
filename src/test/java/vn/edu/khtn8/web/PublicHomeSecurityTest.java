package vn.edu.khtn8.web;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.khtn8.common.web.WebAuthenticationEntryPoint;
import vn.edu.khtn8.config.AppConfig;
import vn.edu.khtn8.config.SecurityConfig;
import vn.edu.khtn8.content.dto.CatalogFilter;
import vn.edu.khtn8.content.service.CatalogService;
import vn.edu.khtn8.identity.security.DatabaseUserDetailsService;
import vn.edu.khtn8.identity.security.JwtAuthenticationFilter;
import vn.edu.khtn8.identity.security.JwtService;
import vn.edu.khtn8.school.AssignmentService;
import vn.edu.khtn8.school.SchoolService;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = HomeController.class, properties = "edulab.security.jwt.secret=dGVzdC1vbmx5LW5vdC1mb3ItZGVwbG95bWVudC1zZWNyZXQta2V5LTMyeA==")
@Import({SecurityConfig.class, AppConfig.class, JwtAuthenticationFilter.class, WebAuthenticationEntryPoint.class})
class PublicHomeSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean SchoolService school;
    @MockBean AssignmentService assignments;
    @MockBean CatalogService catalog;
    @MockBean DatabaseUserDetailsService users;
    @MockBean JwtService jwt;

    @Test void anonymousHomeKeepsDefaultLayoutAndPublicCatalog() throws Exception {
        var subjects = List.<Map<String,Object>>of(Map.of("id", 1L, "name", "Toán", "grade_level", 8));
        var courses = List.<Map<String,Object>>of(Map.of("id", 3L, "title", "Toán 8"));
        when(catalog.subjects()).thenReturn(subjects);
        when(catalog.grades()).thenReturn(List.of(8));
        when(catalog.courses(0, CatalogFilter.all())).thenReturn(courses);
        var statistics = Map.<String,Object>of("courses", 6L, "lessons", 8L, "subjects", 6L, "teachers", 1L);
        var teachers = List.<Map<String,Object>>of(Map.of("name", "Giáo viên", "course_id", 3L));
        when(catalog.publicStatistics()).thenReturn(statistics);
        when(catalog.publicTeachers()).thenReturn(teachers);
        mvc.perform(get("/"))
                .andExpect(status().isOk()).andExpect(view().name("layout/default"))
                .andExpect(model().attribute("pageTitle", "EduLab · Học chủ động, vững tương lai"))
                .andExpect(model().attribute("contentPage", "/WEB-INF/jsp/home.jsp"))
                .andExpect(model().attribute("subjects", subjects))
                .andExpect(model().attribute("grades", List.of(8)))
                .andExpect(model().attribute("courses", courses))
                .andExpect(model().attribute("statistics", statistics))
                .andExpect(model().attribute("teachers", teachers));
        verify(catalog).courses(0, CatalogFilter.all());
        verifyNoInteractions(school, assignments);
    }

    @Test void anonymousDashboardStillRequiresLogin() throws Exception {
        mvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        verifyNoInteractions(school, assignments, catalog);
    }
}