package vn.edu.khtn8.community;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.edu.khtn8.identity.security.AuthenticatedUser;

@Controller
public class ForumController {
    private final ForumService forum;
    private final vn.edu.khtn8.ai.AiService ai;
    private final vn.edu.khtn8.content.service.LessonService lessons;
    public ForumController(ForumService forum,vn.edu.khtn8.ai.AiService ai,vn.edu.khtn8.content.service.LessonService lessons) { this.forum = forum;this.ai=ai;this.lessons=lessons; }
    public record ThreadRequest(@Positive long categoryId, @NotBlank @Size(max=240) String title, @NotBlank @Size(max=10000) String body,
            @Positive Long lessonId,boolean askAi) { }
    public record PostRequest(@NotBlank @Size(max=10000) String body, @NotNull @Size(max=3) List<@NotNull @Positive Long> fileIds) { }
    public record ModerationRequest(@NotBlank String status) { }

    @GetMapping("/forum")
    public String index(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam(required=false) Long category,
                        @RequestParam(defaultValue="0") int page, Model model) {
        var categories = forum.categories(user);
        model.addAttribute("categories", categories);
        if (category == null && !categories.isEmpty()) category = ((Number)categories.getFirst().get("id")).longValue();
        model.addAttribute("categoryId", category);
        model.addAttribute("threads", category == null ? List.of() : forum.threads(user, category, page));
        model.addAttribute("page", page);
        model.addAttribute("lessons",lessons.list(user.id()));
        return view(model, "list");
    }
    @GetMapping("/forum/threads/{id}")
    public String detail(@PathVariable long id, @RequestParam(defaultValue="0") int page,
                         @AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("thread", forum.thread(user, id));
        model.addAttribute("posts", forum.posts(user, id, page));
        model.addAttribute("aiAnswers",forum.aiAnswers(user,id));
        model.addAttribute("page", page);
        return view(model, "detail");
    }
    @PostMapping("/api/v1/forum/threads") @ResponseBody
    public Map<String, Object> create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody ThreadRequest request) {
        long id=forum.create(user, request.categoryId(), request.title(), request.body());
        if(request.askAi() && request.lessonId()!=null) {
            try {
                String question=request.title()+"\n"+request.body();
                var answer=ai.ask(user,request.lessonId(),question.substring(0,Math.min(question.length(),2000)));
                forum.saveAi(user,id,answer.text(),String.join(", ",answer.sourceLabels()));
            } catch(RuntimeException ex) {
                return Map.of("id",id,"aiUnavailable",true);
            }
        }
        return Map.of("id",id,"aiUnavailable",false);
    }
    @PostMapping("/api/v1/forum/threads/{id}/posts") @ResponseBody
    public Map<String, Long> reply(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody PostRequest request) {
        return Map.of("id", forum.reply(user, id, request.body(), request.fileIds()));
    }
    @PostMapping("/api/v1/forum/threads/{id}/moderate") @ResponseBody
    public void moderate(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody ModerationRequest request) {
        forum.moderate(user, id, request.status());
    }
    private String view(Model model, String view) {
        model.addAttribute("pageTitle", "Diễn đàn · EduLab");
        model.addAttribute("contentPage", "/WEB-INF/jsp/forum/" + view + ".jsp");
        return "layout/default";
    }
}
