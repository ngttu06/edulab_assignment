package vn.edu.khtn8.assessment;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import vn.edu.khtn8.assessment.QuizModels.*;

@Controller
public class QuizController {
    private final QuizService service;
    public QuizController(QuizService service) { this.service = service; }

    @GetMapping("/quizzes")
    public String list(Model model) {
        model.addAttribute("quizzes", service.list());
        model.addAttribute("pageTitle", "Luyện tập · EduLab");
        model.addAttribute("contentPage", "/WEB-INF/jsp/quiz/list.jsp");
        return "layout/default";
    }
    @GetMapping("/quizzes/attempts/{id}")
    public String attempt(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("attempt", service.get(user.id(), id));
        model.addAttribute("pageTitle", "Làm quiz · EduLab");
        model.addAttribute("contentPage", "/WEB-INF/jsp/quiz/attempt.jsp");
        return "layout/default";
    }
    @GetMapping("/api/v1/quizzes") @ResponseBody
    public List<Quiz> apiList() { return service.list(); }
    @PostMapping("/api/v1/quizzes/{id}/attempts") @ResponseBody
    public AttemptView start(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user) { return service.start(user.id(), id); }
    @GetMapping("/api/v1/quiz-attempts/{id}") @ResponseBody
    public AttemptView get(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user) { return service.get(user.id(), id); }
    @PostMapping("/api/v1/quiz-attempts/{id}/submit") @ResponseBody
    public AttemptView submit(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user,
                              @Valid @RequestBody Submission submission) { return service.submit(user.id(), id, submission.answers()); }
}
