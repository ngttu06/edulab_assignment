package vn.edu.khtn8.content.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.edu.khtn8.content.dto.CatalogFilter;
import vn.edu.khtn8.content.service.CatalogService;
import vn.edu.khtn8.content.service.LearningPathService;
import vn.edu.khtn8.identity.security.AuthenticatedUser;

@Controller
public class CatalogController {
    private final CatalogService catalog;
    private final LearningPathService path;
    public CatalogController(CatalogService catalog,LearningPathService path) { this.catalog=catalog;this.path=path; }
    @GetMapping("/courses")
    public String list(@ModelAttribute("filter") CatalogFilter filter,Model model,@AuthenticationPrincipal AuthenticatedUser user) {
        filters(model,catalog,user==null?0:user.id());
        model.addAttribute("courses",catalog.courses(user==null?0:user.id(),filter));
        model.addAttribute("pageTitle","Khóa học đa môn · EduLab");
        model.addAttribute("contentPage","/WEB-INF/jsp/catalog/list.jsp");
        return "layout/default";
    }
    @GetMapping("/courses/{id}")
    public String detail(@PathVariable long id,Model model,@AuthenticationPrincipal AuthenticatedUser user) {
        long studentId=user==null?0:user.id();
        var course=catalog.course(studentId,id);
        model.addAttribute("course",course);
        model.addAttribute("steps",path.steps(studentId,new CatalogFilter(null,null,id,"")));
        model.addAttribute("pageTitle",course.get("title")+" · EduLab");
        model.addAttribute("contentPage","/WEB-INF/jsp/catalog/detail.jsp");
        return "layout/default";
    }
    public static void filters(Model model,CatalogService catalog,long studentId) {
        model.addAttribute("subjects",catalog.subjects());
        model.addAttribute("grades",catalog.grades());
        model.addAttribute("courseOptions",catalog.courses(studentId,CatalogFilter.all()));
    }
}