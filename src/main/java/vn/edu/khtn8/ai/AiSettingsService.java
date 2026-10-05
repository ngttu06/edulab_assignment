package vn.edu.khtn8.ai;

import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import vn.edu.khtn8.school.SchoolService;

@Service
public class AiSettingsService {
    private final JdbcTemplate jdbc;
    private final AiSecretCipher cipher;
    private final AiEndpointPolicy endpoints;
    public AiSettingsService(JdbcTemplate jdbc, AiSecretCipher cipher, AiEndpointPolicy endpoints) {
        this.jdbc=jdbc; this.cipher=cipher; this.endpoints=endpoints;
    }
    // Intentionally not a record: prevent generated toString() from exposing a secret.
    public static final class RuntimeSettings {
        public final boolean enabled;
        public final String baseUrl, model, apiKey;
        public final int dailyLimit, globalDailyLimit, maxOutputTokens;
        public RuntimeSettings(boolean enabled, String baseUrl, String model, String apiKey, int dailyLimit, int globalDailyLimit, int maxOutputTokens) {
            this.enabled=enabled; this.baseUrl=baseUrl; this.model=model; this.apiKey=apiKey;
            this.dailyLimit=dailyLimit; this.globalDailyLimit=globalDailyLimit; this.maxOutputTokens=maxOutputTokens;
        }
    }
    public record View(boolean enabled, String baseUrl, String model, boolean keyConfigured, boolean encryptionReady,
                       int dailyLimit, int globalDailyLimit, int maxOutputTokens) {}
    public record Input(boolean enabled, @NotBlank @Size(max=500) String baseUrl,
                        @NotBlank @Pattern(regexp="[A-Za-z0-9._:/-]{1,120}") String model,
                        @Size(max=512) String apiKey, boolean clearApiKey,
                        @Min(1) @Max(100) int dailyLimit, @Min(1) @Max(10000) int globalDailyLimit,
                        @Min(100) @Max(4000) int maxOutputTokens) {
        @Override public String toString() { return "AiSettingsInput[redacted]"; }
    }
    public RuntimeSettings current() {
        return jdbc.queryForObject("SELECT * FROM ai_settings WHERE id=1", (rs, row) -> {
            String encrypted=rs.getString("encrypted_api_key");
            return new RuntimeSettings(rs.getBoolean("enabled"), rs.getString("base_url"), rs.getString("model"),
                    encrypted==null ? "" : cipher.decrypt(encrypted), rs.getInt("daily_limit"),
                    rs.getInt("global_daily_limit"), rs.getInt("max_output_tokens"));
        });
    }
    public View view(AuthenticatedUser actor) {
        SchoolService.require(actor,"ADMIN");
        // Metadata is readable even if a deployment has lost its encryption key.
        return jdbc.queryForObject("SELECT * FROM ai_settings WHERE id=1", (rs,row) -> new View(rs.getBoolean("enabled"),
                rs.getString("base_url"),rs.getString("model"),rs.getString("encrypted_api_key")!=null,cipher.available(),
                rs.getInt("daily_limit"),rs.getInt("global_daily_limit"),rs.getInt("max_output_tokens")));
    }
    public boolean enabled() { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT enabled FROM ai_settings WHERE id=1",Boolean.class)); }
    public int dailyLimit() { return jdbc.queryForObject("SELECT daily_limit FROM ai_settings WHERE id=1",Integer.class); }
    @Transactional
    public View save(AuthenticatedUser actor, Input input) {
        SchoolService.require(actor,"ADMIN");
        String baseUrl=endpoints.validate(input.baseUrl());
        var previous=jdbc.queryForMap("SELECT * FROM ai_settings WHERE id=1 FOR UPDATE");
        String encrypted=(String)previous.get("encrypted_api_key");
        String key=input.apiKey()==null?"":input.apiKey().trim();
        if(input.clearApiKey() && !key.isEmpty()) throw bad("Chọn xóa khóa hoặc nhập khóa mới, không thực hiện cả hai cùng lúc.");
        if(input.clearApiKey()) encrypted=null;
        if(!key.isEmpty()) {
            if(!key.matches("[A-Za-z0-9._-]{8,512}")) throw bad("Khóa AI không hợp lệ.");
            encrypted=cipher.encrypt(key);
        }
        if(!baseUrl.equals(previous.get("base_url")) && encrypted!=null && key.isEmpty() && !input.clearApiKey())
            throw bad("Khi đổi host, hãy nhập khóa mới hoặc xóa khóa cũ để tránh gửi khóa tới host khác.");
        if(input.enabled() && (encrypted==null || !cipher.available())) throw bad("Cần khóa AI và khóa mã hóa server trước khi bật trợ lý.");
        if(input.globalDailyLimit()<input.dailyLimit()) throw bad("Hạn mức tổng phải lớn hơn hoặc bằng hạn mức mỗi người.");
        jdbc.update("UPDATE ai_settings SET enabled=?,base_url=?,model=?,encrypted_api_key=?,daily_limit=?,global_daily_limit=?,max_output_tokens=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=1",
                input.enabled(),baseUrl,input.model(),encrypted,input.dailyLimit(),input.globalDailyLimit(),input.maxOutputTokens());
        jdbc.update("INSERT INTO audit_logs(actor_user_id,action,resource_type,resource_id) VALUES(?,'UPDATE_AI_SETTINGS','ai_settings','1')",actor.id());
        return view(actor);
    }
    private static ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,text); }
}