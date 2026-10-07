package vn.edu.khtn8.content.video;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class VideoModels {
    private VideoModels() { }
    public record Range(@Min(0) long startMs, @Positive long endMs) { }
    public record Input(@NotNull @Pattern(regexp="YOUTUBE|PLAYERJS") String provider,
                        @NotBlank @Size(max=2048) String sourceUrl,
                        @Min(1) @Max(21600) int durationSeconds,
                        @Min(80) @Max(100) int completionThreshold) { }
    public record Video(long id, long lessonId, int revision, String provider, String sourceUrl,
                        String youtubeId, int durationSeconds, int completionThreshold) { }
    public record Progress(long videoId, long positionMs, List<Range> ranges, long watchedMs,
                           int completionPercent, String status) { }
    public record Session(String sessionId, Progress progress) { }
    public record Report(@NotBlank @Pattern(regexp="[a-f0-9-]{36}") String sessionId,
                         @Min(1) int sequence, @Min(0) long positionMs,
                         @Positive long durationMs,
                         @NotNull @Size(max=64) List<@NotNull @Valid Range> ranges) { }
    public record Editor(@Positive Long teacherId) { }
}