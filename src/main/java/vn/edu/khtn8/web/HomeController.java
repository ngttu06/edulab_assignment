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
    public HomeController(SchoolService school) { this.school = school; }
    @GetMapping("/")
    public String home() { return "redirect:/dashboard"; }

    @GetMapping("/dashboard")
    public String dashboard(Model model, @AuthenticationPrincipal AuthenticatedUser user) {
        if (SchoolService.hasRole(user, "TEACHER") || SchoolService.hasRole(user, "ADMIN")) return "redirect:/teacher/classes";
        if (SchoolService.hasRole(user, "PARENT")) return "redirect:/family";
        model.addAttribute("report", school.report(user, user.id()));
        model.addAttribute("pageTitle", "Tổng quan · EduLab KHTN 8");
        model.addAttribute("contentPage", "/WEB-INF/jsp/dashboard.jsp");
        return "layout/default";
    }
}
