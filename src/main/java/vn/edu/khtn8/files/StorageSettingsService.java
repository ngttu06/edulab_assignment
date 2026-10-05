package vn.edu.khtn8.files;

import jakarta.validation.constraints.*;
import java.sql.Statement;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import vn.edu.khtn8.school.SchoolService;

@Service
public class StorageSettingsService {
    private final JdbcTemplate jdbc;
    private final StorageSecretCipher cipher;
    private final StorageEndpointPolicy endpoints;
    public StorageSettingsService(JdbcTemplate jdbc,StorageSecretCipher cipher,StorageEndpointPolicy endpoints) {
        this.jdbc=jdbc;this.cipher=cipher;this.endpoints=endpoints;
    }
    // Never a record: credentials must not appear in generated toString().
    public static final class Profile {
        public final long id;
        public final String endpoint,bucket,region,prefix,accessKey,secretKey;
        public Profile(long id,String endpoint,String bucket,String region,String prefix,String accessKey,String secretKey) {
            this.id=id;this.endpoint=endpoint;this.bucket=bucket;this.region=region;this.prefix=prefix;this.accessKey=accessKey;this.secretKey=secretKey;
        }
    }
    public record Limits(boolean enabled,Long profileId,long maxImageBytes,long maxPdfBytes,long userQuotaBytes,boolean aiImagesEnabled,boolean aiPdfEnabled) {}
    public record View(boolean enabled,String endpoint,String bucket,String region,String prefix,boolean keyConfigured,boolean encryptionReady,
                       long maxImageBytes,long maxPdfBytes,long userQuotaBytes,boolean aiImagesEnabled,boolean aiPdfEnabled) {}
    public record Input(boolean enabled,@NotBlank @Size(max=500) String endpoint,
                        @NotBlank @Pattern(regexp="[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]") String bucket,
                        @NotBlank @Pattern(regexp="[a-z0-9-]{1,63}") String region,
                        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,80}") String prefix,
                        @Size(max=256) String accessKey,@Size(max=512) String secretKey,
                        @Min(1) @Max(5242880) long maxImageBytes,@Min(1) @Max(10485760) long maxPdfBytes,
                        @Min(1048576) @Max(1073741824) long userQuotaBytes,boolean aiImagesEnabled,boolean aiPdfEnabled) {
        @Override public String toString() { return "StorageSettingsInput[redacted]"; }
    }
    public Limits limits() {
        return jdbc.queryForObject("SELECT * FROM storage_settings WHERE id=1",(rs,row)-> {
            long id=rs.getLong("active_profile_id"); Long profileId=rs.wasNull()?null:id;
            return new Limits(rs.getBoolean("enabled"),profileId,rs.getLong("max_image_bytes"),rs.getLong("max_pdf_bytes"),rs.getLong("user_quota_bytes"),rs.getBoolean("ai_images_enabled"),rs.getBoolean("ai_pdf_enabled"));
        });
    }
    public Profile profile(long id) {
        var rows=jdbc.queryForList("SELECT * FROM storage_profiles WHERE id=?",id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Không tìm thấy cấu hình storage của file.");
        var p=rows.getFirst();
        return new Profile(id,(String)p.get("endpoint"),(String)p.get("bucket"),(String)p.get("region"),(String)p.get("object_prefix"),cipher.decrypt((String)p.get("encrypted_access_key")),cipher.decrypt((String)p.get("encrypted_secret_key")));
    }
    public View view(AuthenticatedUser actor) {
        SchoolService.require(actor,"ADMIN");var l=limits();
        var rows=l.profileId()==null?java.util.List.<java.util.Map<String,Object>>of():jdbc.queryForList("SELECT endpoint,bucket,region,object_prefix FROM storage_profiles WHERE id=?",l.profileId());
        var p=rows.isEmpty()?java.util.Map.<String,Object>of():rows.getFirst();
        return new View(l.enabled(),(String)p.getOrDefault("endpoint",StorageEndpointPolicy.DEFAULT_ENDPOINT),(String)p.getOrDefault("bucket","edulab"),
                (String)p.getOrDefault("region","auto"),(String)p.getOrDefault("object_prefix","edulab"),!rows.isEmpty(),cipher.available(),l.maxImageBytes(),l.maxPdfBytes(),l.userQuotaBytes(),l.aiImagesEnabled(),l.aiPdfEnabled());
    }
    @Transactional
    public View save(AuthenticatedUser actor,Input input) {
        SchoolService.require(actor,"ADMIN");
        String endpoint=endpoints.validate(input.endpoint());
        if(input.bucket().contains("..") || input.bucket().matches("[0-9.]+")) throw bad("Tên bucket không hợp lệ.");
        jdbc.queryForMap("SELECT id FROM storage_settings WHERE id=1 FOR UPDATE");
        var previous=limits();Long id=previous.profileId();
        String access=input.accessKey()==null?"":input.accessKey().trim(),secret=input.secretKey()==null?"":input.secretKey().trim();
        if(access.isEmpty()!=secret.isEmpty()) throw bad("Nhập cả Access Key ID và Secret Access Key, hoặc để trống cả hai để giữ khóa.");
        java.util.Map<String,Object> old=id==null?java.util.Map.of():jdbc.queryForMap("SELECT * FROM storage_profiles WHERE id=?",id);
        boolean same=endpoint.equals(old.get("endpoint")) && input.bucket().equals(old.get("bucket")) && input.region().equals(old.get("region")) && input.prefix().equals(old.get("object_prefix"));
        if(!access.isEmpty()) {
            if(!access.matches("[A-Za-z0-9_-]{8,256}") || !secret.matches("[A-Za-z0-9/+=._-]{16,512}")) throw bad("Khóa S3 không hợp lệ.");
            String encryptedAccess=cipher.encrypt(access),encryptedSecret=cipher.encrypt(secret);
            if(same) {
                // Rotate credentials for all old profiles at this bucket too, preserving old downloads.
                jdbc.update("UPDATE storage_profiles SET encrypted_access_key=?,encrypted_secret_key=? WHERE endpoint=? AND bucket=?",encryptedAccess,encryptedSecret,endpoint,input.bucket());
            } else {
                var key=new GeneratedKeyHolder();
                jdbc.update(connection->{
                    var s=connection.prepareStatement("INSERT INTO storage_profiles(endpoint,bucket,region,object_prefix,encrypted_access_key,encrypted_secret_key) VALUES(?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);
                    s.setString(1,endpoint);s.setString(2,input.bucket());s.setString(3,input.region());s.setString(4,input.prefix());s.setString(5,encryptedAccess);s.setString(6,encryptedSecret);return s;
                },key);
                id=java.util.Objects.requireNonNull(key.getKey()).longValue();
            }
        } else if(!same && id!=null) throw bad("Khi đổi endpoint/bucket/region/prefix, nhập lại cả hai khóa. Cấu hình cũ được giữ cho file cũ.");
        if(input.enabled() && (id==null || !cipher.available())) throw bad("Cần khóa S3 và khóa mã hóa server trước khi bật upload.");
        jdbc.update("UPDATE storage_settings SET enabled=?,active_profile_id=?,max_image_bytes=?,max_pdf_bytes=?,user_quota_bytes=?,ai_images_enabled=?,ai_pdf_enabled=? WHERE id=1",input.enabled(),id,input.maxImageBytes(),input.maxPdfBytes(),input.userQuotaBytes(),input.aiImagesEnabled(),input.aiPdfEnabled());
        jdbc.update("INSERT INTO audit_logs(actor_user_id,action,resource_type,resource_id) VALUES(?,'UPDATE_STORAGE_SETTINGS','storage_settings','1')",actor.id());
        return view(actor);
    }
    public static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}