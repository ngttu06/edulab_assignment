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
    public record Deadline(@NotNull Instant dueAt) {}
    @GetMapping("/api/v1/teacher/classes/{classId}/assignments/{id}") @ResponseBody
    public java.util.Map<String,Object> detail(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long classId,@PathVariable long id) {
        return service.assignment(user,classId,id);
    }
    @PutMapping("/api/v1/teacher/classes/{classId}/assignments/{id}") @ResponseBody
    public void update(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long classId,@PathVariable long id,@Valid @RequestBody Deadline request) {
        service.updateAssignment(user,classId,id,request.dueAt());
    }
    @DeleteMapping("/api/v1/teacher/classes/{classId}/assignments/{id}") @ResponseBody
    public void cancel(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long classId,@PathVariable long id) {
        service.cancelAssignment(user,classId,id);
    }
    @GetMapping("/api/v1/teacher/students/{studentId}/feedback/{id}") @ResponseBody
    public java.util.Map<String,Object> feedbackDetail(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long studentId,@PathVariable long id) {
        return service.feedbackDetail(user,studentId,id);
    }
    @PutMapping("/api/v1/teacher/students/{studentId}/feedback/{id}") @ResponseBody
    public void updateFeedback(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long studentId,@PathVariable long id,@Valid @RequestBody Feedback request) {
        service.updateFeedback(user,studentId,id,request.body());
    }
    @DeleteMapping("/api/v1/teacher/students/{studentId}/feedback/{id}") @ResponseBody
    public void deleteFeedback(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long studentId,@PathVariable long id) {
        service.deleteFeedback(user,studentId,id);
    }
    @PostMapping("/api/v1/teacher/classes/{id}/assignments") @ResponseBody
    public void assign(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@Valid @RequestBody Assignment request) {
        service.create(user,id,request.quizId(),request.dueAt());
    }
    @PostMapping("/api/v1/teacher/students/{id}/feedback") @ResponseBody
    public void feedback(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@Valid @RequestBody Feedback request) {
        service.feedback(user,id,request.body());
    }
}