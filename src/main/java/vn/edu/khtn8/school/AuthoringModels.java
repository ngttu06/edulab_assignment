package vn.edu.khtn8.school;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AuthoringModels {
    private AuthoringModels() { }
    public record LessonInput(@Positive long topicId, @NotBlank @Size(max=200) String title,
            @NotNull @Size(max=500) String summary, @Min(1) @Max(600) int durationMinutes,
            @NotNull @Size(max=10000) String objectives, @NotBlank @Size(max=100000) String contentHtml,
            @NotBlank @Pattern(regexp="DRAFT|PUBLISHED|ARCHIVED") String status,
            @NotEmpty @Size(max=50) List<@NotNull @Positive Long> classIds) { }
    public record QuestionInput(@NotBlank @Size(max=10000) String content,
            @NotNull @Size(max=10000) String explanation,
            @NotNull @Size(min=2,max=4) List<@NotBlank @Size(max=1000) String> options,
            @Min(0) @Max(3) int correctIndex,
            @NotNull @DecimalMin("0.01") @DecimalMax("9999.99") @Digits(integer=4,fraction=2) BigDecimal points) { }
    public record QuizInput(@NotBlank @Size(max=200) String title, @Min(1) @Max(600) int durationMinutes,
            @Min(1) @Max(100) int maxAttempts, @NotBlank @Pattern(regexp="PRACTICE|TEST") String mode,
            Instant revealAfter, boolean published, @NotNull @Size(min=1,max=200) List<@NotNull @Valid QuestionInput> questions,
            @NotEmpty @Size(max=50) List<@NotNull @Positive Long> classIds) { }
}