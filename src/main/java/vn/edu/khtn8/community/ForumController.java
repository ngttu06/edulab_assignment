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
            @Positive Long lessonId,boolean askAi,@Size(max=3) List<@NotNull @Positive Long> fileIds) {
        public ThreadRequest(long categoryId,String title,String body,Long lessonId,boolean askAi) { this(categoryId,title,body,lessonId,askAi,List.of()); }
    }
    public record PostRequest(@NotBlank @Size(max=10000) String body, @NotNull @Size(max=3) List<@NotNull @Positive Long> fileIds) { }
    public record ModerationRequest(@NotBlank String status) { }

    @GetMapping("/forum")
    public String index(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam(required=false) Long category,
                        @RequestParam(required=false) Long before, Model model) {
        var categories = forum.categories(user);
        model.addAttribute("categories", categories);
        model.addAttribute("categoryId", category);
        var feed = forum.feed(user, category, before, 10);
        model.addAttribute("threads", feed.items());
        model.addAttribute("nextCursor", feed.nextCursor());
        model.addAttribute("hasMore", feed.hasMore());
        model.addAttribute("lessons",lessons.list(user.id()));
        return view(model, "list");
    }
    @GetMapping("/api/v1/forum/feed") @ResponseBody
    public ForumService.FeedPage feed(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required=false) Long category, @RequestParam(required=false) Long before,
            @RequestParam(defaultValue="10") int size, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        return forum.feed(user, category, before, size);
    }
    @GetMapping("/api/v1/forum/threads/{id}/posts") @ResponseBody
    public ForumService.FeedPage comments(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
            @RequestParam(required=false) Long after, @RequestParam(defaultValue="10") int size,
            jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        return forum.comments(user, id, after, size);
    }
    @PutMapping("/api/v1/forum/threads/{id}/like") @ResponseBody
    public ForumService.LikeState like(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        return forum.like(user, id, true);
    }
    @DeleteMapping("/api/v1/forum/threads/{id}/like") @ResponseBody
    public ForumService.LikeState unlike(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        return forum.like(user, id, false);
    }
    @GetMapping("/forum/threads/{id}")
    public String detail(@PathVariable long id, @RequestParam(defaultValue="0") int page,
                         @AuthenticationPrincipal AuthenticatedUser user, Model model) {
        var thread=forum.thread(user,id);
        model.addAttribute("thread", thread);
        model.addAttribute("isThreadAuthor",((Number)thread.get("author_id")).longValue()==user.id());
        model.addAttribute("posts", forum.posts(user, id, page));
        model.addAttribute("aiAnswers",forum.aiAnswers(user,id));
        model.addAttribute("page", page);
        return view(model, "detail");
    }
    @PostMapping("/api/v1/forum/threads") @ResponseBody
    public Map<String, Object> create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody ThreadRequest request) {
        List<Long> files=request.fileIds()==null?List.of():request.fileIds();
        if(request.askAi() && request.lessonId()==null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Chọn bài học tham khảo trước khi nhờ AI.");
        long id=files.isEmpty() && request.lessonId()==null?forum.create(user, request.categoryId(), request.title(), request.body()):forum.create(user,request.categoryId(),request.title(),request.body(),request.lessonId(),files);
        if(request.askAi() && request.lessonId()!=null) {
            try {
                String question=request.title()+"\n"+request.body();
                var answer=files.isEmpty()?ai.ask(user,request.lessonId(),question.substring(0,Math.min(question.length(),2000))):ai.ask(user,request.lessonId(),question.substring(0,Math.min(question.length(),2000)),files);
                forum.saveAi(user,id,answer.text(),String.join(", ",answer.sourceLabels()));
            } catch(RuntimeException ex) {
                return Map.of("id",id,"aiUnavailable",true);
            }
        }
        return Map.of("id",id,"aiUnavailable",false);
    }
    public record AiRetry(@Positive long lessonId) {}
    @PostMapping("/api/v1/forum/threads/{id}/ai") @ResponseBody
    public vn.edu.khtn8.ai.AiService.Answer retry(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@Valid @RequestBody AiRetry request) {
        var thread=forum.thread(user,id);
        if(((Number)thread.get("author_id")).longValue()!=user.id()) throw new org.springframework.security.access.AccessDeniedException("Only author can request AI");
        @SuppressWarnings("unchecked") var files=(List<Map<String,Object>>)thread.get("files");
        String question=thread.get("title")+"\n"+thread.get("body");
        var answer=ai.ask(user,request.lessonId(),question.substring(0,Math.min(2000,question.length())),files.stream().map(f->((Number)f.get("id")).longValue()).toList());
        forum.saveAi(user,id,answer.text(),String.join(", ",answer.sourceLabels()));return answer;
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
        model.addAttribute("pageTitle", "Hỏi đáp học tập · EduLab");
        model.addAttribute("contentPage", "/WEB-INF/jsp/forum/" + view + ".jsp");
        return "layout/default";
    }
}
