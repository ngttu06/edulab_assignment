package vn.edu.khtn8.identity.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.edu.khtn8.identity.dto.ChangePasswordRequest;
import vn.edu.khtn8.identity.dto.ProfileResponse;
import vn.edu.khtn8.identity.dto.UpdateProfileRequest;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import vn.edu.khtn8.identity.security.LoginRateLimiter;
import vn.edu.khtn8.identity.service.ProfileService;

@Controller
public class ProfileController {
    private final ProfileService profiles;
    private final LoginRateLimiter limiter;

    public ProfileController(ProfileService profiles, LoginRateLimiter limiter) {
        this.profiles = profiles;
        this.limiter = limiter;
    }

    @GetMapping("/profile")
    public String page(Model model, @AuthenticationPrincipal AuthenticatedUser user) {
        model.addAttribute("profile", profiles.profile(user));
        model.addAttribute("pageTitle", "Thông tin cá nhân · EduLab");
        model.addAttribute("contentPage", "/WEB-INF/jsp/profile.jsp");
        return "layout/default";
    }

    @GetMapping("/api/v1/profile") @ResponseBody
    public ProfileResponse profile(@AuthenticationPrincipal AuthenticatedUser user) {
        return profiles.profile(user);
    }

    @PutMapping("/api/v1/profile") @ResponseBody
    public ProfileResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                                  @Valid @RequestBody UpdateProfileRequest request) {
        return profiles.update(user, request);
    }

    @PostMapping("/api/v1/profile/password") @ResponseBody
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ChangePasswordRequest request, HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        limiter.check("password:" + user.id(), "Quá nhiều lần đổi mật khẩu. Vui lòng đợi một phút.");
        profiles.changePassword(user, request, servletRequest, servletResponse);
        return ResponseEntity.noContent().build();
    }
}