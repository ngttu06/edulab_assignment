package vn.edu.khtn8.identity.web;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import vn.edu.khtn8.identity.dto.*;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import vn.edu.khtn8.identity.service.AuthenticationService;
import vn.edu.khtn8.identity.service.RegistrationService;

@Controller
public class AuthController {
    private final AuthenticationService authenticationService;
    private final RegistrationService registrationService;

    public AuthController(AuthenticationService authenticationService, RegistrationService registrationService) {
        this.authenticationService = authenticationService;
        this.registrationService = registrationService;
    }

    @GetMapping("/login") public String loginPage() { return "auth/login"; }
    @GetMapping("/register") public String registerPage() { return "auth/register"; }

    @GetMapping("/api/v1/auth/csrf") @ResponseBody
    public java.util.Map<String, String> csrf(org.springframework.security.web.csrf.CsrfToken token) {
        return java.util.Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @PostMapping("/api/v1/auth/register") @ResponseBody
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(201).body(registrationService.register(request));
    }

    @PostMapping("/api/v1/auth/login") @ResponseBody
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
            jakarta.servlet.http.HttpServletRequest servletRequest, HttpServletResponse response) {
        return ResponseEntity.ok(authenticationService.login(request, servletRequest, response));
    }

    @GetMapping("/api/v1/auth/me") @ResponseBody
    public CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return new CurrentUserResponse(user.id(), user.username(), user.fullName(),
                user.getAuthorities().iterator().next().getAuthority().substring("ROLE_".length()));
    }

    @PostMapping("/api/v1/auth/logout") @ResponseBody
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser user,
            jakarta.servlet.http.HttpServletRequest request, HttpServletResponse response) {
        authenticationService.logout(user, request, response);
        return ResponseEntity.noContent().build();
    }
}
