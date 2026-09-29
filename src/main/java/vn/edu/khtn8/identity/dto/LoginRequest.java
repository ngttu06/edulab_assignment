package vn.edu.khtn8.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Tên đăng nhập là bắt buộc") @jakarta.validation.constraints.Size(max = 80) String username,
        @NotBlank(message = "Mật khẩu là bắt buộc") @jakarta.validation.constraints.Size(max = 72) String password
) { }
