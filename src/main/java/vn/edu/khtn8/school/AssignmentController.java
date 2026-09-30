package vn.edu.khtn8.school;

import java.time.Instant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import vn.edu.khtn8.identity.security.AuthenticatedUser;

@Controller
public class AssignmentController {
    private final AssignmentService service;
    public AssignmentController(AssignmentService service){this.service=service;}
    public record Assignment(@Positive long quizId,@NotNull Instant dueAt) {}
    public record Feedback(@NotBlank @Size(max=2000) String body) {}
    @PostMapping("/api/v1/teacher/classes/{id}/assignments") @ResponseBody
    public void assign(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@Valid @RequestBody Assignment request) {
        service.create(user,id,request.quizId(),request.dueAt());
    }
    @PostMapping("/api/v1/teacher/students/{id}/feedback") @ResponseBody
    public void feedback(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@Valid @RequestBody Feedback request) {
        service.feedback(user,id,request.body());
    }
}