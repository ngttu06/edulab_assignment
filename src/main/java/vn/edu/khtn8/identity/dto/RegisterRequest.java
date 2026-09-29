package vn.edu.khtn8.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 80) @Pattern(regexp = "[A-Za-z0-9._-]+", message = "Tên đăng nhập chỉ gồm chữ, số, dấu chấm, gạch dưới hoặc gạch ngang") String username,
        @NotBlank @Email @Size(max = 160) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 160) String fullName,
        @NotBlank @Pattern(regexp = "STUDENT|PARENT", message = "Chỉ có thể tự đăng ký tài khoản học sinh hoặc phụ huynh") String role
) { }
