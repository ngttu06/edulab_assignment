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
import vn.edu.khtn8.content.service.CatalogService;
import vn.edu.khtn8.school.SchoolService;

@Controller
public class SocialCommunityController {
    private final SocialCommunityService social;
    private final CatalogService catalog;
    public SocialCommunityController(SocialCommunityService social,CatalogService catalog) {this.social=social;this.catalog=catalog;}
    public record NewPost(@Size(max=3000) String body,@NotNull @Size(max=5) List<@NotNull @Positive Long> imageIds) { }
    public record Text(@NotBlank @Size(max=1000) String body) { }
    public record Edit(@NotNull @Size(max=3000) String body) { }
    public record Report(@NotBlank @Size(max=500) String reason) { }
    public record React(@NotNull SocialCommunityService.ReactionType reaction) { }

    @GetMapping("/community")
    public String index(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam(defaultValue="false") boolean mine,
                        @RequestParam(required=false) Long before,Model model) {
        var page=social.feed(user,mine,before,10);
        model.addAttribute("posts",page.items());model.addAttribute("nextCursor",page.nextCursor());
        model.addAttribute("hasMore",page.hasMore());model.addAttribute("mine",mine);
        model.addAttribute("subjects",catalog.subjects());model.addAttribute("grades",catalog.grades());
        model.addAttribute("pageTitle","Cộng đồng · EduLab");
        model.addAttribute("contentPage","/WEB-INF/jsp/community/list.jsp");
        return "layout/default";
    }
    @GetMapping("/community/posts/{id}")
    public String detail(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,Model model) {
        model.addAttribute("posts",List.of(social.post(user,id)));
        model.addAttribute("mine",false);model.addAttribute("detail",true);
        model.addAttribute("subjects",catalog.subjects());model.addAttribute("grades",catalog.grades());
        model.addAttribute("pageTitle","Bài viết · Cộng đồng EduLab");
        model.addAttribute("contentPage","/WEB-INF/jsp/community/list.jsp");
        return "layout/default";
    }
    @GetMapping("/api/v1/community/posts") @ResponseBody
    public SocialCommunityService.Page feed(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam(defaultValue="false") boolean mine,
            @RequestParam(required=false) Long before,@RequestParam(defaultValue="10") int size,jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control","private, no-store");return social.feed(user,mine,before,size);
    }
    @GetMapping("/api/v1/community/posts/{id}") @ResponseBody
    public Map<String,Object> post(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id) {return social.post(user,id);}
    @PostMapping("/api/v1/community/posts") @ResponseBody
    public Map<String,Long> create(@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody NewPost input) {
        return Map.of("id",social.create(user,input.body(),input.imageIds()));
    }
    @PutMapping("/api/v1/community/posts/{id}") @ResponseBody
    public void edit(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@Valid @RequestBody Edit input) {social.edit(user,id,input.body());}
    @DeleteMapping("/api/v1/community/posts/{id}") @ResponseBody
    public void remove(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id) {social.remove(user,id);}
    @PutMapping("/api/v1/community/posts/{id}/like") @ResponseBody
    public SocialCommunityService.Like like(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id) {return social.like(user,id,true);}
    @DeleteMapping("/api/v1/community/posts/{id}/like") @ResponseBody
    public SocialCommunityService.Like unlike(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id) {return social.like(user,id,false);}
    @PutMapping("/api/v1/community/posts/{id}/reaction") @ResponseBody
    public SocialCommunityService.Reaction react(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@Valid @RequestBody React input) {
        return social.react(user,id,input.reaction());
    }
    @DeleteMapping("/api/v1/community/posts/{id}/reaction") @ResponseBody
    public SocialCommunityService.Reaction unreact(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id) {return social.react(user,id,null);}
    @GetMapping("/api/v1/community/posts/{id}/comments") @ResponseBody
    public SocialCommunityService.Page comments(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,
            @RequestParam(required=false) Long after,@RequestParam(defaultValue="10") int size,jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control","private, no-store");return social.comments(user,id,after,size);
    }
    @PostMapping("/api/v1/community/posts/{id}/comments") @ResponseBody
    public Map<String,Object> comment(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@Valid @RequestBody Text input) {
        return social.commentView(user,id,social.comment(user,id,input.body()));
    }
    @PutMapping("/api/v1/community/posts/{id}/comments/{commentId}") @ResponseBody
    public void editComment(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@PathVariable long commentId,@Valid @RequestBody Text input) {
        social.editComment(user,id,commentId,input.body());
    }
    @DeleteMapping("/api/v1/community/posts/{id}/comments/{commentId}") @ResponseBody
    public void deleteComment(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@PathVariable long commentId) {social.deleteComment(user,id,commentId);}
    @PostMapping("/api/v1/community/posts/{id}/reports") @ResponseBody
    public void report(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@Valid @RequestBody Report input) {social.report(user,id,input.reason());}
    @PostMapping("/api/v1/community/posts/{id}/hide") @ResponseBody
    public void hide(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id) {social.moderate(user,id,true);}
    @GetMapping("/admin/community/reports")
    public String reports(@AuthenticationPrincipal AuthenticatedUser user,Model model) {
        SchoolService.require(user,"ADMIN");model.addAttribute("reports",social.reports(user));
        model.addAttribute("pageTitle","Báo cáo cộng đồng · EduLab");
        model.addAttribute("contentPage","/WEB-INF/jsp/community/reports.jsp");
        return "layout/default";
    }
    @GetMapping("/api/v1/admin/community/reports") @ResponseBody
    public List<Map<String,Object>> reportsApi(@AuthenticationPrincipal AuthenticatedUser user) {return social.reports(user);}
    @PostMapping("/api/v1/admin/community/posts/{id}/restore") @ResponseBody
    public void restore(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id) {social.moderate(user,id,false);}
    @DeleteMapping("/api/v1/admin/community/posts/{id}/reports") @ResponseBody
    public void resolve(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id) {social.resolveReports(user,id);}
}