package vn.edu.khtn8.identity.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import org.springframework.http.ResponseCookie;
import vn.edu.khtn8.common.config.AppProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import vn.edu.khtn8.identity.dto.LoginRequest;
import vn.edu.khtn8.identity.dto.LoginResponse;
import vn.edu.khtn8.identity.entity.User;
import vn.edu.khtn8.identity.repository.UserRepository;
import vn.edu.khtn8.identity.security.JwtAuthenticationFilter;
import vn.edu.khtn8.identity.security.JwtService;

@Service
public class AuthenticationService {
    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final JwtService jwtService;
    private final AppProperties properties;

    public AuthenticationService(AuthenticationManager authenticationManager, UserRepository users, JwtService jwtService, AppProperties properties) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    public LoginResponse login(LoginRequest request, HttpServletRequest servletRequest, HttpServletResponse response) {
        if (request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid credentials");
        }
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        User user = users.findByUsernameIgnoreCase(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user was not found"));
        String token = jwtService.createAccessToken(user);
        response.addHeader("Set-Cookie", accessCookie(token, (int) jwtService.accessTokenSeconds(), servletRequest));
        return new LoginResponse(jwtService.accessTokenSeconds(), user.getUsername(), user.getFullName(), user.getRole().name());
    }

    @Transactional
    public void logout(AuthenticatedUser user, HttpServletRequest request, HttpServletResponse response) {
        users.revokeAccessTokens(user.id());
        response.addHeader("Set-Cookie", accessCookie("", 0, request));
    }

    private String accessCookie(String value, int maxAge, HttpServletRequest request) {
        String path = request.getContextPath().isEmpty() ? "/" : request.getContextPath();
        return ResponseCookie.from(JwtAuthenticationFilter.ACCESS_COOKIE, value)
                .httpOnly(true).secure(properties.getCookie().isSecure()).path(path)
                .sameSite(properties.getCookie().getSameSite()).maxAge(maxAge).build().toString();
    }
}
