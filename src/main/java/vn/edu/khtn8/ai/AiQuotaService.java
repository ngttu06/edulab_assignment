package vn.edu.khtn8.ai;

import java.sql.Statement;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.common.config.AppProperties;

@Service
public class AiQuotaService {
    private final JdbcTemplate jdbc;
    private final AppProperties properties;
    public AiQuotaService(JdbcTemplate jdbc, AppProperties properties) { this.jdbc = jdbc; this.properties = properties; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long reserve(long userId, String provider) {
        jdbc.queryForObject("SELECT id FROM users WHERE id=? AND enabled=TRUE FOR UPDATE", Long.class, userId);
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM quiz_attempts WHERE student_id=? AND mode_snapshot='TEST' AND status='IN_PROGRESS' AND deadline_at>CURRENT_TIMESTAMP(6))",Boolean.class,userId)))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"AI tạm khóa trong khi bạn đang làm bài kiểm tra.");
        int count = jdbc.queryForObject("SELECT COUNT(*) FROM ai_requests WHERE user_id=? AND created_at>=DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 1 DAY)", Integer.class, userId);
        if (count >= properties.getAi().getDailyRequestLimit())
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Đã đạt giới hạn trợ giúp AI trong 24 giờ.");
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO ai_requests(user_id,provider,status,request_type) VALUES(?,?,'PENDING','HINT')", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, userId); statement.setString(2, provider); return statement;
        }, key);
        return java.util.Objects.requireNonNull(key.getKey()).longValue();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(long id, boolean success) {
        jdbc.update("UPDATE ai_requests SET status=? WHERE id=? AND status='PENDING'", success ? "SUCCEEDED" : "FAILED", id);
    }
}