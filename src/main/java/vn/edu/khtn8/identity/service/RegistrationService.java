package vn.edu.khtn8.identity.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.identity.dto.RegisterRequest;
import vn.edu.khtn8.identity.dto.RegistrationResponse;
import vn.edu.khtn8.identity.entity.Role;
import vn.edu.khtn8.identity.entity.User;
import vn.edu.khtn8.identity.repository.UserRepository;

@Service
public class RegistrationService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegistrationResponse register(RegisterRequest request) {
        if (request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu không được vượt quá 72 byte UTF-8.");
        }
        if (!java.util.Set.of("STUDENT", "PARENT").contains(request.role())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vai trò đăng ký không hợp lệ.");
        }
        if (users.existsByUsernameIgnoreCase(request.username().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên đăng nhập đã được sử dụng.");
        }
        if (users.existsByEmailIgnoreCase(request.email().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã được sử dụng.");
        }
        User user = new User();
        user.setUsername(request.username().trim());
        user.setEmail(request.email().trim().toLowerCase(java.util.Locale.ROOT));
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.valueOf(request.role()));
        User created = users.save(user);
        return new RegistrationResponse(created.getId(), created.getUsername(), created.getRole().name(),
                "Tạo tài khoản thành công. Bạn có thể đăng nhập ngay.");
    }
}
