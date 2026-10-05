package vn.edu.khtn8.files;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import vn.edu.khtn8.school.SchoolService;

@RestController
@RequestMapping("/api/v1/admin/storage-settings")
public class StorageAdminController {
    private final StorageSettingsService settings;
    private final R2Client r2;
    public StorageAdminController(StorageSettingsService settings,R2Client r2) { this.settings=settings;this.r2=r2; }
    @GetMapping
    public StorageSettingsService.View view(@AuthenticationPrincipal AuthenticatedUser user,jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");return settings.view(user);
    }
    @PutMapping
    public StorageSettingsService.View save(@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody StorageSettingsService.Input input) { return settings.save(user,input); }
    @PostMapping("/test")
    public java.util.Map<String,Object> test(@AuthenticationPrincipal AuthenticatedUser user,jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        SchoolService.require(user,"ADMIN");var limits=settings.limits();
        if(limits.profileId()==null) throw StorageSettingsService.bad("Lưu cấu hình có khóa trước khi kiểm tra.");
        var p=settings.profile(limits.profileId());String key=p.prefix+"/probe/"+java.util.UUID.randomUUID();
        // Presigning is local; the browser performs the real PUT. No upload body passes through this server.
        return java.util.Map.of("url",r2.presign(p,"PUT",key,java.util.Map.of("Content-Type","text/plain"),60).toString(),"key",key);
    }
    public record Probe(@jakarta.validation.constraints.NotBlank String key) {}
    @PostMapping("/test/complete")
    public java.util.Map<String,String> testComplete(@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody Probe probe) {
        SchoolService.require(user,"ADMIN");var limits=settings.limits();
        if(limits.profileId()==null) throw StorageSettingsService.bad("Chưa có cấu hình storage.");
        var p=settings.profile(limits.profileId());
        if(!probe.key().matches(java.util.regex.Pattern.quote(p.prefix+"/probe/")+"[a-f0-9-]{36}")) throw StorageSettingsService.bad("Object kiểm tra không hợp lệ.");
        try {
            var head=r2.head(p,probe.key());
            if(head.size()!=2 || !java.util.Arrays.equals(r2.read(p,probe.key(),2),new byte[]{79,75})) throw StorageSettingsService.bad("Nội dung file kiểm tra không hợp lệ.");
            return java.util.Map.of("message","Kiểm tra R2 thành công: nhận object mẫu, đọc và xóa. Nếu PUT thực hiện từ trình duyệt thì CORS cũng đã đạt.");
        } finally { r2.delete(p,probe.key()); }
    }
}