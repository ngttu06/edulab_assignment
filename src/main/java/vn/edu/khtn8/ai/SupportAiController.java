package vn.edu.khtn8.ai;

import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.khtn8.common.config.AppProperties;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import vn.edu.khtn8.school.SchoolService;

@RestController
public class SupportAiController {
    private final SupportAiService service;
    private final AiSettingsService settings;
    private final AiProvider provider;
    private final SupportAiQuota quota;
    private final AppProperties properties;
    public SupportAiController(SupportAiService service,AiSettingsService settings,AiProvider provider,SupportAiQuota quota,AppProperties properties) {
        this.service=service;this.settings=settings;this.provider=provider;this.quota=quota;this.properties=properties;
    }
    public record Message(@NotNull @Pattern(regexp="user|assistant") String role,@NotBlank @Size(max=2000) String content) {}
    public record Question(@NotBlank @Size(max=2000) String question,@NotNull @Size(max=8) List<@NotNull @Valid Message> history) {}
    public record Status(boolean enabled) {}
    @GetMapping("/api/v1/ai/support/status")
    public Status status(HttpServletResponse response) {response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");return new Status(settings.enabled());}
    @PostMapping("/api/v1/ai/support/chat")
    public SupportAiService.Answer chat(@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody Question question,
            HttpServletRequest request,HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");
        return service.chat(user,request.getRemoteAddr(),guest(request,response),question.question().trim(),
                question.history().stream().map(m->new AiProvider.ChatMessage(m.role(),m.content())).toList());
    }
    @GetMapping("/api/v1/admin/ai-settings")
    public AiSettingsService.View settings(@AuthenticationPrincipal AuthenticatedUser actor,HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");return settings.view(actor);
    }
    @PutMapping("/api/v1/admin/ai-settings")
    public AiSettingsService.View save(@AuthenticationPrincipal AuthenticatedUser actor,@Valid @RequestBody AiSettingsService.Input input) {
        return settings.save(actor,input);
    }
    @PostMapping("/api/v1/admin/ai-settings/test")
    public java.util.Map<String,String> test(@AuthenticationPrincipal AuthenticatedUser actor,HttpServletRequest request) {
        SchoolService.require(actor,"ADMIN");
        var config=settings.current();
        if(!config.enabled) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Lưu cấu hình và bật AI trước khi kiểm tra kết nối.");
        long id=quota.reserve(actor.id(),request.getRemoteAddr(),"admin-test",config);
        try {
            provider.answer(new AiProvider.AiQuestion("Trả lời đúng một từ: OK.","Kiểm tra kết nối EduLab.","SUPPORT"));
            quota.finish(id,true);return java.util.Map.of("message","Kết nối AI thành công với model đã lưu.");
        } catch(RuntimeException ex) {quota.finish(id,false);throw ex;}
    }
    private String guest(HttpServletRequest request,HttpServletResponse response) {
        if(request.getCookies()!=null) for(Cookie cookie:request.getCookies()) {
            if(cookie.getName().equals("edulab_ai_guest") && cookie.getValue().matches("[a-f0-9-]{36}")) return cookie.getValue();
        }
        String id=UUID.randomUUID().toString();
        response.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from("edulab_ai_guest",id).httpOnly(true)
                .secure(properties.getCookie().isSecure()).sameSite("Strict").path(request.getContextPath()+"/api/v1/ai/support")
                .maxAge(java.time.Duration.ofDays(1)).build().toString());
        return id;
    }
}