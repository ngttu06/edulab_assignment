package vn.edu.khtn8.content.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ProgressRequest(@NotBlank String status, @Min(0) @Max(100) int completionPercent) { }
