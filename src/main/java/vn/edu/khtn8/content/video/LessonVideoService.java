package vn.edu.khtn8.content.video;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.content.repository.LessonRepository;
import vn.edu.khtn8.content.service.LearningPathService;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import vn.edu.khtn8.school.SchoolService;
import static vn.edu.khtn8.content.video.VideoModels.*;

@Service
public class LessonVideoService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final VideoPolicy policy;
    private final LessonRepository lessons;
    private final LearningPathService path;
    public LessonVideoService(JdbcTemplate jdbc, ObjectMapper json, VideoPolicy policy,
                              LessonRepository lessons, LearningPathService path) {
        this.jdbc = jdbc; this.json = json; this.policy = policy; this.lessons = lessons; this.path = path;
    }
    public Video video(AuthenticatedUser user, long lessonId) {
        lessons.findPublishedDetail(lessonId, user.id()).orElseThrow(() -> missing("Không tìm thấy bài học."));
        if (SchoolService.hasRole(user, "STUDENT")) path.requireUnlocked(user.id(), lessonId);
        return current(lessonId);
    }
    private Video current(long lessonId) {
        return jdbc.query("""
                SELECT v.* FROM lessons l JOIN lesson_videos v ON v.id=l.video_id AND v.lesson_id=l.id WHERE l.id=?
                """, (rs, n) -> new Video(rs.getLong("id"), rs.getLong("lesson_id"), rs.getInt("revision"),
                rs.getString("provider"), rs.getString("source_url"), rs.getString("youtube_id"),
                rs.getInt("duration_seconds"), rs.getInt("completion_threshold")), lessonId)
                .stream().findFirst().orElseThrow(() -> missing("Bài học chưa có video."));
    }
    public Progress progress(AuthenticatedUser user, long lessonId) {
        return progress(user.id(), video(user, lessonId));
    }
    private Progress progress(long studentId, Video video) {
        var rows = jdbc.queryForList("SELECT position_ms,ranges_json FROM video_progress WHERE student_id=? AND video_id=?", studentId, video.id());
        List<Range> ranges = rows.isEmpty() ? List.of() : decode(rows.getFirst().get("ranges_json"));
        long position = rows.isEmpty() ? 0 : ((Number) rows.getFirst().get("position_ms")).longValue();
        int percent = WatchedRanges.percent(ranges, video.durationSeconds() * 1000L);
        String status = percent >= video.completionThreshold() ? "COMPLETED" : ranges.isEmpty() ? "NOT_STARTED" : "IN_PROGRESS";
        return new Progress(video.id(), position, ranges, WatchedRanges.duration(ranges), percent, status);
    }
    @Transactional
    public Session begin(AuthenticatedUser user, long lessonId) {
        SchoolService.require(user, "STUDENT");
        lockLesson(lessonId);
        Video video = video(user, lessonId);
        // One reporting session per student/video: a second tab invalidates the first.
        jdbc.update("DELETE FROM video_watch_sessions WHERE student_id=? AND video_id=?", user.id(), video.id());
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO video_watch_sessions(id,student_id,video_id,expires_at) VALUES(?,?,?,DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 2 HOUR))", id, user.id(), video.id());
        return new Session(id, progress(user.id(), video));
    }
    @Transactional
    public Progress report(AuthenticatedUser user, long lessonId, Report report) {
        SchoolService.require(user, "STUDENT");
        lockLesson(lessonId);
        Video video = video(user, lessonId);
        var rows = jdbc.queryForList("SELECT * FROM video_watch_sessions WHERE id=? AND student_id=? AND video_id=? FOR UPDATE", report.sessionId(), user.id(), video.id());
        if (rows.isEmpty()) throw conflict("Phiên xem đã thay đổi. Hãy tải lại trang; chỉ một tab được lưu tiến độ mỗi video.");
        var session = rows.getFirst();
        Instant now = Instant.now();
        if (((Timestamp) session.get("expires_at")).toInstant().isBefore(now)) throw conflict("Phiên xem đã hết hạn. Hãy tải lại trang.");
        int sequence = ((Number) session.get("sequence_number")).intValue();
        if (report.sequence() <= sequence) return progress(user.id(), video); // Retry never counts twice.
        if (report.sequence() != sequence + 1) throw conflict("Thứ tự lưu tiến độ không hợp lệ. Hãy tải lại trang.");
        long duration = video.durationSeconds() * 1000L;
        if (report.positionMs() < 0 || report.positionMs() > duration
                || Math.abs(report.durationMs() - duration) > Math.max(2000, duration / 100))
            throw bad("Thời lượng thực tế không khớp cấu hình. Giáo viên cần cập nhật đúng thời lượng video.");
        var incoming = WatchedRanges.merge(List.of(), report.ranges(), duration);
        long elapsed = Math.max(0, now.toEpochMilli() - ((Timestamp) session.get("last_report_at")).toInstant().toEpochMilli());
        long credit = Math.min(90000, ((Number) session.get("credit_ms")).longValue() + Math.min(elapsed, 45000) * 2);
        long consumed = WatchedRanges.duration(incoming);
        if (consumed > credit) throw bad("Tiến trình vượt thời gian xem hợp lệ. Hãy tải lại trang và xem với tốc độ tối đa 2x.");
        var old = progress(user.id(), video);
        var ranges = WatchedRanges.merge(old.ranges(), incoming, duration);
        jdbc.update("""
                INSERT INTO video_progress(student_id,video_id,position_ms,ranges_json) VALUES(?,?,?,?)
                ON DUPLICATE KEY UPDATE position_ms=VALUES(position_ms),ranges_json=VALUES(ranges_json)
                """, user.id(), video.id(), report.positionMs(), encode(ranges));
        jdbc.update("""
                UPDATE video_watch_sessions SET sequence_number=?,credit_ms=?,last_report_at=?,expires_at=DATE_ADD(?,INTERVAL 2 HOUR) WHERE id=?
                """, report.sequence(), credit - consumed, Timestamp.from(now), Timestamp.from(now), report.sessionId());
        var saved = progress(user.id(), video);
        lessons.upsertProgress(user.id(), lessonId, saved.status(), saved.completionPercent());
        return saved;
    }
    public List<Map<String,Object>> manageable(AuthenticatedUser user) {
        SchoolService.require(user, "TEACHER", "ADMIN");
        return jdbc.queryForList("""
                SELECT l.id,l.title,l.status,l.video_teacher_id,u.full_name teacher_name,v.provider,v.revision,
                       c.title course_title,s.name subject_name,s.grade_level
                FROM lessons l JOIN topics t ON t.id=l.topic_id JOIN courses c ON c.id=t.course_id
                JOIN subjects s ON s.id=c.subject_id LEFT JOIN users u ON u.id=l.video_teacher_id
                LEFT JOIN lesson_videos v ON v.id=l.video_id
                WHERE ? OR l.video_teacher_id=? ORDER BY s.grade_level,c.id,t.display_order,l.id
                """, SchoolService.hasRole(user, "ADMIN"), user.id());
    }
    public List<Map<String,Object>> teachers(AuthenticatedUser user) {
        SchoolService.require(user, "ADMIN");
        return jdbc.queryForList("SELECT id,full_name,username FROM users WHERE role='TEACHER' AND enabled=TRUE ORDER BY full_name,id");
    }
    public Map<String,Object> configuration(AuthenticatedUser user, long lessonId) {
        authorizeEditor(user, lessonId);
        var rows = jdbc.queryForList("""
                SELECT l.id,l.title,l.video_teacher_id,v.provider,v.source_url,v.duration_seconds,v.completion_threshold,v.revision
                FROM lessons l LEFT JOIN lesson_videos v ON v.id=l.video_id WHERE l.id=?
                """, lessonId);
        return rows.getFirst();
    }
    @Transactional
    public Video save(AuthenticatedUser user, long lessonId, Input input) {
        lockLesson(lessonId); authorizeEditor(user, lessonId);
        var source = policy.validate(input.provider(), input.sourceUrl());
        if (input.durationSeconds() < 1 || input.durationSeconds() > 21600 || input.completionThreshold() < 80 || input.completionThreshold() > 100)
            throw bad("Thời lượng hoặc ngưỡng hoàn thành không hợp lệ.");
        int revision = jdbc.queryForObject("SELECT COALESCE(MAX(revision),0)+1 FROM lesson_videos WHERE lesson_id=?", Integer.class, lessonId);
        var key = new GeneratedKeyHolder();
        jdbc.update(c -> {
            var statement = c.prepareStatement("INSERT INTO lesson_videos(lesson_id,revision,provider,source_url,youtube_id,duration_seconds,completion_threshold,created_by) VALUES(?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            Object[] values = {lessonId, revision, input.provider(), source.url(), source.youtubeId(), input.durationSeconds(), input.completionThreshold(), user.id()};
            for (int i=0; i<values.length; i++) statement.setObject(i+1, values[i]);
            return statement;
        }, key);
        long id = java.util.Objects.requireNonNull(key.getKey()).longValue();
        jdbc.update("UPDATE lessons SET video_id=? WHERE id=?", id, lessonId);
        resetLesson(lessonId); audit(user, "SET_LESSON_VIDEO", lessonId);
        return current(lessonId);
    }
    @Transactional
    public void remove(AuthenticatedUser user, long lessonId) {
        lockLesson(lessonId); authorizeEditor(user, lessonId);
        jdbc.update("UPDATE lessons SET video_id=NULL WHERE id=?", lessonId);
        resetLesson(lessonId); audit(user, "REMOVE_LESSON_VIDEO", lessonId);
    }
    @Transactional
    public void assign(AuthenticatedUser user, long lessonId, Long teacherId) {
        SchoolService.require(user, "ADMIN"); lockLesson(lessonId);
        if (teacherId != null && !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE id=? AND role='TEACHER' AND enabled=TRUE)", Boolean.class, teacherId)))
            throw bad("Giáo viên không hợp lệ.");
        jdbc.update("UPDATE lessons SET video_teacher_id=? WHERE id=?", teacherId, lessonId);
        audit(user, "ASSIGN_VIDEO_TEACHER", lessonId);
    }
    private void authorizeEditor(AuthenticatedUser user, long lessonId) {
        SchoolService.require(user, "TEACHER", "ADMIN");
        var rows = jdbc.queryForList("SELECT video_teacher_id FROM lessons WHERE id=?", lessonId);
        if (rows.isEmpty()) throw missing("Không tìm thấy bài học.");
        if (!SchoolService.hasRole(user, "ADMIN") && (rows.getFirst().get("video_teacher_id") == null
                || ((Number) rows.getFirst().get("video_teacher_id")).longValue() != user.id()))
            throw new org.springframework.security.access.AccessDeniedException("Bài học chưa được phân công cho giáo viên.");
    }
    private void lockLesson(long lessonId) {
        if (jdbc.queryForList("SELECT id FROM lessons WHERE id=? FOR UPDATE", Long.class, lessonId).isEmpty()) throw missing("Không tìm thấy bài học.");
    }
    private void resetLesson(long lessonId) {
        jdbc.update("UPDATE lesson_progress SET status='NOT_STARTED',completion_percent=0,started_at=NULL,completed_at=NULL WHERE lesson_id=?", lessonId);
        jdbc.update("DELETE s FROM video_watch_sessions s JOIN lesson_videos v ON v.id=s.video_id WHERE v.lesson_id=?", lessonId);
    }
    private List<Range> decode(Object value) {
        try { return json.readValue(value.toString(), new TypeReference<List<Range>>() {}); }
        catch (Exception e) { throw new IllegalStateException("Không thể đọc tiến trình video đã lưu.", e); }
    }
    private String encode(List<Range> ranges) {
        try { return json.writeValueAsString(ranges); }
        catch (Exception e) { throw new IllegalStateException("Không thể lưu tiến trình video.", e); }
    }
    private void audit(AuthenticatedUser actor, String action, long id) {
        jdbc.update("INSERT INTO audit_logs(actor_user_id,action,resource_type,resource_id) VALUES(?,?,'lesson',?)", actor.id(), action, String.valueOf(id));
    }
    @Scheduled(fixedDelay=3600000)
    public void cleanup() { jdbc.update("DELETE FROM video_watch_sessions WHERE expires_at<CURRENT_TIMESTAMP(6)"); }
    private static ResponseStatusException missing(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}