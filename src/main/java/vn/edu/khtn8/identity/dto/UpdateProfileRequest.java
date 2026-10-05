package vn.edu.khtn8.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "Vui lòng nhập họ và tên.")
        @Size(max = 160, message = "Họ và tên không được vượt quá 160 ký tự.") String fullName,
        @NotBlank(message = "Vui lòng nhập email.")
        @Email(message = "Email không đúng định dạng.")
        @Size(max = 160, message = "Email không được vượt quá 160 ký tự.") String email
) { }