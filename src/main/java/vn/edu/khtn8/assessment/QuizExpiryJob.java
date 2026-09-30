package vn.edu.khtn8.assessment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class QuizExpiryJob {
    private final JdbcTemplate jdbc;
    private final QuizService quizzes;
    public QuizExpiryJob(JdbcTemplate jdbc,QuizService quizzes){this.jdbc=jdbc;this.quizzes=quizzes;}
    @Scheduled(fixedDelay=60000,initialDelay=60000)
    public void finalizeExpired() {
        var rows=jdbc.queryForList("SELECT id,student_id FROM quiz_attempts WHERE status='IN_PROGRESS' AND deadline_at<=CURRENT_TIMESTAMP(6) ORDER BY id LIMIT 100");
        for(var row:rows) {
            try{quizzes.expire(((Number)row.get("student_id")).longValue(),((Number)row.get("id")).longValue());}
            catch(RuntimeException ex){org.slf4j.LoggerFactory.getLogger(QuizExpiryJob.class).warn("Quiz expiry failed: {}",ex.getClass().getSimpleName());}
        }
    }
}