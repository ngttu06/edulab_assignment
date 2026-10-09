package vn.edu.khtn8.ai;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.khtn8.identity.security.AuthenticatedUser;

@RestController
public class VideoAiController {
    private final VideoAiService service;
    public VideoAiController(VideoAiService service) { this.service = service; }

    public record Message(@NotNull @Pattern(regexp="user|assistant") String role, @NotBlank @Size(max=2000) String content) { }
    public record Frame(@NotBlank @Size(max=VideoFramePolicy.MAX_DATA_URL_LENGTH) String dataUrl,
                        @PositiveOrZero long timestampMs) {
        @Override public String toString() { return "VideoFrame[redacted]"; }
    }
    public record Question(@Positive long videoId, @NotBlank @Size(max=2000) String question,
                           @PositiveOrZero long positionMs, @NotNull @Size(max=8) List<@NotNull @Valid Message> history,
                           @Valid Frame frame) {
        @Override public String toString() { return "VideoQuestion[redacted]"; }
    }

    @PostMapping("/api/v1/lessons/{id}/video/chat")
    public ResponseEntity<VideoAiService.Answer> chat(@PathVariable @Positive long id,
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody Question question) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.chat(user, id, question));
    }
}