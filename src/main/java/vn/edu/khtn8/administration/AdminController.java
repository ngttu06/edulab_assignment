package vn.edu.khtn8.administration;

import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.edu.khtn8.identity.security.AuthenticatedUser;

@Controller
public class AdminController {
    private final AdminService service;
    public AdminController(AdminService service) { this.service=service; }
    public record UserInput(@NotBlank @Pattern(regexp="[A-Za-z0-9._-]{3,80}") String username,@NotBlank @Email @Size(max=160) String email,
            @NotBlank @Size(min=8,max=72) String password,@NotBlank @Size(max=160) String fullName,@NotBlank String role) {}
    public record LessonInput(@Positive long topicId,@NotBlank @Size(max=200) String title,@NotBlank @Size(max=500) String summary,
            @Min(1) @Max(180) int durationMinutes,@NotBlank @Size(max=2000) String objectives,@NotBlank @Size(max=100000) String contentHtml,
            @Positive Long prerequisiteId,@DecimalMin("0") @DecimalMax("10") double requiredScore) {}
    public record QuestionInput(@NotBlank @Size(max=5000) String content,@NotBlank @Size(max=5000) String explanation,
            @Pattern(regexp="EASY|MEDIUM|HARD") @NotNull String difficulty,@NotNull @Size(min=2,max=6) List<@NotBlank @Size(max=1000) String> options,@Min(0) int correctIndex) {}
    public record QuizInput(@NotBlank @Size(max=200) String title,@Positive Long lessonId,@Min(1) @Max(180) int durationMinutes,
            @Min(1) @Max(100) int maxAttempts,@NotNull @Size(min=1,max=200) List<@NotNull @Positive Long> questionIds,
            @NotNull @Pattern(regexp="PRACTICE|TEST") String mode,java.time.Instant revealAfter) {
        public QuizInput(String title,Long lessonId,int durationMinutes,int maxAttempts,List<Long> questionIds){this(title,lessonId,durationMinutes,maxAttempts,questionIds,"PRACTICE",null);}
    }
    public record State(boolean enabled) {}
    @PostMapping("/api/v1/admin/users") @ResponseBody public Map<String,Long> user(@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody UserInput input) { return Map.of("id",service.createUser(user,input)); }
    @PostMapping("/api/v1/admin/users/{id}/state") @ResponseBody public void state(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@RequestBody State input) {service.enableUser(user,id,input.enabled());}
    @PostMapping("/api/v1/admin/lessons") @ResponseBody public Map<String,Long> lesson(@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody LessonInput input) {return Map.of("id",service.createLesson(user,input));}
    @PostMapping("/api/v1/admin/lessons/{id}/publish") @ResponseBody public void publishLesson(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@RequestBody State input) {service.publishLesson(user,id,input.enabled());}
    @PostMapping("/api/v1/admin/questions") @ResponseBody public Map<String,Long> question(@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody QuestionInput input) {return Map.of("id",service.createQuestion(user,input));}
    @PostMapping("/api/v1/admin/quizzes") @ResponseBody public Map<String,Long> quiz(@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody QuizInput input) {return Map.of("id",service.createQuiz(user,input));}
    @PostMapping("/api/v1/admin/quizzes/{id}/publish") @ResponseBody public void publishQuiz(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@RequestBody State input) {service.publishQuiz(user,id,input.enabled());}
}