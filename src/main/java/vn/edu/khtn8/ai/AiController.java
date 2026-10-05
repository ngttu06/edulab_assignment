package vn.edu.khtn8.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.khtn8.identity.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {
    private final AiService ai;
    public AiController(AiService ai) { this.ai = ai; }
    public record Question(@Positive long lessonId, @NotBlank @Size(max=2000) String question,@Size(max=3) java.util.List<@NotNull @Positive Long> fileIds) {
        public Question(long lessonId,String question) { this(lessonId,question,java.util.List.of()); }
    }
    @PostMapping("/questions")
    public AiService.Answer ask(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody Question request) {
        return ai.ask(user, request.lessonId(), request.question(),request.fileIds()==null?java.util.List.of():request.fileIds());
    }
}
