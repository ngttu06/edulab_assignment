package vn.edu.khtn8.school;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.edu.khtn8.identity.security.AuthenticatedUser;

@Controller
public class SchoolController {
    private final SchoolService school;
    private final AssignmentService assignments;
    private final TeacherInsightsService insights;
    public SchoolController(SchoolService school,AssignmentService assignments,TeacherInsightsService insights) {this.school=school;this.assignments=assignments;this.insights=insights;}
    public record CodeRequest(@NotBlank @Pattern(regexp = "[a-f0-9]{32}") String code) { }
    public record MemberRequest(@Positive long userId) { }
    public record ClassRequest(@Positive long yearId, @NotBlank @Size(max = 80) String name) { }

    @GetMapping("/teacher/classes")
    public String classes(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("classes", school.classrooms(user));
        model.addAttribute("archivedClasses",school.archivedClasses(user));
        return page(model, "school/classes", "Lớp học");
    }
    @GetMapping("/teacher/classes/{id}")
    public String students(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("classroom",school.classroom(user,id));
        model.addAttribute("summary",insights.classroom(user,id));
        model.addAttribute("classId",id);
        return page(model, "teacher/class-detail", "Chi tiết lớp học");
    }
    @GetMapping("/school/students/{id}")
    public String report(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("report", school.report(user, id));
        model.addAttribute("assignments",assignments.forStudent(user,id));
        model.addAttribute("feedback",assignments.feedback(user,id));
        model.addAttribute("studentId",id);
        return page(model, "school/report", "Kết quả học tập");
    }
    @GetMapping("/family")
    public String family(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("links", school.links(user));
        return page(model, "school/family", "Liên kết gia đình");
    }
    @GetMapping("/api/v1/school/students/{id}") @ResponseBody
    public Map<String, Object> apiReport(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user) { return school.report(user, id); }
    @PostMapping("/api/v1/family/codes") @ResponseBody
    public Map<String, Object> generate(@AuthenticationPrincipal AuthenticatedUser user) { return Map.of("code", school.generateCode(user), "expiresInMinutes", 15); }
    @DeleteMapping("/api/v1/family/codes") @ResponseBody
    public void revokeCodes(@AuthenticationPrincipal AuthenticatedUser user) { school.revokeCodes(user); }
    @PostMapping("/api/v1/family/links") @ResponseBody
    public void redeem(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody CodeRequest request) { school.redeem(user, request.code()); }
    @DeleteMapping("/api/v1/family/links/{id}") @ResponseBody
    public void revoke(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) { school.revoke(user, id); }
    @PostMapping("/api/v1/admin/classes") @ResponseBody
    public void create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody ClassRequest request) { school.createClass(user, request.yearId(), request.name()); }
    @PutMapping("/api/v1/admin/classes/{id}") @ResponseBody
    public void update(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@Valid @RequestBody ClassRequest request) {school.updateClass(user,id,request.yearId(),request.name());}
    @DeleteMapping("/api/v1/admin/classes/{id}") @ResponseBody
    public void delete(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id) {school.archiveClass(user,id);}
    @PostMapping("/api/v1/admin/classes/{id}/students") @ResponseBody
    public void enroll(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody MemberRequest request) { school.enroll(user, id, request.userId()); }
    @PostMapping("/api/v1/admin/classes/{id}/teachers") @ResponseBody
    public void assign(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody MemberRequest request) { school.assign(user, id, request.userId()); }
    @DeleteMapping("/api/v1/admin/classes/{id}/teachers/{teacherId}") @ResponseBody
    public void unassign(@PathVariable long id,@PathVariable long teacherId,@AuthenticationPrincipal AuthenticatedUser user) {school.unassign(user,id,teacherId);}

    private String page(Model model, String view, String title) {
        model.addAttribute("contentPage", "/WEB-INF/jsp/" + view + ".jsp");
        model.addAttribute("pageTitle", title + " · EduLab");
        return "layout/default";
    }
}
