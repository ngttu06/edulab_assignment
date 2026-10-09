package vn.edu.khtn8.content.video;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.edu.khtn8.content.service.LessonService;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import vn.edu.khtn8.school.SchoolService;
import static vn.edu.khtn8.content.video.VideoModels.*;

@Controller
public class LessonVideoController {
    private final LessonVideoService videos;
    private final LessonService lessons;
    private final VideoPolicy policy;
    public LessonVideoController(LessonVideoService videos, LessonService lessons, VideoPolicy policy) {
        this.videos=videos; this.lessons=lessons; this.policy=policy;
    }
    @GetMapping("/lessons/{id}/video")
    public String watch(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("video", videos.video(user,id));
        model.addAttribute("lesson", lessons.detail(user.id(),id));
        model.addAttribute("playerAvailable", policy.playerAvailable());
        model.addAttribute("playerPath", policy.playerPath());
        return page(model,"lesson/video","Học qua video");
    }
    @GetMapping("/teacher/videos")
    public String manage(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("lessons",videos.manageable(user));
        if (SchoolService.hasRole(user,"ADMIN")) model.addAttribute("teachers",videos.teachers(user));
        model.addAttribute("videoHosts",policy.hosts());
        model.addAttribute("videoAllHosts",policy.allowsAllHosts());
        model.addAttribute("playerAvailable",policy.playerAvailable());
        model.addAttribute("playerPath",policy.playerPath());
        return page(model,"lesson/video-manage","Quản lý video bài học");
    }
    @GetMapping("/admin/videos")
    public String adminManage(@AuthenticationPrincipal AuthenticatedUser user,Model model) {
        SchoolService.require(user,"ADMIN");manage(user,model);
        model.addAttribute("adminTitle","Video bài học");model.addAttribute("adminActive","videos");
        model.addAttribute("adminNavigation",vn.edu.khtn8.administration.AdminResources.all());
        model.addAttribute("adminVideo",true);return "layout/admin";
    }
    @GetMapping("/api/v1/lessons/{id}/video/progress") @ResponseBody
    public Progress progress(@PathVariable long id,@AuthenticationPrincipal AuthenticatedUser user) { return videos.progress(user,id); }
    @PostMapping("/api/v1/lessons/{id}/video/sessions") @ResponseBody
    public Session session(@PathVariable long id,@AuthenticationPrincipal AuthenticatedUser user) { return videos.begin(user,id); }
    @PostMapping("/api/v1/lessons/{id}/video/progress") @ResponseBody
    public Progress report(@PathVariable long id,@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody Report input) { return videos.report(user,id,input); }
    @GetMapping({"/api/v1/teacher/lessons/{id}/video","/api/v1/admin/lessons/{id}/video"}) @ResponseBody
    public java.util.Map<String,Object> configuration(@PathVariable long id,@AuthenticationPrincipal AuthenticatedUser user) { return videos.configuration(user,id); }
    @PostMapping({"/api/v1/teacher/videos/validate","/api/v1/admin/videos/validate"}) @ResponseBody
    public VideoPolicy.Source validate(@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody Input input) {
        SchoolService.require(user,"TEACHER","ADMIN"); return policy.validate(input.provider(),input.sourceUrl());
    }
    @PutMapping({"/api/v1/teacher/lessons/{id}/video","/api/v1/admin/lessons/{id}/video"}) @ResponseBody
    public Video save(@PathVariable long id,@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody Input input) { return videos.save(user,id,input); }
    @DeleteMapping({"/api/v1/teacher/lessons/{id}/video","/api/v1/admin/lessons/{id}/video"}) @ResponseBody
    public void remove(@PathVariable long id,@AuthenticationPrincipal AuthenticatedUser user) { videos.remove(user,id); }
    @PutMapping("/api/v1/admin/lessons/{id}/video-teacher") @ResponseBody
    public void assign(@PathVariable long id,@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody Editor input) { videos.assign(user,id,input.teacherId()); }
    private String page(Model model,String view,String title) {
        model.addAttribute("contentPage","/WEB-INF/jsp/"+view+".jsp");
        model.addAttribute("pageTitle",title+" · EduLab"); return "layout/default";
    }
}