package vn.edu.khtn8.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại.")
        @Size(max = 72, message = "Mật khẩu hiện tại không được vượt quá 72 ký tự.") String currentPassword,
        @NotBlank(message = "Vui lòng nhập mật khẩu mới.")
        @Size(min = 8, max = 72, message = "Mật khẩu mới phải có từ 8 đến 72 ký tự và không vượt quá 72 byte UTF-8.") String newPassword,
        @NotBlank(message = "Vui lòng xác nhận mật khẩu mới.")
        @Size(max = 72, message = "Xác nhận mật khẩu không được vượt quá 72 ký tự.") String confirmPassword
) { }