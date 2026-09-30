package vn.edu.khtn8.identity.web;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.edu.khtn8.identity.dto.*;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import vn.edu.khtn8.identity.service.AuthenticationService;
import vn.edu.khtn8.identity.service.RegistrationService;

@Controller
public class AuthController {
    private final AuthenticationService authenticationService;
    private final RegistrationService registrationService;
    private final vn.edu.khtn8.identity.security.LoginRateLimiter limiter;

    public AuthController(AuthenticationService authenticationService, RegistrationService registrationService,
            vn.edu.khtn8.identity.security.LoginRateLimiter limiter) {
        this.authenticationService = authenticationService;
        this.registrationService = registrationService;
        this.limiter=limiter;
    }

    @GetMapping("/login") public String loginPage(Model model) {
        model.addAttribute("pageTitle", "Đăng nhập · EduLab");
        model.addAttribute("contentPage", "/WEB-INF/jsp/auth/login.jsp");
        return "layout/auth";
    }
    @GetMapping("/register") public String registerPage(Model model) {
        model.addAttribute("pageTitle", "Đăng ký · EduLab");
        model.addAttribute("contentPage", "/WEB-INF/jsp/auth/register.jsp");
        return "layout/auth";
    }

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
        limiter.check(servletRequest.getRemoteAddr());
        return ResponseEntity.ok(authenticationService.login(request, servletRequest, response));
    }

    @GetMapping("/api/v1/auth/me") @ResponseBody
    public CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return new CurrentUserResponse(user.id(), user.username(), user.fullName(),
                user.getAuthorities().iterator().next().getAuthority().substring("ROLE_".length()));
    }

    @PostMapping("/api/v1/auth/refresh") @ResponseBody
    public LoginResponse refresh(@CookieValue(name="EDULAB_REFRESH", required=false) String token,
            jakarta.servlet.http.HttpServletRequest request, HttpServletResponse response) {
        limiter.check(request.getRemoteAddr());
        return authenticationService.refresh(token,request,response);
    }

    @PostMapping("/api/v1/auth/logout") @ResponseBody
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser user,
            jakarta.servlet.http.HttpServletRequest request, HttpServletResponse response) {
        authenticationService.logout(user, request, response);
        return ResponseEntity.noContent().build();
    }
}
