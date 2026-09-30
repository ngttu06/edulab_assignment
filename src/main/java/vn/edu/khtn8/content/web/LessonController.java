package vn.edu.khtn8.content.web;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.edu.khtn8.content.dto.*;
import vn.edu.khtn8.content.service.LessonService;
import vn.edu.khtn8.identity.security.AuthenticatedUser;

@Controller
public class LessonController {
    private final LessonService service;
    private final vn.edu.khtn8.content.service.LearningPathService path;
    private final vn.edu.khtn8.content.service.CatalogService catalog;
    public LessonController(LessonService service, vn.edu.khtn8.content.service.LearningPathService path,vn.edu.khtn8.content.service.CatalogService catalog) { this.service = service; this.path = path;this.catalog=catalog; }

    @GetMapping("/roadmap")
    public String roadmap(Model model,@AuthenticationPrincipal AuthenticatedUser user,@ModelAttribute("filter") CatalogFilter filter) {
        CatalogController.filters(model,catalog,user.id());
        model.addAttribute("steps",path.steps(user.id(),filter));
        model.addAttribute("pageTitle","Lộ trình học · EduLab");
        model.addAttribute("contentPage","/WEB-INF/jsp/lesson/roadmap.jsp");
        return "layout/default";
    }

    @GetMapping("/lessons")
    public String lessons(Model model, @AuthenticationPrincipal AuthenticatedUser user,@ModelAttribute("filter") CatalogFilter filter) {
        CatalogController.filters(model,catalog,user.id());
        model.addAttribute("lessons", service.list(user.id(),filter));
        model.addAttribute("pageTitle", "Thư viện bài học · EduLab");
        model.addAttribute("contentPage", "/WEB-INF/jsp/lesson/list.jsp");
        return "layout/default";
    }

    @GetMapping("/lessons/{id}")
    public String lesson(@PathVariable long id, Model model, @AuthenticationPrincipal AuthenticatedUser user) {
        if (vn.edu.khtn8.school.SchoolService.hasRole(user,"STUDENT")) path.requireUnlocked(user.id(),id);
        model.addAttribute("lesson", service.detail(user.id(), id));
        model.addAttribute("pageTitle", "Bài học · EduLab");
        model.addAttribute("contentPage", "/WEB-INF/jsp/lesson/detail.jsp");
        return "layout/default";
    }

    @GetMapping("/api/v1/lessons") @ResponseBody
    public List<LessonSummary> apiLessons(@AuthenticationPrincipal AuthenticatedUser user,@ModelAttribute CatalogFilter filter) { return service.list(user.id(),filter); }

    @GetMapping("/api/v1/lessons/{id}") @ResponseBody
    public LessonDetail apiLesson(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user) {
        if (vn.edu.khtn8.school.SchoolService.hasRole(user,"STUDENT")) path.requireUnlocked(user.id(),id);
        return service.detail(user.id(), id);
    }

    @PostMapping("/api/v1/lessons/{id}/progress") @ResponseBody
    public LessonDetail progress(@PathVariable long id, @Valid @RequestBody ProgressRequest request,
                                 @AuthenticationPrincipal AuthenticatedUser user) {
        path.requireUnlocked(user.id(),id);
        service.updateProgress(user.id(), id, request);
        return service.detail(user.id(), id);
    }
}
