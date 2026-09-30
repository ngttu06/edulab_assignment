package vn.edu.khtn8.assessment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import vn.edu.khtn8.assessment.QuizModels.*;

@Repository
public class QuizRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public QuizRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public List<Quiz> list() {
        return jdbc.query("""
                SELECT q.id, q.title, q.duration_minutes, q.max_attempts,q.mode,q.reveal_after,
                  (SELECT COUNT(*) FROM quiz_items i JOIN questions x ON x.id=i.question_id WHERE i.quiz_id=q.id AND x.active=TRUE) question_count
                FROM quizzes q WHERE q.published=TRUE
                  AND (q.lesson_id IS NULL OR EXISTS (SELECT 1 FROM lessons l WHERE l.id=q.lesson_id AND l.status='PUBLISHED'))
                ORDER BY q.id
                """, (rs, n) -> new Quiz(rs.getLong("id"), rs.getString("title"), rs.getInt("duration_minutes"),
                rs.getInt("max_attempts"), rs.getInt("question_count"),rs.getString("mode"),rs.getTimestamp("reveal_after")==null?null:rs.getTimestamp("reveal_after").toInstant()));
    }

    public void lockStudent(long studentId) {
        jdbc.queryForObject("SELECT id FROM users WHERE id=? AND role='STUDENT' AND enabled=TRUE FOR UPDATE", Long.class, studentId);
    }

    public Optional<Attempt> active(long studentId, long quizId) {
        return jdbc.queryForList("SELECT id FROM quiz_attempts WHERE student_id=? AND quiz_id=? AND status='IN_PROGRESS' ORDER BY id DESC LIMIT 1",
                Long.class, studentId, quizId).stream().findFirst().flatMap(id -> find(id, studentId, true));
    }

    public int attempts(long studentId, long quizId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM quiz_attempts WHERE student_id=? AND quiz_id=?", Integer.class, studentId, quizId);
    }

    public List<Question> questions(long quizId) {
        return jdbc.query("""
                SELECT q.id,q.content,q.explanation,i.points FROM quiz_items i JOIN questions q ON q.id=i.question_id
                WHERE i.quiz_id=? AND q.active=TRUE ORDER BY i.display_order,q.id
                """, (rs, n) -> new Question(rs.getLong("id"), rs.getString("content"), rs.getString("explanation"),
                rs.getBigDecimal("points"), jdbc.query("SELECT id,option_text,is_correct FROM question_options WHERE question_id=? ORDER BY display_order,id",
                (option, index) -> new Option(option.getLong("id"), option.getString("option_text"), option.getBoolean("is_correct")), rs.getLong("id"))), quizId);
    }

    public long create(long studentId, Quiz quiz, int number, List<Question> questions, Instant deadline) {
        String snapshot = encode(questions);
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO quiz_attempts(quiz_id,student_id,attempt_number,title_snapshot,questions_snapshot,deadline_at,max_score,mode_snapshot,reveal_after_snapshot)
                    VALUES(?,?,?,?,?,?,10,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, quiz.id()); statement.setLong(2, studentId); statement.setInt(3, number);
            statement.setString(4, quiz.title()); statement.setString(5, snapshot); statement.setTimestamp(6, Timestamp.from(deadline));
            statement.setString(7,quiz.mode());statement.setTimestamp(8,quiz.revealAfter()==null?null:Timestamp.from(quiz.revealAfter()));
            return statement;
        }, key);
        return java.util.Objects.requireNonNull(key.getKey()).longValue();
    }

    public Optional<Attempt> find(long id, long studentId, boolean lock) {
        return jdbc.query("SELECT * FROM quiz_attempts WHERE id=? AND student_id=?" + (lock ? " FOR UPDATE" : ""), (rs, n) -> {
            String snapshot = rs.getString("questions_snapshot");
            if (snapshot == null || rs.getTimestamp("deadline_at") == null) throw new IllegalStateException("Legacy attempt has no snapshot");
            return new Attempt(rs.getLong("id"), rs.getLong("student_id"), rs.getString("title_snapshot"), rs.getString("status"),
                    rs.getTimestamp("deadline_at").toInstant(), decode(snapshot, new TypeReference<List<Question>>() { }),
                    rs.getString("answers_json") == null ? Map.of() : decode(rs.getString("answers_json"), new TypeReference<Map<Long, Long>>() { }),
                    rs.getBigDecimal("score"), rs.getBigDecimal("max_score"), rs.getBoolean("expired"),rs.getString("mode_snapshot"),
                    rs.getTimestamp("reveal_after_snapshot")==null?null:rs.getTimestamp("reveal_after_snapshot").toInstant());
        }, id, studentId).stream().findFirst();
    }

    public void grade(long id, Map<Long, Long> answers, BigDecimal score, boolean expired) {
        jdbc.update("UPDATE quiz_attempts SET status='GRADED',answers_json=?,score=?,expired=?,submitted_at=CURRENT_TIMESTAMP(6) WHERE id=? AND status='IN_PROGRESS'",
                encode(answers), score, expired, id);
    }

    public void save(long id, Map<Long,Long> answers) {
        jdbc.update("UPDATE quiz_attempts SET answers_json=? WHERE id=? AND status='IN_PROGRESS'",encode(answers),id);
    }

    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot encode quiz snapshot", ex); }
    }
    private <T> T decode(String value, TypeReference<T> type) {
        try { return json.readValue(value, type); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot decode quiz snapshot", ex); }
    }
}
