package vn.edu.khtn8.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import vn.edu.khtn8.school.SchoolService;

@Controller
public class HomeController {
    private final SchoolService school;
    private final vn.edu.khtn8.school.AssignmentService assignments;
    private final vn.edu.khtn8.content.service.CatalogService catalog;
    public HomeController(SchoolService school,vn.edu.khtn8.school.AssignmentService assignments,vn.edu.khtn8.content.service.CatalogService catalog) { this.school = school;this.assignments=assignments;this.catalog=catalog; }
    @GetMapping("/")
    public String home(Model model,@AuthenticationPrincipal AuthenticatedUser user) {
        model.addAttribute("subjects",catalog.subjects());
        model.addAttribute("grades",catalog.grades());
        model.addAttribute("statistics",catalog.publicStatistics());
        model.addAttribute("teachers",catalog.publicTeachers());
        model.addAttribute("courses",catalog.courses(user==null?0:user.id(),vn.edu.khtn8.content.dto.CatalogFilter.all()));
        model.addAttribute("pageTitle", "EduLab · Học chủ động, vững tương lai");
        model.addAttribute("contentPage", "/WEB-INF/jsp/home.jsp");
        return "layout/default";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, @AuthenticationPrincipal AuthenticatedUser user) {
        if (SchoolService.hasRole(user,"ADMIN")) return "redirect:/admin";
        if (SchoolService.hasRole(user, "TEACHER")) return "redirect:/teacher";
        if (SchoolService.hasRole(user, "PARENT")) return "redirect:/family";
        model.addAttribute("report", school.report(user, user.id()));
        model.addAttribute("assignments",assignments.forStudent(user,user.id()));
        model.addAttribute("feedback",assignments.feedback(user,user.id()));
        model.addAttribute("courses",catalog.courses(user.id(),vn.edu.khtn8.content.dto.CatalogFilter.all()));
        model.addAttribute("pageTitle", "Góc học tập · EduLab");
        model.addAttribute("contentPage", "/WEB-INF/jsp/dashboard.jsp");
        return "layout/default";
    }
}
