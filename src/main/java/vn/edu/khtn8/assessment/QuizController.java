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
    private final vn.edu.khtn8.content.service.LearningPathService path;
    private final vn.edu.khtn8.content.service.CatalogService catalog;
    public QuizController(QuizService service,vn.edu.khtn8.content.service.LearningPathService path,vn.edu.khtn8.content.service.CatalogService catalog) { this.service = service;this.path=path;this.catalog=catalog; }

    @GetMapping("/quizzes")
    public String list(Model model,@AuthenticationPrincipal AuthenticatedUser user,@ModelAttribute("filter") vn.edu.khtn8.content.dto.CatalogFilter filter) {
        vn.edu.khtn8.content.web.CatalogController.filters(model,catalog,0);
        var ids=new java.util.HashSet<>(catalog.quizIds(filter));
        model.addAttribute("quizzes", service.list(user.id()).stream().filter(q->ids.contains(q.id())).toList());
        model.addAttribute("pageTitle", "Luyện tập · EduLab");
        model.addAttribute("contentPage", "/WEB-INF/jsp/quiz/list.jsp");
        return "layout/default";
    }
    @GetMapping("/quizzes/attempts/{id}")
    public String attempt(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user, Model model) {
        AttemptView attempt = service.get(user.id(), id);
        // A quiz in progress opens in a distraction-free workspace; a graded attempt
        // falls back to the regular shell so the answer review keeps navigation.
        boolean taking = "IN_PROGRESS".equals(attempt.status());
        model.addAttribute("attempt", attempt);
        model.addAttribute("pageTitle", taking ? "Làm quiz · EduLab" : "Xem lại bài làm · EduLab");
        model.addAttribute("contentPage", taking ? "/WEB-INF/jsp/quiz/take.jsp" : "/WEB-INF/jsp/quiz/attempt.jsp");
        return taking ? "layout/focus" : "layout/default";
    }
    @GetMapping("/api/v1/quizzes") @ResponseBody
    public List<Quiz> apiList(@AuthenticationPrincipal AuthenticatedUser user,@ModelAttribute vn.edu.khtn8.content.dto.CatalogFilter filter) {
        var ids=new java.util.HashSet<>(catalog.quizIds(filter));
        return service.list(user.id()).stream().filter(q->ids.contains(q.id())).toList();
    }
    @PostMapping("/api/v1/quizzes/{id}/attempts") @ResponseBody
    public AttemptView start(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user) {
        if(vn.edu.khtn8.school.SchoolService.hasRole(user,"STUDENT")) path.requireQuizUnlocked(user.id(),id);
        return service.start(user.id(), id);
    }
    @GetMapping("/api/v1/quiz-attempts/{id}") @ResponseBody
    public AttemptView get(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user) { return service.get(user.id(), id); }
    @PostMapping("/api/v1/quiz-attempts/{id}/submit") @ResponseBody
    public AttemptView submit(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user,
                              @Valid @RequestBody Submission submission) { return service.submit(user.id(), id, submission.answers()); }

    @PutMapping("/api/v1/quiz-attempts/{id}/answers") @ResponseBody
    public AttemptView save(@PathVariable long id,@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody Submission submission) {
        return service.save(user.id(),id,submission.answers());
    }
}
