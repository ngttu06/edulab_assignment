package vn.edu.khtn8.identity.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.identity.dto.ChangePasswordRequest;
import vn.edu.khtn8.identity.dto.ProfileResponse;
import vn.edu.khtn8.identity.dto.UpdateProfileRequest;
import vn.edu.khtn8.identity.entity.User;
import vn.edu.khtn8.identity.repository.UserRepository;
import vn.edu.khtn8.identity.security.AuthenticatedUser;

@Service
public class ProfileService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationService authentication;

    public ProfileService(UserRepository users, PasswordEncoder passwordEncoder, AuthenticationService authentication) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authentication = authentication;
    }

    @Transactional(readOnly = true)
    public ProfileResponse profile(AuthenticatedUser principal) {
        return response(requireCurrentUser(users.findById(principal.id()).orElseThrow(ProfileService::expired), principal));
    }

    @Transactional
    public ProfileResponse update(AuthenticatedUser principal, UpdateProfileRequest request) {
        User user = lockedUser(principal);
        String fullName = request.fullName().strip();
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (fullName.isBlank() || email.isBlank()) {
            throw bad("Họ và tên, email không được để trống.");
        }
        if (users.existsByEmailIgnoreCaseAndIdNot(email, user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã được sử dụng bởi tài khoản khác.");
        }
        user.setFullName(fullName);
        user.setEmail(email);
        // Flush here so a concurrent duplicate email is reported by the shared conflict handler.
        users.saveAndFlush(user);
        return response(user);
    }

    @Transactional
    public void changePassword(AuthenticatedUser principal, ChangePasswordRequest request,
                               HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        if (request.currentPassword().getBytes(StandardCharsets.UTF_8).length > 72
                || request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72
                || request.confirmPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw bad("Mật khẩu không được vượt quá 72 byte UTF-8.");
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw bad("Xác nhận mật khẩu mới không khớp.");
        }
        User user = lockedUser(principal);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            // A validation error, not 401: clients must not refresh/retry a wrong current password.
            throw bad("Mật khẩu hiện tại không đúng.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw bad("Mật khẩu mới phải khác mật khẩu hiện tại.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // logout clears the persistence context, so persist the new hash before revoking all sessions.
        users.saveAndFlush(user);
        authentication.logout(AuthenticatedUser.from(user), servletRequest, servletResponse);
    }

    private User lockedUser(AuthenticatedUser principal) {
        return requireCurrentUser(users.findLockedById(principal.id()).orElseThrow(ProfileService::expired), principal);
    }

    private User requireCurrentUser(User user, AuthenticatedUser principal) {
        if (!user.isEnabled() || user.getAuthVersion() != principal.authVersion()) throw expired();
        return user;
    }

    private ProfileResponse response(User user) {
        return new ProfileResponse(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(), user.getRole().name());
    }

    private static ResponseStatusException expired() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.");
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}