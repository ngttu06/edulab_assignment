package vn.edu.khtn8.ai;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SupportAiQuota {
    private final JdbcTemplate jdbc;
    private final String hashKey;
    public SupportAiQuota(JdbcTemplate jdbc,@Value("${EDULAB_AI_ENCRYPTION_KEY:}") String hashKey) {
        this.jdbc=jdbc; this.hashKey=hashKey;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public long reserve(Long userId,String address,String guest,AiSettingsService.RuntimeSettings config) {
        // A database lock serializes budget reservation across instances. Failures consume quota too.
        Boolean enabled=jdbc.queryForObject("SELECT enabled FROM ai_settings WHERE id=1 FOR UPDATE",Boolean.class);
        if(!Boolean.TRUE.equals(enabled)) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Trợ lý AI đang tắt.");
        if(userId!=null && Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM quiz_attempts WHERE student_id=? AND mode_snapshot='TEST' AND status='IN_PROGRESS' AND deadline_at>CURRENT_TIMESTAMP(6))",Boolean.class,userId)))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"AI tạm khóa trong khi bạn đang làm bài kiểm tra.");
        // Use one database UTC day for both identities and the quota window, including at midnight.
        String day=jdbc.queryForObject("SELECT UTC_DATE()",String.class);
        String actor=hash(day,userId==null?"guest:"+guest:"user:"+userId), ip=hash(day,"ip:"+address);
        int daily=jdbc.queryForObject("SELECT COUNT(*) FROM support_ai_requests WHERE (actor_hash=? OR address_hash=?) AND created_at>=?",Integer.class,actor,ip,day);
        int minute=jdbc.queryForObject("SELECT COUNT(*) FROM support_ai_requests WHERE address_hash=? AND created_at>=DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 1 MINUTE)",Integer.class,ip);
        int total=jdbc.queryForObject("SELECT COUNT(*) FROM support_ai_requests WHERE created_at>=?",Integer.class,day);
        int pending=jdbc.queryForObject("SELECT COUNT(*) FROM support_ai_requests WHERE status='PENDING' AND created_at>=DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 2 MINUTE)",Integer.class);
        if(daily>=config.dailyLimit || minute>=5) throw limited("Bạn đã đạt giới hạn chat. Hãy thử lại sau; hạn mức ngày làm mới lúc 00:00 UTC.");
        if(total>=config.globalDailyLimit || pending>=4) throw limited("Trợ lý đang hết hạn mức hoặc đang bận. Hãy thử lại sau.");
        var key=new GeneratedKeyHolder();
        jdbc.update(c->{var s=c.prepareStatement("INSERT INTO support_ai_requests(actor_hash,address_hash) VALUES(?,?)",java.sql.Statement.RETURN_GENERATED_KEYS);
            s.setString(1,actor);s.setString(2,ip);return s;},key);
        return java.util.Objects.requireNonNull(key.getKey()).longValue();
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void finish(long id,boolean success) {
        jdbc.update("UPDATE support_ai_requests SET status=? WHERE id=? AND status='PENDING'",success?"SUCCEEDED":"FAILED",id);
    }
    @Scheduled(fixedDelay=3600000)
    public void cleanup() {
        jdbc.update("DELETE FROM support_ai_requests WHERE created_at<DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 2 DAY)");
    }
    private String hash(String day,String text) {
        if(hashKey.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Server chưa cấu hình khóa bảo vệ AI.");
        try {
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(hashKey.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((day+":"+text).getBytes(StandardCharsets.UTF_8)));
        } catch(java.security.GeneralSecurityException ex) { throw new IllegalStateException("Cannot initialize AI quota hashing"); }
    }
    private static ResponseStatusException limited(String text) { return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,text); }
}