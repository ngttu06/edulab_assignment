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
        public final String baseUrl, model, apiKey, systemPrompt, supportSystemPrompt;
        public final int dailyLimit, globalDailyLimit, maxOutputTokens;
        public RuntimeSettings(boolean enabled, String baseUrl, String model, String apiKey, int dailyLimit, int globalDailyLimit, int maxOutputTokens) {
            this(enabled,baseUrl,model,apiKey,dailyLimit,globalDailyLimit,maxOutputTokens,null,null);
        }
        public RuntimeSettings(boolean enabled, String baseUrl, String model, String apiKey, int dailyLimit, int globalDailyLimit, int maxOutputTokens,
                               String systemPrompt, String supportSystemPrompt) {
            this.enabled=enabled; this.baseUrl=baseUrl; this.model=model; this.apiKey=apiKey;
            this.dailyLimit=dailyLimit; this.globalDailyLimit=globalDailyLimit; this.maxOutputTokens=maxOutputTokens;
            this.systemPrompt=AiPrompts.effective(systemPrompt,false); this.supportSystemPrompt=AiPrompts.effective(supportSystemPrompt,true);
        }
    }
    public record View(boolean enabled, String baseUrl, String model, boolean keyConfigured, boolean encryptionReady,
                       int dailyLimit, int globalDailyLimit, int maxOutputTokens, String systemPrompt, String supportSystemPrompt) {
        public View(boolean enabled,String baseUrl,String model,boolean keyConfigured,boolean encryptionReady,int dailyLimit,int globalDailyLimit,int maxOutputTokens) {
            this(enabled,baseUrl,model,keyConfigured,encryptionReady,dailyLimit,globalDailyLimit,maxOutputTokens,AiPrompts.LESSON,AiPrompts.SUPPORT);
        }
    }
    public record Input(boolean enabled, @NotBlank @Size(max=500) String baseUrl,
                        @NotBlank @Pattern(regexp="[A-Za-z0-9._:/-]{1,120}") String model,
                        @Size(max=512) String apiKey, boolean clearApiKey,
                        @Min(1) @Max(100) int dailyLimit, @Min(1) @Max(10000) int globalDailyLimit,
                        @Min(100) @Max(4000) int maxOutputTokens,
                        @Size(max=8000) String systemPrompt, @Size(max=8000) String supportSystemPrompt) {
        public Input(boolean enabled,String baseUrl,String model,String apiKey,boolean clearApiKey,int dailyLimit,int globalDailyLimit,int maxOutputTokens) {
            this(enabled,baseUrl,model,apiKey,clearApiKey,dailyLimit,globalDailyLimit,maxOutputTokens,null,null);
        }
        @Override public String toString() { return "AiSettingsInput[redacted]"; }
    }
    public RuntimeSettings current() {
        return jdbc.queryForObject("SELECT * FROM ai_settings WHERE id=1", (rs, row) -> {
            String encrypted=rs.getString("encrypted_api_key");
            return new RuntimeSettings(rs.getBoolean("enabled"), rs.getString("base_url"), rs.getString("model"),
                    encrypted==null ? "" : cipher.decrypt(encrypted), rs.getInt("daily_limit"),
                    rs.getInt("global_daily_limit"), rs.getInt("max_output_tokens"),rs.getString("system_prompt"),rs.getString("support_system_prompt"));
        });
    }
    public View view(AuthenticatedUser actor) {
        SchoolService.require(actor,"ADMIN");
        // Metadata is readable even if a deployment has lost its encryption key.
        return jdbc.queryForObject("SELECT * FROM ai_settings WHERE id=1", (rs,row) -> new View(rs.getBoolean("enabled"),
                rs.getString("base_url"),rs.getString("model"),rs.getString("encrypted_api_key")!=null,cipher.available(),
                rs.getInt("daily_limit"),rs.getInt("global_daily_limit"),rs.getInt("max_output_tokens"),
                AiPrompts.effective(rs.getString("system_prompt"),false),AiPrompts.effective(rs.getString("support_system_prompt"),true)));
    }
    public boolean enabled() { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT enabled FROM ai_settings WHERE id=1",Boolean.class)); }
    public int dailyLimit() { return jdbc.queryForObject("SELECT daily_limit FROM ai_settings WHERE id=1",Integer.class); }
    public record ModelsInput(@NotBlank @Size(max=500) String baseUrl, @Size(max=512) String apiKey) {
        @Override public String toString() { return "AiModelsInput[redacted]"; }
    }
    public static final class Connection {
        public final String baseUrl,apiKey;
        Connection(String baseUrl,String apiKey) { this.baseUrl=baseUrl;this.apiKey=apiKey; }
    }
    public Connection modelConnection(AuthenticatedUser actor,ModelsInput input) {
        SchoolService.require(actor,"ADMIN");
        String baseUrl=endpoints.validate(input.baseUrl());
        String key=input.apiKey()==null?"":input.apiKey().trim();
        if(!key.isEmpty()) {
            if(!key.matches("[A-Za-z0-9._-]{8,512}")) throw bad("Khóa AI không hợp lệ.");
        } else {
            var stored=jdbc.queryForMap("SELECT base_url,encrypted_api_key FROM ai_settings WHERE id=1");
            if(!baseUrl.equals(stored.get("base_url"))) throw bad("Khi đổi Base URL, nhập khóa mới để lấy model; không gửi khóa đã lưu sang provider khác.");
            String encrypted=(String)stored.get("encrypted_api_key");
            if(encrypted==null) throw bad("Nhập API key hoặc lưu khóa trước khi lấy model.");
            key=cipher.decrypt(encrypted);
        }
        if(key.isBlank()) throw bad("Cần API key để lấy model.");
        return new Connection(baseUrl,key);
    }
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
        String systemPrompt=prompt(input.systemPrompt(),(String)previous.get("system_prompt"));
        String supportPrompt=prompt(input.supportSystemPrompt(),(String)previous.get("support_system_prompt"));
        jdbc.update("UPDATE ai_settings SET enabled=?,base_url=?,model=?,encrypted_api_key=?,daily_limit=?,global_daily_limit=?,max_output_tokens=?,system_prompt=?,support_system_prompt=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=1",
                input.enabled(),baseUrl,input.model(),encrypted,input.dailyLimit(),input.globalDailyLimit(),input.maxOutputTokens(),systemPrompt,supportPrompt);
        jdbc.update("INSERT INTO audit_logs(actor_user_id,action,resource_type,resource_id) VALUES(?,'UPDATE_AI_SETTINGS','ai_settings','1')",actor.id());
        return view(actor);
    }
    private static String prompt(String input,String previous) {
        if(input==null) return previous; // Backwards-compatible API updates preserve prompts.
        if(input.length()>8000 || input.indexOf('\0')>=0) throw bad("System prompt tối đa 8000 ký tự, không chứa ký tự NUL.");
        return input.isBlank() ? null : input.trim();
    }
    private static ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,text); }
}