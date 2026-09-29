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
    public LessonController(LessonService service) { this.service = service; }

    @GetMapping("/lessons")
    public String lessons(Model model, @AuthenticationPrincipal AuthenticatedUser user) {
        model.addAttribute("lessons", service.list(user.id()));
        model.addAttribute("pageTitle", "Bài học · EduLab KHTN 8");
        model.addAttribute("contentPage", "/WEB-INF/jsp/lesson/list.jsp");
        return "layout/default";
    }

    @GetMapping("/lessons/{id}")
    public String lesson(@PathVariable long id, Model model, @AuthenticationPrincipal AuthenticatedUser user) {
        model.addAttribute("lesson", service.detail(user.id(), id));
        model.addAttribute("pageTitle", "Bài học · EduLab KHTN 8");
        model.addAttribute("contentPage", "/WEB-INF/jsp/lesson/detail.jsp");
        return "layout/default";
    }

    @GetMapping("/api/v1/lessons") @ResponseBody
    public List<LessonSummary> apiLessons(@AuthenticationPrincipal AuthenticatedUser user) { return service.list(user.id()); }

    @GetMapping("/api/v1/lessons/{id}") @ResponseBody
    public LessonDetail apiLesson(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user) { return service.detail(user.id(), id); }

    @PostMapping("/api/v1/lessons/{id}/progress") @ResponseBody
    public LessonDetail progress(@PathVariable long id, @Valid @RequestBody ProgressRequest request,
                                 @AuthenticationPrincipal AuthenticatedUser user) {
        service.updateProgress(user.id(), id, request);
        return service.detail(user.id(), id);
    }
}
